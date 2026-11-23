package vn.utbridora.auth.modules.matching.advisory;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.matching.semantic.*;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Bounded daemon; timer never waits on CPU/network work. Durable DB is the queue. */
@Component @ConditionalOnProperty(name={"app.matching.advisory.enabled","app.matching.advisory.worker-enabled"},havingValue="true")
public class AdvisoryWorker implements AutoCloseable {
    private final AdvisoryRepository store;private final AdvisoryAuthority authority;private final StudentSemanticSnapshotPort students;private final DemandSemanticSnapshotPort demands;
    private final AdvisoryService feature;private final AuditService audit;private final TransactionTemplate tx;private final SemanticHttpClient client;
    private final ExecutorService executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"school-ai-advisory");t.setDaemon(true);return t;});
    private final AtomicBoolean busy=new AtomicBoolean();private volatile boolean closed;private volatile Stored active;private volatile long lastHeartbeat;
    @org.springframework.beans.factory.annotation.Autowired
    public AdvisoryWorker(AdvisoryRepository store,AdvisoryAuthority authority,StudentSemanticSnapshotPort students,DemandSemanticSnapshotPort demands,AdvisoryService feature,AuditService audit,PlatformTransactionManager manager,Environment env) {
        this(store,authority,students,demands,feature,audit,manager,createClient(env));
    }
    AdvisoryWorker(AdvisoryRepository store,AdvisoryAuthority authority,StudentSemanticSnapshotPort students,DemandSemanticSnapshotPort demands,AdvisoryService feature,AuditService audit,PlatformTransactionManager manager,SemanticHttpClient client) {
        this.store=store;this.authority=authority;this.students=students;this.demands=demands;this.feature=feature;this.audit=audit;this.tx=new TransactionTemplate(manager);
        this.client=client;
    }
    private static SemanticHttpClient createClient(Environment env) {
        String revision=env.getProperty("app.matching.semantic.model-revision",REVISION);if(!REVISION.equals(revision)) throw new IllegalArgumentException("AI revision differs from model lock");
        return new SemanticHttpClient(URI.create(env.getRequiredProperty("app.matching.semantic.base-url")),env.getRequiredProperty("app.matching.semantic.token"),revision,Duration.ofSeconds(12),ADVISORY_TEMPLATE);
    }
    @Scheduled(fixedDelay=1000) public void tick() {
        if(closed || !feature.enabled()) return;
        try {
            if(active!=null && System.nanoTime()-lastHeartbeat>TimeUnit.SECONDS.toNanos(15)) {var lease=active;tx.executeWithoutResult(s->store.heartbeat(lease));lastHeartbeat=System.nanoTime();}
            if(busy.compareAndSet(false,true)) executor.submit(()->{try {var job=tx.execute(s->{var claimed=store.claim();if(claimed!=null && claimed.state().equals("FAILED")){audit.workflowFailure(null,"AI_ADVISORY_FAILED",claimed.data().id(),claimed.data().school(),Map.of("matchRunId",claimed.data().run().matchRunId(),"failureCode","WORK_TIMEOUT","attempt",claimed.attempt()));return null;}return claimed;});if(job!=null){active=job;lastHeartbeat=System.nanoTime();process(job);}}catch(RuntimeException failure){/* Next poll recovers a durable lease; never log source payloads. */}finally{active=null;busy.set(false);}});
        } catch(RejectedExecutionException stopping) {busy.set(false);}
        catch(RuntimeException unavailable) {/* Dependency failure is retried by the next bounded timer tick. */}
    }
    private Authentication validate(Stored s) {
        if(closed || !feature.enabled() || !store.live(s)) return null;
        var auth=authority.current(s.data().stamp()).orElse(null);
        if(auth==null) {invalidate(s,"REVOKED","AUTHORITY_REVOKED");return null;}
        if(!s.data().versions().equals(versions(s.data().run())) || !demands.current(auth,s.data().demandSnapshot()) || !students.current(auth,s.data().studentSet())) {invalidate(s,"STALE","SOURCE_CHANGED");return null;}
        return auth;
    }
    void process(Stored job) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(600);
        try {
            var context=tx.execute(s->{var auth=validate(job);return auth==null?null:demands.read(auth,job.data().demandSnapshot());});if(context==null)return;
            String query=AdvisoryPolicy.query(context);if(!AdvisoryPolicy.inputHash("query",query).equals(job.data().queryHash()))throw new Unavailable("AI_INVALID_RESPONSE");boolean noQuery=context.description().isBlank();
            var strict=tx.execute(s->store.population(job.data().run().matchRunId()));var fits=new HashMap<String,java.math.BigDecimal>();for(var r:strict) fits.put(r.studentUserId(),r.fitScore());
            var result=new ArrayList<AdvisoryPolicy.Unranked>();var pending=new ArrayList<Passage>();var pendingEntries=new ArrayList<StudentSemanticSnapshotPort.Entry>();
            String cursor=null;
            do {
                if(closed || !feature.enabled() || System.nanoTime()>=deadline) throw new Unavailable("AI_TIMEOUT");
                final String after=cursor;
                var entries=tx.execute(s->{if(!store.live(job) || closed || !feature.enabled())return null;var auth=authority.current(job.data().stamp()).orElse(null);if(auth==null){invalidate(job,"REVOKED","AUTHORITY_REVOKED");return null;}return students.page(auth,job.data().studentSet(),after,100);});
                if(entries==null)return;if(entries.isEmpty())break;
                for(var e:entries) {
                    if(!fits.containsKey(e.studentUserId())) throw new Unavailable("AI_INVALID_RESPONSE");
                    String passage=AdvisoryPolicy.passage(e);
                    String reason=noQuery?"DEMAND_TEXT_EMPTY":!e.sourceStatus().equals("AVAILABLE")?"PROFILE_TEXT_EMPTY":!AdvisoryPolicy.bounded(passage)?"INPUT_TOO_LARGE":null;
                    if(reason!=null) result.add(new AdvisoryPolicy.Unranked(e,fits.get(e.studentUserId()),null,null,reason));
                    else {
                        var p=new Passage(Integer.toString(result.size()+pending.size()),passage);
                        var proposed=new ArrayList<>(pending);proposed.add(p);
                        if(pending.size()==16 || requestBytes(query,proposed)>262144) {flush(job,query,pending,pendingEntries,result,fits,deadline);pending.clear();pendingEntries.clear();}
                        pending.add(p);pendingEntries.add(e);
                    }
                }
                cursor=entries.getLast().studentUserId();
            } while(true);
            flush(job,query,pending,pendingEntries,result,fits,deadline);
            if(result.size()!=fits.size()) throw new Unavailable("AI_INVALID_RESPONSE");
            var rows=AdvisoryPolicy.rank(result);int scored=(int)rows.stream().filter(r->r.scoreStatus().equals("SCORED")).count();int large=(int)rows.stream().filter(r->r.scoreStatus().equals("INPUT_TOO_LARGE")).count();
            var counts=new Counts(rows.size(),scored,rows.size()-scored-large,large);
            var hashes=new HashMap<String,String>();for(var r:result)hashes.put(r.entry().studentUserId(),AdvisoryPolicy.inputHash("passage",AdvisoryPolicy.passage(r.entry())));
            tx.executeWithoutResult(s->{if(validate(job)==null)return;store.publish(job,rows,counts,scored>0?null:rows.isEmpty()?"EMPTY_MATCH_SET":noQuery?"DEMAND_TEXT_EMPTY":"NO_PROFILE_TEXT",hashes);
                audit.workflow(null,"AI_ADVISORY_COMPLETED",job.data().id(),job.data().school(),Map.of("matchRunId",job.data().run().matchRunId(),"state",scored>0?"READY":"NO_DATA","eligibleCount",rows.size(),"scoredCount",scored,"policyVersion",AdvisoryPolicy.VERSION,"modelRevision",REVISION));});
        } catch(RuntimeException failure) {
            boolean retry=failure instanceof Unavailable u && Set.of("AI_TIMEOUT","AI_UNAVAILABLE").contains(u.getMessage());
            String reason=retry?"INFERENCE_UNAVAILABLE":failure instanceof Unavailable?"PROTOCOL_INVALID":"INFERENCE_UNAVAILABLE";
            tx.executeWithoutResult(s->{if(!store.live(job))return;store.fail(job,reason,retry);if(!retry || job.attempt()>=3) audit.workflowFailure(null,"AI_ADVISORY_FAILED",job.data().id(),job.data().school(),Map.of("matchRunId",job.data().run().matchRunId(),"failureCode",reason,"attempt",job.attempt()));});
        }
    }
    private void flush(Stored job,String query,List<Passage> batch,List<StudentSemanticSnapshotPort.Entry> entries,List<AdvisoryPolicy.Unranked> rows,Map<String,java.math.BigDecimal> fits,long deadline) {
        if(batch.isEmpty())return;
        if(closed || !feature.enabled() || System.nanoTime()>=deadline) throw new Unavailable("AI_TIMEOUT");
        // Recheck exact initiating grants immediately before any outbound request.
        Boolean allowed=tx.execute(s->{if(!store.live(job))return false;if(authority.current(job.data().stamp()).isEmpty()){invalidate(job,"REVOKED","AUTHORITY_REVOKED");return false;}return true;});if(!Boolean.TRUE.equals(allowed)) throw new Unavailable("AI_UNAVAILABLE");
        var scores=client.compare(query,List.copyOf(batch),Duration.ofNanos(deadline-System.nanoTime()));
        for(int i=0;i<scores.size();i++) {var pair=scores.get(i);if(!"READY".equals(pair.status()))throw new Unavailable("AI_INVALID_RESPONSE");var e=entries.get(i);rows.add(new AdvisoryPolicy.Unranked(e,fits.get(e.studentUserId()),pair.score(),pair.limited(),null));}
    }
    static int requestBytes(String query,List<Passage> passages) {return JSON.writeValueAsBytes(new Request(SCHEMA,"00000000-0000-4000-8000-000000000000",ADVISORY_TEMPLATE,query,passages)).length;}
    private void invalidate(Stored s,String state,String reason) {store.invalidate(s,state,reason);audit.workflow(null,"AI_ADVISORY_INVALIDATED",s.data().id(),s.data().school(),Map.of("matchRunId",s.data().run().matchRunId(),"state",state,"reasonCode",reason));}
    @Override @jakarta.annotation.PreDestroy public void close() {closed=true;executor.shutdownNow();client.close();}
}
