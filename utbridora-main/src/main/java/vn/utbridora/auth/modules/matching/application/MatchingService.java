package vn.utbridora.auth.modules.matching.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.modules.cooperation.integration.DemandCriteriaPort;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** School API only. Enterprise protected releases remain closed until the privacy gate is implemented. */
@Service
public class MatchingService {
    public record Evaluate(String demandId,Long expectedDemandVersion,Long expectedCriteriaVersion) {}
    public record MatchRunRef(String matchRunId,String demandId,long demandVersion,long criteriaVersion,String criteriaHash,
        String datasetSnapshotId,String profileSnapshotVersion,String ruleVersion,String taxonomyVersion,String scoringVersion,
        String state,Instant createdAt,Instant completedAt,String failureCode) {}
    public record Summary(MatchRunRef run,int matched,int missingData,int notMatched) {}
    public record Evaluated(String audience,Summary summary) {}
    public record Details(MatchRunRef run,Page<MatchResult> results) {}
    public record StoredRun(MatchRunRef run,String schoolOrgId,String enterpriseOrgId,String termId) {}
    private final JdbcTemplate jdbc;private final DemandCriteriaPort demands;private final StudentProfileSnapshotPort profiles;
    private final SchoolDelegationAccess access;private final OperationLedger ledger;private final AuditService audit;
    public MatchingService(JdbcTemplate jdbc,DemandCriteriaPort demands,StudentProfileSnapshotPort profiles,SchoolDelegationAccess access,OperationLedger ledger,AuditService audit) {
        this.jdbc=jdbc;this.demands=demands;this.profiles=profiles;this.access=access;this.ledger=ledger;this.audit=audit;
    }
    @Transactional public Evaluated evaluate(Authentication auth,String key,Evaluate input) {
        uuid(input.demandId());version(input.expectedDemandVersion());version(input.expectedCriteriaVersion());
        var criteria=demands.readForSchool(auth,input.demandId(),null);
        access.require(auth,criteria.schoolOrgId(),criteria.termId(),"matching:run:execute");
        return ledger.execute(new OperationLedger.Command(actor(auth),criteria.schoolOrgId(),"MATCH_EVALUATE",key,input,200),Evaluated.class,()->{
            if(criteria.demandVersion()!=input.expectedDemandVersion() || criteria.criteriaVersion()!=input.expectedCriteriaVersion()) throw failure(409,"CRITERIA_STALE");
            var dataset=profiles.captureForMatching(auth,criteria.termId(),criteria.academicCatalogVersion(),criteria.taxonomyVersion(),key);
            String id=UUID.randomUUID().toString();Instant now=Instant.now();
            audit.workflow(actor(auth),"MATCH_RUN_REQUESTED",id,criteria.schoolOrgId(),Map.of("demandId",criteria.demandId(),"criteriaHash",criteria.criteriaHash(),"criteriaVersion",criteria.criteriaVersion(),"ruleVersion",StrictClassifier.RULE_VERSION,"taxonomyVersion",criteria.taxonomyVersion()));
            Map<String,String> aliases=new HashMap<>();
            jdbc.query("SELECT a.alias,s.skill_code FROM skill_aliases a JOIN skills s ON s.id=a.skill_id WHERE s.status='ACTIVE' FOR SHARE",r->{aliases.put(r.getString(1),r.getString(2));});
            var results=new ArrayList<MatchResult>();String cursor=null;
            do {
                var p=profiles.readPage(auth,dataset.datasetSnapshotId(),cursor,100);
                for(var profile:p.items()) results.add(StrictClassifier.evaluate(profile,criteria.required(),criteria.preferred(),aliases));
                cursor=p.nextCursor();
            } while(cursor!=null);
            var run=new MatchRunRef(id,criteria.demandId(),criteria.demandVersion(),criteria.criteriaVersion(),criteria.criteriaHash(),dataset.datasetSnapshotId(),dataset.profileSnapshotVersion(),StrictClassifier.RULE_VERSION,criteria.taxonomyVersion(),StrictClassifier.SCORING_VERSION,"COMPLETED",now,Instant.now(),null);
            var stored=new StoredRun(run,criteria.schoolOrgId(),criteria.enterpriseOrgId(),criteria.termId());
            jdbc.update("INSERT INTO match_runs(id,demand_id,school_org_id,enterprise_org_id,term_id,dataset_id,data_json) VALUES(?,?,?,?,?,?,?)",id,run.demandId(),stored.schoolOrgId(),stored.enterpriseOrgId(),stored.termId(),run.datasetSnapshotId(),JSON.writeValueAsString(stored));
            for(var result:results) jdbc.update("INSERT INTO match_results VALUES(?,?,?,?,?)",id,result.studentUserId(),result.overallStatus(),result.fitScore(),JSON.writeValueAsString(result));
            audit.workflow(actor(auth),"MATCH_RUN_COMPLETED",id,criteria.schoolOrgId(),Map.of("demandId",criteria.demandId(),"runVersion",1,"status","COMPLETED","ruleVersion",StrictClassifier.RULE_VERSION));
            return new Evaluated("SCHOOL",new Summary(run,(int)results.stream().filter(r->r.overallStatus().equals("MATCH")).count(),(int)results.stream().filter(r->r.overallStatus().equals("MISSING_DATA")).count(),(int)results.stream().filter(r->r.overallStatus().equals("NOT_MATCH")).count()));
        },r->r.summary().run().matchRunId());
    }
    @Transactional public Details details(Authentication auth,String id,String status,int page,int size) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM memberships m JOIN users u ON u.id=m.user_id JOIN organizations o ON o.id=m.organization_id JOIN role_permissions p ON p.role=m.role WHERE m.user_id=? AND m.role='SCHOOL_STAFF' AND m.active=true AND u.status='ACTIVE' AND u.security_version=? AND o.status='VERIFIED' AND p.permission_code='matching:school:detail_read'",Integer.class,actor(auth),((vn.utbridora.auth.security.AppPrincipal)auth.getPrincipal()).securityVersion())==0) throw failure(403,"ACCESS_DENIED");
        page(page,size);if(!Set.of("MATCH","NOT_MATCH","MISSING_DATA").contains(status)) throw failure(400,"SCHEMA_VALIDATION");
        var stored=read(auth,id,"matching:school:detail_read");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM match_results WHERE run_id=? AND overall_status=?",Long.class,id,status);
        var rows=jdbc.query("SELECT data_json FROM match_results WHERE run_id=? AND overall_status=? ORDER BY fit_score DESC,student_user_id ASC LIMIT ? OFFSET ?",(r,n)->JSON.readValue(r.getString(1),MatchResult.class),id,status,size,page*size);
        audit.workflow(actor(auth),"MATCH_DETAILS_VIEWED",id,stored.schoolOrgId(),Map.of("schoolOrgId",stored.schoolOrgId(),"termId",stored.termId(),"page",page,"size",size));
        return new Details(stored.run(),new Page<>(rows,page,size,count,(int)((count+size-1)/size)));
    }
    @Transactional public StoredRun read(Authentication auth,String id,String permission) {
        var run=jdbc.query("SELECT data_json FROM match_runs WHERE id=?",(r,n)->JSON.readValue(r.getString(1),StoredRun.class),uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        access.require(auth,run.schoolOrgId(),run.termId(),permission);return run;
    }
}
