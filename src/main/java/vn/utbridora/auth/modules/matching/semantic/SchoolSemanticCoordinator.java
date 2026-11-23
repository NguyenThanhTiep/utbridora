package vn.utbridora.auth.modules.matching.semantic;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandCriteriaSnapshot;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticWork.*;

/** Ephemeral shadow jobs. Results have no public reader and cannot participate in a decision. */
final class SchoolSemanticCoordinator implements SchoolSemanticObserver {
    private final Client client;private final String revision;private final int maxProfiles,retainedRuns;
    private final Duration ttl,workTimeout;private final Clock clock;private final ThreadPoolExecutor worker;
    private final LinkedHashMap<String,Outcome> outcomes=new LinkedHashMap<>();
    private final Set<String> active=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed=new AtomicBoolean();
    private final AtomicLong accepted=new AtomicLong(),ready=new AtomicLong(),failed=new AtomicLong(),rejected=new AtomicLong();

    SchoolSemanticCoordinator(Client client,String revision,int queueCapacity,int retainedRuns,int maxProfiles,
                              Duration ttl,Duration workTimeout,Clock clock) {
        if(queueCapacity<1 || queueCapacity>8 || retainedRuns<1 || retainedRuns>32 || maxProfiles<1 || maxProfiles>5000
            || ttl.compareTo(Duration.ofSeconds(1))<0 || ttl.compareTo(Duration.ofMinutes(30))>0
            || workTimeout.compareTo(Duration.ofMillis(1))<0 || workTimeout.compareTo(Duration.ofMinutes(3))>0)
            throw new IllegalArgumentException("Invalid semantic resource limits");
        this.client=Objects.requireNonNull(client);this.revision=revision;this.retainedRuns=retainedRuns;
        this.maxProfiles=maxProfiles;this.ttl=ttl;this.workTimeout=workTimeout;this.clock=clock;
        this.worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(queueCapacity),task->{
            var thread=new Thread(task,"matching-semantic-shadow");thread.setDaemon(true);return thread;
        },new ThreadPoolExecutor.AbortPolicy());
    }
    public boolean enabled() {return !closed.get();}
    public int captureLimit() {return maxProfiles;}

    public void afterSchoolCommit(String runId,DemandCriteriaSnapshot criteria,SnapshotSet dataset,List<ProfileSnapshot> profiles) {
        if(!enabled()) return;
        if(!TransactionSynchronizationManager.isSynchronizationActive() || !TransactionSynchronizationManager.isActualTransactionActive()) {
            failed.incrementAndGet();return; // Never silently infer from an uncommitted/nontransactional call.
        }
        Context context=new Context(runId,dataset.schoolOrgId(),dataset.termId(),dataset.datasetSnapshotId(),dataset.profileSnapshotVersion(),
            criteria.demandVersion(),criteria.criteriaVersion(),criteria.criteriaHash(),criteria.taxonomyVersion(),criteria.academicCatalogVersion());
        Runnable after;
        if(profiles.size()>maxProfiles || dataset.profileCount()>maxProfiles) {
            after=()->{rejected.incrementAndGet();remember(outcome(context,null,Status.REJECTED,List.of(),"AI_PROFILE_LIMIT"));};
        } else {
            try {
                var work=SemanticWork.build(runId,criteria,dataset,profiles);
                after=()->offer(work);
            } catch(RuntimeException invalid) {
                after=()->{failed.incrementAndGet();remember(outcome(context,null,Status.FAILED,List.of(),"AI_INVALID_SNAPSHOT"));};
            }
        }
        Runnable action=after;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {action.run();} catch(RuntimeException optionalFailure) {failed.incrementAndGet();}
            }
        });
    }

    private void offer(SemanticWork work) {
        String id=work.context().runId();
        if(closed.get() || !active.add(id)) return;
        if(inspect(id).isPresent()) {active.remove(id);return;}
        remember(outcome(work.context(),work.queryHash(),Status.PENDING,List.of(),null));
        try {worker.execute(()->process(work));accepted.incrementAndGet();}
        catch(RejectedExecutionException full) {
            active.remove(id);rejected.incrementAndGet();
            remember(outcome(work.context(),work.queryHash(),Status.REJECTED,List.of(),"AI_QUEUE_FULL"));
        }
    }
    private void process(SemanticWork work) {
        try {
            if(TransactionSynchronizationManager.isActualTransactionActive()) throw new Unavailable("AI_TRANSACTION_ACTIVE");
            long start=System.nanoTime();var scores=new ArrayList<Score>();
            for(int offset=0;offset<work.items().size();offset+=MAX_BATCH) {
                Duration remaining=workTimeout.minusNanos(System.nanoTime()-start);
                if(remaining.isNegative() || remaining.isZero()) throw new Unavailable("AI_TIMEOUT");
                var batch=work.items().subList(offset,Math.min(offset+MAX_BATCH,work.items().size()));
                var pairs=client.compare(work.query(),batch.stream().map(p->new Passage(p.key(),p.text())).toList(),remaining);
                if(pairs.size()!=batch.size()) throw new Unavailable("AI_INVALID_RESPONSE");
                for(int i=0;i<batch.size();i++) {
                    var pair=pairs.get(i);var item=batch.get(i);
                    if(!item.key().equals(pair.key())) throw new Unavailable("AI_INVALID_RESPONSE");
                    scores.add(new Score(item.ref(),pair.score(),pair.status(),pair.queryChunks(),pair.passageChunks(),pair.limited()));
                }
                if(System.nanoTime()-start>workTimeout.toNanos()) throw new Unavailable("AI_TIMEOUT");
            }
            Status status=scores.stream().anyMatch(s->s.score()!=null)?Status.READY:Status.NO_DATA;
            remember(outcome(work.context(),work.queryHash(),status,List.copyOf(scores),null));ready.incrementAndGet();
        } catch(Unavailable unavailable) {
            failed.incrementAndGet();String code=Set.of("AI_TIMEOUT","AI_UNAVAILABLE","AI_INVALID_RESPONSE","AI_INVALID_INPUT").contains(unavailable.getMessage())?unavailable.getMessage():"AI_UNAVAILABLE";
            remember(outcome(work.context(),work.queryHash(),Status.UNAVAILABLE,List.of(),code));
        } catch(RuntimeException invalid) {
            failed.incrementAndGet();remember(outcome(work.context(),work.queryHash(),Status.FAILED,List.of(),"AI_FAILED"));
        } finally {active.remove(work.context().runId());}
    }
    private Outcome outcome(Context context,String hash,Status status,List<Score> scores,String failure) {
        return new Outcome(context,hash,status,scores,revision,TEMPLATE,AGGREGATION,failure,clock.instant().plus(ttl));
    }
    private synchronized void remember(Outcome value) {
        if(closed.get()) return;
        expire();outcomes.put(value.context().runId(),value);
        while(outcomes.size()>retainedRuns) outcomes.remove(outcomes.keySet().iterator().next());
    }
    private void expire() {outcomes.values().removeIf(v->!v.expiresAt().isAfter(clock.instant()));}
    // Package-private diagnostics only. A future reader must add current authority and stale checks.
    synchronized Optional<Outcome> inspect(String runId) {expire();return Optional.ofNullable(outcomes.get(runId));}
    synchronized int retainedCount() {expire();return outcomes.size();}
    Map<String,Long> counters() {return Map.of("accepted",accepted.get(),"ready",ready.get(),"failed",failed.get(),"rejected",rejected.get());}
    @Override public void close() {
        if(closed.compareAndSet(false,true)) {
            worker.shutdownNow();synchronized(this) {outcomes.clear();}active.clear();
            if(client instanceof AutoCloseable resource) {try {resource.close();} catch(Exception ignored) {}}
        }
    }
}
