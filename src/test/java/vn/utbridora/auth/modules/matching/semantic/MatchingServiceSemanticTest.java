package vn.utbridora.auth.modules.matching.semantic;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.integration.DemandCriteriaPort;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticFixtures.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Real service/classifier and Spring transaction callbacks; mocked owner ports/JDBC, no database. */
class MatchingServiceSemanticTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final DemandCriteriaPort demands=mock(DemandCriteriaPort.class);
    private final StudentProfileSnapshotPort profiles=mock(StudentProfileSnapshotPort.class);
    private final SchoolDelegationAccess access=mock(SchoolDelegationAccess.class);
    private final AuthorizationPolicy authorization=mock(AuthorizationPolicy.class);
    private final OperationLedger ledger=mock(OperationLedger.class);
    private final AuditService audit=mock(AuditService.class);
    private final AtomicReference<StrictClassifier.MatchResult> written=new AtomicReference<>();
    private final AtomicReference<MatchingService.Evaluated> replay=new AtomicReference<>();
    private final AtomicBoolean failAfterMutation=new AtomicBoolean();
    private final Authentication auth=new UsernamePasswordAuthenticationToken(
        new AppPrincipal(id(9),"synthetic@example.test","not-a-password",1,List.of()),null,List.of());
    private SchoolSemanticCoordinator coordinator;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(jdbc.query(anyString(),any(ResultSetExtractor.class),any(Object[].class))).thenReturn(id(3));
        when(demands.readForSchool(eq(auth),eq(id(1)),isNull())).thenReturn(criteria());
        when(profiles.captureForMatching(eq(auth),eq(id(4)),eq("academic-v1"),eq("tax-v1"),anyString())).thenReturn(dataset(1));
        when(profiles.readPage(eq(auth),eq(id(5)),isNull(),eq(100))).thenReturn(new SnapshotPage(id(5),"dataset-version",List.of(profile(1)),null));
        when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(invocation->{
            if(invocation.getArgument(0,String.class).startsWith("INSERT INTO match_results"))
                written.set(JSON.readValue(invocation.getArgument(5,String.class),StrictClassifier.MatchResult.class));
            return 1;
        });
        when(ledger.execute(any(),eq(MatchingService.Evaluated.class),any(),any())).thenAnswer(invocation->{
            if(replay.get()!=null) return replay.get();
            var result=(MatchingService.Evaluated)invocation.getArgument(2,Supplier.class).get();
            if(failAfterMutation.get()) throw new IllegalStateException("synthetic rollback after callback registration");
            replay.set(result);return result;
        });
    }
    @AfterEach void cleanup() {if(coordinator!=null) coordinator.close();TransactionSynchronizationManager.clear();}

    private MatchingService service(SchoolSemanticObserver observer) {
        var target=new MatchingService(jdbc,demands,profiles,access,authorization,ledger,audit,observer);
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new NoDatabaseTransactionManager(),new AnnotationTransactionAttributeSource()));
        return (MatchingService)factory.getProxy();
    }
    private SchoolSemanticCoordinator shadow(SemanticProtocol.Client client) {
        coordinator=new SchoolSemanticCoordinator(client,SemanticProtocol.REVISION,1,4,5000,Duration.ofMinutes(5),Duration.ofSeconds(2),Clock.systemUTC());
        return coordinator;
    }
    private MatchingService.Evaluated evaluate(MatchingService service) {return service.evaluate(auth,id(10),new MatchingService.Evaluate(id(1),1L,1L));}
    private SemanticWork.Outcome outcome(String run) throws Exception {
        long end=System.nanoTime()+2_000_000_000L;
        do {var result=coordinator.inspect(run);if(result.isPresent() && result.get().status()!=SemanticWork.Status.PENDING) return result.get();Thread.sleep(5);}while(System.nanoTime()<end);
        throw new AssertionError("No terminal shadow result");
    }
    @Test void realCanonicalClassificationIsIdenticalWithFlagOffAndOn() throws Exception {
        var off=evaluate(service(SchoolSemanticObserver.disabled()));var expected=written.get();replay.set(null);
        var on=evaluate(service(shadow((q,p,d)->scores(p))));
        assertEquals(expected,written.get());assertEquals(off.summary().matched(),on.summary().matched());
        assertEquals(off.summary().missingData(),on.summary().missingData());assertEquals(off.summary().notMatched(),on.summary().notMatched());
        assertEquals("COMPLETED",on.summary().run().state());
        assertEquals(SemanticWork.Status.READY,outcome(on.summary().run().matchRunId()).status());
        assertEquals(StrictClassifier.evaluate(profile(1),criteria().required(),criteria().preferred(),Map.of()),written.get());
    }
    @Test void idempotentReplayDoesNotReenqueueOrRecapture() throws Exception {
        var calls=new AtomicInteger();var service=service(shadow((q,p,d)->{calls.incrementAndGet();return scores(p);}));
        var first=evaluate(service);outcome(first.summary().run().matchRunId());var second=evaluate(service);
        assertEquals(first,second);assertEquals(1,calls.get());
        verify(profiles,times(1)).captureForMatching(any(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void rollbackAfterRegistrationDoesNotInfer() {
        var calls=new AtomicInteger();var service=service(shadow((q,p,d)->{calls.incrementAndGet();return scores(p);}));
        failAfterMutation.set(true);assertThrows(IllegalStateException.class,()->evaluate(service));
        assertEquals(0,calls.get());assertEquals(0L,coordinator.counters().get("accepted"));
    }
    @Test void aiFailureDoesNotChangeCanonicalSuccess() throws Exception {
        var service=service(shadow((q,p,d)->{throw new SemanticProtocol.Unavailable("AI_TIMEOUT");}));
        var result=evaluate(service);assertEquals("COMPLETED",result.summary().run().state());assertEquals(1,result.summary().matched());
        assertEquals(SemanticWork.Status.UNAVAILABLE,outcome(result.summary().run().matchRunId()).status());
        assertEquals("MATCH",written.get().overallStatus());
    }
    @Test void observerRegistrationFailureCannotRollbackCanonicalSuccess() {
        var observer=mock(SchoolSemanticObserver.class);when(observer.enabled()).thenReturn(true);when(observer.captureLimit()).thenReturn(5000);
        doThrow(new IllegalStateException("synthetic optional failure")).when(observer).afterSchoolCommit(anyString(),any(),any(),anyList());
        assertEquals("COMPLETED",evaluate(service(observer)).summary().run().state());
    }
    @Test void enterpriseBranchDoesNotTouchSemanticObserver() {
        var observer=mock(SchoolSemanticObserver.class);
        when(authorization.hasOrganization(eq(auth),eq(id(3)),eq(Role.ENTERPRISE),eq("matching:enterprise:aggregate_read"))).thenReturn(true);
        when(jdbc.queryForObject(anyString(),eq(Boolean.class))).thenReturn(false);
        assertThrows(RuntimeException.class,()->evaluate(service(observer)));verifyNoInteractions(observer);
    }
    private static final class NoDatabaseTransactionManager extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() {return new Object();}
        protected void doBegin(Object transaction,TransactionDefinition definition) {}
        protected void doCommit(DefaultTransactionStatus status) {}
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
