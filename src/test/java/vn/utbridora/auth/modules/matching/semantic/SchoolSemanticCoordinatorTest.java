package vn.utbridora.auth.modules.matching.semantic;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticFixtures.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticWork.Status;

class SchoolSemanticCoordinatorTest {
    private final List<SchoolSemanticCoordinator> coordinators=new ArrayList<>();
    @AfterEach void cleanup() {
        TransactionSynchronizationManager.clear();
        coordinators.forEach(SchoolSemanticCoordinator::close);
    }
    private SchoolSemanticCoordinator coordinator(SemanticProtocol.Client client) {
        var c=new SchoolSemanticCoordinator(client,SemanticProtocol.REVISION,1,2,5000,Duration.ofMinutes(5),Duration.ofSeconds(3),Clock.systemUTC());
        coordinators.add(c);return c;
    }
    private void begin() {TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);}
    private void finish(boolean commit) {
        var callbacks=TransactionSynchronizationManager.getSynchronizations();
        if(commit) callbacks.forEach(TransactionSynchronization::afterCommit);
        callbacks.forEach(s->s.afterCompletion(commit?TransactionSynchronization.STATUS_COMMITTED:TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clear();
    }
    private void submit(SchoolSemanticCoordinator c,int run,int count) {
        begin();c.afterSchoolCommit(id(run),criteria(),dataset(count),java.util.stream.IntStream.range(0,count).mapToObj(SemanticFixtures::profile).toList());finish(true);
    }
    private SemanticWork.Outcome completed(SchoolSemanticCoordinator c,int id) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        do {var value=c.inspect(id(id));if(value.isPresent() && value.get().status()!=Status.PENDING) return value.get();Thread.sleep(5);}
        while(System.nanoTime()<until);
        throw new AssertionError("Shadow work did not terminate");
    }
    @Test void inferenceOccursOnlyAfterCommitOnWorkerWithoutTransaction() throws Exception {
        var calls=new AtomicInteger();
        var c=coordinator((q,p,d)->{assertFalse(TransactionSynchronizationManager.isActualTransactionActive());calls.incrementAndGet();return scores(p);});
        begin();c.afterSchoolCommit(id(6),criteria(),dataset(1),List.of(profile(1)));
        assertEquals(0,calls.get());assertTrue(c.inspect(id(6)).isEmpty());finish(true);
        var result=completed(c,6);assertEquals(Status.READY,result.status());assertEquals(1,calls.get());
        assertEquals("profile-hash-1",result.scores().getFirst().ref().relevantHash());
    }
    @Test void rollbackAndNontransactionalCallsNeverInfer() throws Exception {
        var calls=new AtomicInteger();var c=coordinator((q,p,d)->{calls.incrementAndGet();return scores(p);});
        begin();c.afterSchoolCommit(id(6),criteria(),dataset(1),List.of(profile(1)));finish(false);
        c.afterSchoolCommit(id(7),criteria(),dataset(1),List.of(profile(1)));
        assertEquals(0,calls.get());assertTrue(c.inspect(id(6)).isEmpty());assertTrue(c.inspect(id(7)).isEmpty());
    }
    @Test void boundedQueueRejectsWholeWorkAndNeverBlocksCanonicalCaller() throws Exception {
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        var c=coordinator((q,p,d)->{started.countDown();try {assertTrue(release.await(2,TimeUnit.SECONDS));}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SemanticProtocol.Unavailable("AI_UNAVAILABLE");}return scores(p);});
        try {
            submit(c,6,1);assertTrue(started.await(1,TimeUnit.SECONDS));submit(c,7,1);submit(c,8,1);
            assertEquals(Status.REJECTED,completed(c,8).status());assertEquals(1,c.counters().get("rejected"));
        } finally {release.countDown();}
        assertEquals(Status.READY,completed(c,7).status());
    }
    @Test void laterBatchFailureDoesNotPublishPartialScores() throws Exception {
        var calls=new AtomicInteger();var c=coordinator((q,p,d)->{if(calls.incrementAndGet()==2) throw new SemanticProtocol.Unavailable("AI_TIMEOUT");return scores(p);});
        submit(c,6,17);var result=completed(c,6);
        assertEquals(Status.UNAVAILABLE,result.status());assertEquals("AI_TIMEOUT",result.failureCode());assertTrue(result.scores().isEmpty());
    }
    @Test void batchingPreservesImmutableRefsAndDoesNotSendDomainIdentifiers() throws Exception {
        var sizes=new ArrayList<Integer>();
        var c=coordinator((q,p,d)->{sizes.add(p.size());assertFalse(q.contains(id(2)));p.forEach(v->assertFalse(v.text().contains(id(2))));return scores(p);});
        submit(c,6,17);var result=completed(c,6);
        assertEquals(List.of(16,1),sizes);assertEquals(17,result.scores().size());
        assertEquals(profile(16).studentUserId(),result.scores().get(16).ref().studentUserId());
    }
    @Test void profileCapRejectsRatherThanSampling() throws Exception {
        var c=new SchoolSemanticCoordinator((q,p,d)->{throw new AssertionError("Must not infer");},SemanticProtocol.REVISION,1,2,1,
            Duration.ofMinutes(5),Duration.ofSeconds(1),Clock.systemUTC());coordinators.add(c);
        submit(c,6,2);assertEquals(Status.REJECTED,completed(c,6).status());assertTrue(completed(c,6).scores().isEmpty());
    }
    @Test void emptyDatasetDoesNotFabricateScoresOrCallAi() throws Exception {
        var c=coordinator((q,p,d)->{throw new AssertionError("Must not infer");});submit(c,6,0);
        assertEquals(Status.NO_DATA,completed(c,6).status());assertTrue(completed(c,6).scores().isEmpty());
    }
    @Test void retentionIsBoundedAndExpires() throws Exception {
        var instant=new AtomicReference<>(Instant.now());
        Clock clock=new Clock() {public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return instant.get();}};
        var c=new SchoolSemanticCoordinator((q,p,d)->scores(p),SemanticProtocol.REVISION,1,2,10,Duration.ofSeconds(1),Duration.ofSeconds(1),clock);
        coordinators.add(c);
        for(int run=6;run<=8;run++){submit(c,run,1);completed(c,run);}
        assertEquals(2,c.retainedCount());instant.set(instant.get().plusSeconds(2));assertEquals(0,c.retainedCount());
    }
    @Test void sameImmutableRunIsNotInferredTwiceDuringRetention() throws Exception {
        var calls=new AtomicInteger();var c=coordinator((q,p,d)->{calls.incrementAndGet();return scores(p);});
        submit(c,6,1);completed(c,6);submit(c,6,1);assertEquals(1,calls.get());
    }
}
