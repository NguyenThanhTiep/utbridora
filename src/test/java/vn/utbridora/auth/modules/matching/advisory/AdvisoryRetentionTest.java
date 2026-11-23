package vn.utbridora.auth.modules.matching.advisory;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static org.mockito.Mockito.*;
class AdvisoryRetentionTest {
    final String id="a17e2026-1121-4000-8000-000000000001";
    @Test void cleanupDoesNotDependOnAiFlagOrModelAndHonorsHold() {
        var store=mock(AdvisoryRepository.class);var students=mock(StudentSemanticSnapshotPort.class);var demands=mock(DemandSemanticSnapshotPort.class);var audit=mock(AuditService.class);
        var run=new MatchingService.MatchRunRef(id,id,1,1,"a".repeat(64),id,"b".repeat(64),"strict-v1","tax-v1","preferred-v1","COMPLETED",Instant.now(),Instant.now(),null);
        var stamp=new AdvisoryAuthority.Stamp(id,1,id,id,List.of(),List.of(),Instant.now());
        var data=new Data(id,run,id,id,id,id,stamp,Instant.now().minusSeconds(90000),Instant.now().minusSeconds(4000),versions(run),"c".repeat(64),"d".repeat(64));
        when(store.load(id,true)).thenReturn(new Stored(data,"READY",Instant.now(),null,null,1,1,null));when(store.expired()).thenReturn(List.of(id));when(store.purgeable()).thenReturn(List.of(id));
        var maintenance=new AdvisoryRetention(store,students,demands,audit,new AdvisoryWorkerTest.NoDbManager());maintenance.tick();
        verify(store).invalidate(any(),eq("EXPIRED"),eq("TTL_EXPIRED"));verify(students).purge(id);verify(demands).purge(id);verify(store).purged(id);
        clearInvocations(students,demands,store);when(store.held()).thenReturn(true);maintenance.tick();verifyNoInteractions(students,demands);verify(store,never()).purged(id);
    }
}
