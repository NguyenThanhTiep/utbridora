package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.modules.internship.application.WeeklyReportPorts;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Uses T1 metadata for pending drafts and T1 clean/storage validation for submit. */
public final class WeeklyReportFileAdapter implements WeeklyReportPorts.Files {
    private final FileService files;
    public WeeklyReportFileAdapter(FileService files) { this.files = files; }
    @Override public void requireBound(Authentication actor, UUID student, UUID reportId, List<UUID> ids, boolean clean) {
        var context = ActorContext.fromAuthenticatedWorkflow(actor, null);
        if (!student.toString().equals(context.actorId())) throw failure(404, "RESOURCE_NOT_FOUND");
        var resource = new ResourceRef(ResourceType.WEEKLY_REPORT, reportId.toString());
        for (UUID id : ids) {
            FileDto file = clean ? files.requireCleanBound(id.toString(), resource, FilePurpose.WEEKLY_REPORT, context)
                    : files.metadata(actor, id.toString());
            if (!id.toString().equals(file.fileId()) || !resource.equals(file.resource())
                    || file.purpose() != FilePurpose.WEEKLY_REPORT || file.deletedAt() != null) {
                throw failure(422, "FILE_BINDING_INVALID");
            }
            if (file.scanStatus() != FileMetadata.ScanStatus.PENDING && file.scanStatus() != FileMetadata.ScanStatus.CLEAN) {
                throw failure(409, "FILE_NOT_CLEAN");
            }
        }
    }
}
