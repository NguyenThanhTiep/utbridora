package vn.utbridora.auth.modules.matching.advisory;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.student.port.*;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class AdvisoryService {
    private final AdvisoryRepository store;private final MatchingService matching;private final AdvisoryAuthority authority;
    private final StudentSemanticSnapshotPort students;private final DemandSemanticSnapshotPort demands;
    private final OperationLedger ledger;private final AuditService audit;private final Environment env;
    public AdvisoryService(AdvisoryRepository store,MatchingService matching,AdvisoryAuthority authority,StudentSemanticSnapshotPort students,DemandSemanticSnapshotPort demands,OperationLedger ledger,AuditService audit,Environment env) {this.store=store;this.matching=matching;this.authority=authority;this.students=students;this.demands=demands;this.ledger=ledger;this.audit=audit;this.env=env;}
    public boolean enabled() {return env.getProperty("app.matching.advisory.enabled",Boolean.class,false);}
    @Transactional public Accepted create(Authentication auth,String runId,String key,Create input) {
        runId=uuid(runId);uuid(key);expectedVersion(input.expectedDemandVersion());expectedVersion(input.expectedCriteriaVersion());
        if(input.expectedProfileSnapshotVersion()==null || !input.expectedProfileSnapshotVersion().matches("[a-f0-9]{64}")) throw failure(400,"SCHEMA_VALIDATION");
        var stored=matching.read(auth,runId,"matching:school:detail_read");authority.require(auth,stored.schoolOrgId(),stored.termId(),true);checkEnabled();
        final String id=runId;
        return ledger.execute(new OperationLedger.Command(actor(auth),stored.schoolOrgId(),"AI_ADVISORY_CREATE",key,Map.of("runId",id,"input",input),202),Accepted.class,()->{
            var r=stored.run();
            if(!store.schoolOrigin(id) || !r.state().equals("COMPLETED")) throw failure(409,"AI_ADVISORY_RUN_NOT_ELIGIBLE");
            if(r.demandVersion()!=input.expectedDemandVersion() || r.criteriaVersion()!=input.expectedCriteriaVersion() || !r.profileSnapshotVersion().equals(input.expectedProfileSnapshotVersion())) throw failure(409,"AI_ADVISORY_SOURCE_STALE");
            store.admission(id,stored.schoolOrgId());var stamp=authority.capture(auth,stored.schoolOrgId(),stored.termId());
            Instant now=Instant.now(),until=now.plusSeconds(86400).isBefore(stamp.until())?now.plusSeconds(86400):stamp.until();
            var demand=demands.capture(auth,r.demandId(),r.demandVersion(),r.criteriaVersion(),r.criteriaHash(),until);
            String query=AdvisoryPolicy.query(demand);if(!AdvisoryPolicy.bounded(query)) throw failure(409,"AI_ADVISORY_INPUT_TOO_LARGE");
            var matches=store.population(id);
            var refs=matches.stream().map(p->new StudentProfileSnapshotPort.DecisionProfileRef(p.studentUserId(),p.profileSnapshotId(),p.profileVersion(),p.relevantHash())).toList();
            // Academic version is captured by the authoritative canonical dataset, not a client field.
            var set=students.capture(auth,stored.schoolOrgId(),stored.termId(),r.datasetSnapshotId(),r.taxonomyVersion(),refs,until);
            long bytes=query.getBytes(StandardCharsets.UTF_8).length;String cursor=null;
            do {var batch=students.page(auth,set.setId(),cursor,100);if(batch.isEmpty()) break;
                for(var e:batch) {bytes+=AdvisoryPolicy.passage(e).getBytes(StandardCharsets.UTF_8).length;if(bytes>32L*1024*1024) throw failure(409,"AI_ADVISORY_LIMIT_EXCEEDED");}
                cursor=batch.getLast().studentUserId();
            } while(true);
            String advisory=UUID.randomUUID().toString();
            var data=new Data(advisory,r,stored.schoolOrgId(),stored.termId(),set.setId(),demand.id(),stamp,now,until,versions(r),AdvisoryPolicy.inputHash("query",query),set.sourceSetHash());store.insert(data);
            audit.workflow(actor(auth),"AI_ADVISORY_REQUESTED",advisory,data.school(),Map.of("matchRunId",id,"schoolOrgId",data.school(),"termId",data.term(),"policyVersion",AdvisoryPolicy.VERSION,"modelRevision",data.versions().modelRevision()));
            return new Accepted(advisory,id,now);
        },Accepted::advisoryId);
    }
    @Transactional(noRollbackFor=vn.utbridora.auth.operations.OperationFailure.class)
    public Metadata metadata(Authentication auth,String id) {var s=checked(auth,id);var d=s.data();audit.workflow(actor(auth),"AI_ADVISORY_VIEWED",d.id(),d.school(),Map.of("matchRunId",d.run().matchRunId(),"page",0,"size",0,"viewKind","METADATA"));return new Metadata(d.id(),d.run().matchRunId(),d.school(),d.term(),d.run().demandId(),s.state(),d.createdAt(),s.completedAt(),d.expiresAt(),d.versions(),readable(s.state())?s.counts():null,s.failure());}
    @Transactional(noRollbackFor=vn.utbridora.auth.operations.OperationFailure.class)
    public Page items(Authentication auth,String id,int page,int size) {
        page(page,size);var s=checked(auth,id);var d=s.data();
        switch(s.state()) {case "REVOKED"->throw failure(404,"RESOURCE_NOT_FOUND");case "EXPIRED"->throw failure(410,"AI_ADVISORY_EXPIRED");case "STALE"->throw failure(409,"AI_ADVISORY_SOURCE_STALE");default->{if(!readable(s.state())) throw failure(409,"AI_ADVISORY_NOT_READY");}}
        var c=s.counts();audit.workflow(actor(auth),"AI_ADVISORY_VIEWED",d.id(),d.school(),Map.of("matchRunId",d.run().matchRunId(),"page",page,"size",size,"viewKind","ITEMS"));
        return new Page(d.id(),d.run().matchRunId(),s.state(),d.versions().policyVersion(),d.expiresAt(),c.eligibleCount(),c.scoredCount(),c.eligibleCount()==0?0:(double)c.scoredCount()/c.eligibleCount(),page,size,c.eligibleCount(),(c.eligibleCount()+size-1)/size,store.items(d.id(),page,size));
    }
    private Stored checked(Authentication auth,String id) {
        var s=store.load(uuid(id),true);var d=s.data();authority.require(auth,d.school(),d.term(),false);checkEnabled();
        String invalid=null,reason=null;
        if(authority.current(d.stamp()).isEmpty()) {invalid="REVOKED";reason="AUTHORITY_REVOKED";}
        else if(!Instant.now().isBefore(d.expiresAt())) {invalid="EXPIRED";reason="TTL_EXPIRED";}
        else if(!Set.of("STALE","REVOKED","EXPIRED").contains(s.state()) && (!d.versions().equals(versions(d.run())) || !demands.current(auth,d.demandSnapshot()) || !students.current(auth,d.studentSet()))) {invalid="STALE";reason="SOURCE_CHANGED";}
        if(invalid!=null && !s.state().equals(invalid)) {store.invalidate(s,invalid,reason);audit.workflow(null,"AI_ADVISORY_INVALIDATED",d.id(),d.school(),Map.of("matchRunId",d.run().matchRunId(),"state",invalid,"reasonCode",reason));s=store.load(d.id(),false);}
        return s;
    }
    private void checkEnabled() {if(!enabled()) throw failure(503,"AI_ADVISORY_DISABLED");}
    private static void expectedVersion(Long value) {if(value==null || value<1 || value>9_007_199_254_740_991L)throw failure(400,"SCHEMA_VALIDATION");}
}
