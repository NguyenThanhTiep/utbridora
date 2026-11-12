package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.files.FileContracts.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyReportFileAdapterTest extends WeeklyReportAdapterTestSupport {
    final FileService files=mock(FileService.class);
    final WeeklyReportFileAdapter adapter=new WeeklyReportFileAdapter(files);
    final UUID report=UUID.randomUUID(), file=UUID.randomUUID();
    FileDto dto(UUID root, FileMetadata.ScanStatus status) {
        return new FileDto(file.toString(),new ResourceRef(ResourceType.WEEKLY_REPORT,root.toString()),
                FilePurpose.WEEKLY_REPORT,"synthetic.pdf","application/pdf",12,"0".repeat(64),status,now,null);
    }
    @Test void draftUsesOriginalPrincipalAndPermitsPending() {
        var actor=auth(student);when(files.metadata(actor,file.toString())).thenReturn(dto(report,FileMetadata.ScanStatus.PENDING));
        adapter.requireBound(actor,student,report,List.of(file),false);
        verify(files).metadata(same(actor),eq(file.toString()));verifyNoMoreInteractions(files);
    }
    @Test void submitUsesT1CleanPortAndOriginalSecurityContext() {
        var actor=auth(student);
        when(files.requireCleanBound(eq(file.toString()),any(),eq(FilePurpose.WEEKLY_REPORT),any()))
                .thenAnswer(call->{
                    vn.utbridora.auth.contracts.ActorContext context=call.getArgument(3);
                    assertSame(actor,context.authentication());assertEquals(4,context.currentSecurityVersion());
                    return dto(report,FileMetadata.ScanStatus.CLEAN);
                });
        adapter.requireBound(actor,student,report,List.of(file),true);
        verify(files,never()).metadata(any(),any());
    }
    @Test void foreignBindingIsRejected() {
        var actor=auth(student);when(files.metadata(actor,file.toString())).thenReturn(dto(UUID.randomUUID(),FileMetadata.ScanStatus.CLEAN));
        code(422,"FILE_BINDING_INVALID",()->adapter.requireBound(actor,student,report,List.of(file),false));
    }
    @Test void anotherOwnerCannotSupplyAReconstructedIdentity() {
        code(404,"RESOURCE_NOT_FOUND",()->adapter.requireBound(auth(mentor),student,report,List.of(file),false));
        verifyNoInteractions(files);
    }
    @Test void draftRejectsRejectedEvidence() {
        var actor=auth(student);when(files.metadata(actor,file.toString())).thenReturn(dto(report,FileMetadata.ScanStatus.REJECTED));
        code(409,"FILE_NOT_CLEAN",()->adapter.requireBound(actor,student,report,List.of(file),false));
    }
}
