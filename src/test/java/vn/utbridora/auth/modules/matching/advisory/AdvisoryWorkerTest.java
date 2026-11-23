package vn.utbridora.auth.modules.matching.advisory;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import vn.utbridora.auth.modules.matching.semantic.*;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Real worker orchestration/transactions with synthetic ports/client; not MySQL/inference evidence. */
class AdvisoryWorkerTest {
    final String id="a17e2026-1121-4000-8000-000000000001";
    AdvisoryRepository store;AdvisoryAuthority authority;StudentSemanticSnapshotPort students;DemandSemanticSnapshotPort demands;AdvisoryService feature;AuditService audit;SemanticHttpClient client;AdvisoryWorker worker;Stored job;Authentication auth;
    DemandSemanticSnapshotPort.Snapshot demand;
    @BeforeEach void setup() {
        store=mock(AdvisoryRepository.class);authority=mock(AdvisoryAuthority.class);students=mock(StudentSemanticSnapshotPort.class);demands=mock(DemandSemanticSnapshotPort.class);feature=mock(AdvisoryService.class);audit=mock(AuditService.class);client=mock(SemanticHttpClient.class);auth=mock(Authentication.class);
        worker=new AdvisoryWorker(store,authority,students,demands,feature,audit,new NoDbManager(),client);
        var run=new MatchingService.MatchRunRef(id,id,2,1,"a".repeat(64),id,"b".repeat(64),"strict-v1","tax-v1","preferred-v1","COMPLETED",Instant.now(),Instant.now(),null);
        var stamp=new AdvisoryAuthority.Stamp(id,1,id,id,List.of(),List.of(),Instant.now().plusSeconds(1000));
        demand=new DemandSemanticSnapshotPort.Snapshot(id,id,id,id,2,1,"a".repeat(64),"Backend","Xây API Java", "c".repeat(64),Instant.now(),Instant.now().plusSeconds(1000));resetData(run,stamp);
        when(feature.enabled()).thenReturn(true);when(store.live(any())).thenReturn(true);when(authority.current(stamp)).thenReturn(Optional.of(auth));when(demands.current(auth,id)).thenReturn(true);when(students.current(auth,id)).thenReturn(true);when(demands.read(auth,id)).thenAnswer(i->demand);
        population(2,false);
        when(client.compare(anyString(),anyList(),any())).thenAnswer(i->{List<Passage> ps=i.getArgument(1);return ps.stream().map(p->new Pair(p.key(),"READY",p.key().equals("0")?.6:.8,1,1,false)).toList();});
    }
    void resetData(MatchingService.MatchRunRef run,AdvisoryAuthority.Stamp stamp) {var d=new Data(id,run,id,id,id,id,stamp,Instant.now(),Instant.now().plusSeconds(1000),versions(run),AdvisoryPolicy.inputHash("query",AdvisoryPolicy.query(demand)),"d".repeat(64));job=new Stored(d,"RUNNING",null,null,null,1,1,Instant.now().plusSeconds(90));}
    void population(int count,boolean large) {
        var entries=new ArrayList<StudentSemanticSnapshotPort.Entry>();var matches=new ArrayList<MatchResult>();
        for(int i=1;i<=count;i++) {var e=AdvisoryPolicyTest.entry(i);if(large)e=new StudentSemanticSnapshotPort.Entry(e.studentUserId(),e.profileSnapshotId(),1,e.relevantHash(),e.semanticSnapshotId(),e.semanticSourceHash(),"AVAILABLE",List.of(),List.of(new StudentSemanticSnapshotPort.Project(id,"😀".repeat(200),"😀".repeat(5000),"😀".repeat(200),List.of(),"SELF_DECLARED")));entries.add(e);matches.add(new MatchResult(e.studentUserId(),e.profileSnapshotId(),1,e.relevantHash(),"MATCH",BigDecimal.valueOf(100-i),List.of()));}
        when(store.population(id)).thenReturn(matches);when(students.page(eq(auth),eq(id),isNull(),eq(100))).thenReturn(entries);when(students.page(eq(auth),eq(id),notNull(),eq(100))).thenReturn(List.of());
    }
    @AfterEach void close(){worker.close();}
    @Test @SuppressWarnings("unchecked") void publishesCompleteRankedSnapshotAfterAllInference() {worker.process(job);var rows=ArgumentCaptor.forClass(List.class);verify(store).publish(eq(job),rows.capture(),eq(new Counts(2,2,0,0)),isNull(),anyMap());var values=(List<AdvisoryPolicy.Item>)rows.getValue();assertEquals(AdvisoryPolicyTest.entry(2).studentUserId(),values.getFirst().studentUserId());assertFalse(TransactionSynchronizationManager.isActualTransactionActive());}
    @Test void laterBatchProtocolFailureNeverPublishesFirstBatch() {population(17,false);when(client.compare(anyString(),anyList(),any())).thenAnswer(i->{List<Passage> ps=i.getArgument(1);return ps.stream().map(p->new Pair(p.key(),"READY",.5,1,1,false)).toList();}).thenThrow(new Unavailable("AI_INVALID_RESPONSE"));worker.process(job);verify(store,never()).publish(any(),anyList(),any(),any(),anyMap());verify(store).fail(job,"PROTOCOL_INVALID",false);}
    @Test void authorityRevokedAfterInferenceCannotPublish() {var revoked=new AtomicBoolean();when(authority.current(job.data().stamp())).thenAnswer(i->revoked.get()?Optional.empty():Optional.of(auth));when(client.compare(anyString(),anyList(),any())).thenAnswer(i->{revoked.set(true);List<Passage> ps=i.getArgument(1);return ps.stream().map(p->new Pair(p.key(),"READY",.5,1,1,false)).toList();});worker.process(job);verify(store).invalidate(job,"REVOKED","AUTHORITY_REVOKED");verify(store,never()).publish(any(),anyList(),any(),any(),anyMap());}
    @Test void lostLeaseCannotOverwriteNewOwner() {var lost=new AtomicBoolean();when(store.live(job)).thenAnswer(i->!lost.get());when(client.compare(anyString(),anyList(),any())).thenAnswer(i->{lost.set(true);List<Passage> ps=i.getArgument(1);return ps.stream().map(p->new Pair(p.key(),"READY",.5,1,1,false)).toList();});worker.process(job);verify(store,never()).publish(any(),anyList(),any(),any(),anyMap());}
    @Test void emptyDemandDoesNotCallModelAndPreservesAllCandidates() {demand=new DemandSemanticSnapshotPort.Snapshot(id,id,id,id,2,1,"a".repeat(64),"Backend","","c".repeat(64),Instant.now(),Instant.now().plusSeconds(1000));resetData(job.data().run(),job.data().stamp());worker.process(job);verify(client,never()).compare(anyString(),anyList(),any());verify(store).publish(eq(job),anyList(),eq(new Counts(2,0,2,0)),eq("DEMAND_TEXT_EMPTY"),anyMap());}
    @Test void unicodeBatchesRespectSerializedByteLimitBeforeSending() {population(13,true);when(client.compare(anyString(),anyList(),any())).thenAnswer(i->{String query=i.getArgument(0);List<Passage> ps=i.getArgument(1);assertTrue(AdvisoryWorker.requestBytes(query,ps)<=262144);return ps.stream().map(p->new Pair(p.key(),"READY",.5,1,1,true)).toList();});worker.process(job);verify(client,atLeast(2)).compare(anyString(),anyList(),any());verify(store).publish(eq(job),anyList(),eq(new Counts(13,13,0,0)),isNull(),anyMap());}
    @Test void disabledFlagPreventsOutboundAndPublication() {when(feature.enabled()).thenReturn(false);worker.process(job);verify(client,never()).compare(anyString(),anyList(),any());verify(store,never()).publish(any(),anyList(),any(),any(),anyMap());}
    static final class NoDbManager extends AbstractPlatformTransactionManager {protected Object doGetTransaction(){return new Object();}protected void doBegin(Object t,TransactionDefinition d){}protected void doCommit(DefaultTransactionStatus s){}protected void doRollback(DefaultTransactionStatus s){}}
}
