package vn.utbridora.auth.modules.matching.advisory;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.student.port.*;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort;
import vn.utbridora.auth.operations.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;

/** Scope/lifecycle unit evidence only; no DB or runtime source-owner replacement claim. */
class AdvisoryServiceTest {
    final AdvisoryRepository store=mock(AdvisoryRepository.class);final MatchingService matching=mock(MatchingService.class);
    final AdvisoryAuthority authority=mock(AdvisoryAuthority.class);final StudentSemanticSnapshotPort students=mock(StudentSemanticSnapshotPort.class);
    final DemandSemanticSnapshotPort demands=mock(DemandSemanticSnapshotPort.class);final OperationLedger ledger=mock(OperationLedger.class);final AuditService audit=mock(AuditService.class);
    final MockEnvironment env=new MockEnvironment();final Authentication auth=mock(Authentication.class);
    final String id="a17e2026-1121-4000-8000-000000000001";
    AdvisoryService service;Data data;Stored ready;
    @BeforeEach void setup() {
        service=new AdvisoryService(store,matching,authority,students,demands,ledger,audit,env);env.setProperty("app.matching.advisory.enabled","true");
        var run=new MatchingService.MatchRunRef(id,id,2,1,"a".repeat(64),id,"b".repeat(64),"strict-v1","tax-v1","preferred-v1","COMPLETED",Instant.now(),Instant.now(),null);
        var stamp=new AdvisoryAuthority.Stamp(id,1,id,id,List.of(),List.of(),Instant.now().plusSeconds(10000));
        data=new Data(id,run,id,id,id,id,stamp,Instant.now(),Instant.now().plusSeconds(3600),versions(run),"c".repeat(64),"d".repeat(64));ready=new Stored(data,"READY",Instant.now(),new Counts(2,1,1,0),null,1,1,Instant.now().plusSeconds(90));
        when(store.load(id,true)).thenReturn(ready);when(authority.current(stamp)).thenReturn(Optional.of(auth));when(demands.current(auth,id)).thenReturn(true);when(students.current(auth,id)).thenReturn(true);
        var principal=new vn.utbridora.auth.security.AppPrincipal(id,"synthetic@example.test",null,1,List.of());when(auth.isAuthenticated()).thenReturn(true);when(auth.getPrincipal()).thenReturn(principal);
    }
    @Test void defaultFeatureOffRequiresNoOwnerCalls() {env.setProperty("app.matching.advisory.enabled","false");var error=assertThrows(OperationFailure.class,()->service.metadata(auth,id));assertEquals("AI_ADVISORY_DISABLED",error.code);verifyNoInteractions(students,demands);}
    @Test void deniedReaderNeverLoadsSourcesOrRows() {doThrow(new OperationFailure(404,"RESOURCE_NOT_FOUND","scope",null)).when(authority).require(auth,id,id,false);assertEquals(404,assertThrows(OperationFailure.class,()->service.items(auth,id,0,25)).status);verifyNoInteractions(students,demands);verify(store,never()).items(anyString(),anyInt(),anyInt());}
    @Test void readyPageKeepsGlobalTotalsAndNullCoverageSemantics() {when(store.items(id,0,25)).thenReturn(List.of());var result=service.items(auth,id,0,25);assertEquals(2,result.totalElements());assertEquals(.5,result.coverage());verify(students).current(auth,id);verify(demands).current(auth,id);}
    @Test void sourceChangeInvalidatesWholeJobBeforeReadingRows() {when(students.current(auth,id)).thenReturn(false);when(store.load(id,false)).thenReturn(new Stored(data,"STALE",ready.completedAt(),null,"SOURCE_CHANGED",1,2,null));assertEquals("AI_ADVISORY_SOURCE_STALE",assertThrows(OperationFailure.class,()->service.items(auth,id,0,25)).code);verify(store).invalidate(ready,"STALE","SOURCE_CHANGED");verify(store,never()).items(anyString(),anyInt(),anyInt());}
    @Test void exactInitiatorRevocationHidesRows() {when(authority.current(data.stamp())).thenReturn(Optional.empty());when(store.load(id,false)).thenReturn(new Stored(data,"REVOKED",ready.completedAt(),null,"AUTHORITY_REVOKED",1,2,null));assertEquals(404,assertThrows(OperationFailure.class,()->service.items(auth,id,0,25)).status);verifyNoInteractions(students,demands);}
    @Test void pendingDoesNotExposePartialRows() {when(store.load(id,true)).thenReturn(new Stored(data,"RUNNING",null,null,null,1,1,ready.leaseUntil()));assertEquals("AI_ADVISORY_NOT_READY",assertThrows(OperationFailure.class,()->service.items(auth,id,0,25)).code);verify(store,never()).items(anyString(),anyInt(),anyInt());}
    @Test void invalidPageDoesNotReadRepository() {assertEquals(400,assertThrows(OperationFailure.class,()->service.items(auth,id,-1,25)).status);verifyNoInteractions(store);}
    @Test void dependencyOutageFailsClosedBeforeRows() {when(demands.current(auth,id)).thenThrow(new OperationFailure(503,"DEPENDENCY_UNAVAILABLE","unavailable",null));assertEquals(503,assertThrows(OperationFailure.class,()->service.items(auth,id,0,25)).status);verify(store,never()).items(anyString(),anyInt(),anyInt());}
    @Test void enterpriseOrUnknownOriginIsRejectedBeforeCapture() {
        when(matching.read(auth,id,"matching:school:detail_read")).thenReturn(new MatchingService.StoredRun(data.run(),id,id,id));
        when(ledger.execute(any(),eq(Accepted.class),any(),any())).thenAnswer(i->i.getArgument(2,java.util.function.Supplier.class).get());
        assertEquals("AI_ADVISORY_RUN_NOT_ELIGIBLE",assertThrows(OperationFailure.class,()->service.create(auth,id,id,new Create(2L,1L,"b".repeat(64)))).code);verifyNoInteractions(students,demands);
    }
    @Test void maximumReadOnlyExpectedVersionUsesSourceStaleRatherThanMutationOverflowError() {
        when(matching.read(auth,id,"matching:school:detail_read")).thenReturn(new MatchingService.StoredRun(data.run(),id,id,id));when(store.schoolOrigin(id)).thenReturn(true);
        when(ledger.execute(any(),eq(Accepted.class),any(),any())).thenAnswer(i->i.getArgument(2,java.util.function.Supplier.class).get());
        assertEquals("AI_ADVISORY_SOURCE_STALE",assertThrows(OperationFailure.class,()->service.create(auth,id,id,new Create(9_007_199_254_740_991L,1L,"b".repeat(64)))).code);
    }
}
