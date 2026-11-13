package vn.utbridora.auth.modules.matching.application;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.user.Role;
import vn.utbridora.auth.modules.cooperation.integration.DemandCriteriaPort;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier.MatchResult;
import vn.utbridora.auth.modules.matching.domain.ProtectedAggregatePolicy;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** School matching plus a fail-closed enterprise protected-release path. */
@Service
public class MatchingService {
    public record Evaluate(String demandId,Long expectedDemandVersion,Long expectedCriteriaVersion) {}
    public record MatchRunRef(String matchRunId,String demandId,long demandVersion,long criteriaVersion,String criteriaHash,
        String datasetSnapshotId,String profileSnapshotVersion,String ruleVersion,String taxonomyVersion,String scoringVersion,
        String state,Instant createdAt,Instant completedAt,String failureCode) {}
    public record Summary(MatchRunRef run,int matched,int missingData,int notMatched) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Evaluated(String audience,Summary summary,ProtectedAggregatePolicy.ProtectedAggregate release) {}
    public record Details(MatchRunRef run,Page<MatchResult> results) {}
    public record StoredRun(MatchRunRef run,String schoolOrgId,String enterpriseOrgId,String termId) {}
    private final JdbcTemplate jdbc;private final DemandCriteriaPort demands;private final StudentProfileSnapshotPort profiles;
    private final SchoolDelegationAccess access;private final AuthorizationPolicy authorization;private final OperationLedger ledger;private final AuditService audit;
    public MatchingService(JdbcTemplate jdbc,DemandCriteriaPort demands,StudentProfileSnapshotPort profiles,SchoolDelegationAccess access,AuthorizationPolicy authorization,OperationLedger ledger,AuditService audit) {
        this.jdbc=jdbc;this.demands=demands;this.profiles=profiles;this.access=access;this.authorization=authorization;this.ledger=ledger;this.audit=audit;
    }
    @Transactional public Evaluated evaluate(Authentication auth,String key,Evaluate input) {
        uuid(input.demandId());version(input.expectedDemandVersion());version(input.expectedCriteriaVersion());
        String enterpriseOrg=jdbc.query("SELECT enterprise_org_id FROM demands WHERE id=?",r->r.next()?r.getString(1):null,input.demandId());
        if(enterpriseOrg!=null && authorization.hasOrganization(auth,enterpriseOrg,Role.ENTERPRISE,"matching:enterprise:aggregate_read"))
            return evaluateEnterprise(auth,key,input,enterpriseOrg);
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
            audit.matchingRunCompleted(actor(auth),criteria.demandId(),id,criteria.schoolOrgId(),Map.of("demandId",criteria.demandId(),"runVersion",1,"status","COMPLETED","ruleVersion",StrictClassifier.RULE_VERSION));
            return new Evaluated("SCHOOL",new Summary(run,(int)results.stream().filter(r->r.overallStatus().equals("MATCH")).count(),(int)results.stream().filter(r->r.overallStatus().equals("MISSING_DATA")).count(),(int)results.stream().filter(r->r.overallStatus().equals("NOT_MATCH")).count()),null);
        },r->r.summary().run().matchRunId());
    }

    /** Doanh nghiệp chỉ được đánh giá trên batch đã đóng băng do Ops chọn và chỉ nhận DTO đã bảo vệ. */
    @Transactional public Evaluated evaluateEnterprise(Authentication auth,String key,Evaluate input,String enterpriseOrg) {
        if(jdbc.queryForObject("SELECT enabled FROM matching_privacy_release_settings WHERE id=1",Boolean.class)!=Boolean.TRUE)
            throw failure(503,"PRIVACY_RELEASE_UNAVAILABLE");
        var criteria=demands.read(auth,enterpriseOrg,input.demandId(),input.expectedCriteriaVersion(),DemandCriteriaPort.Purpose.EVALUATE);
        if(criteria.demandVersion()!=input.expectedDemandVersion() || criteria.criteriaVersion()!=input.expectedCriteriaVersion()) throw failure(409,"CRITERIA_STALE");

        var batchRows=jdbc.query("SELECT b.demo_batch_id,b.dataset_snapshot_id FROM matching_active_demo_batches a JOIN matching_demo_batches b ON b.demo_batch_id=a.demo_batch_id AND b.school_org_id=a.school_org_id AND b.term_id=a.term_id WHERE a.school_org_id=? AND a.term_id=? FOR UPDATE",
            (r,n)->new String[]{r.getString(1),r.getString(2)},criteria.schoolOrgId(),criteria.termId());
        if(batchRows.size()!=1) throw failure(503,"PRIVACY_RELEASE_UNAVAILABLE");
        String batchId=batchRows.getFirst()[0],datasetId=batchRows.getFirst()[1];
        var dataset=jdbc.query("SELECT data_json FROM student_snapshot_sets WHERE id=? AND school_org_id=? AND term_id=?",
            (r,n)->JSON.readValue(r.getString(1),StudentProfileSnapshotPort.SnapshotSet.class),datasetId,criteria.schoolOrgId(),criteria.termId())
            .stream().findFirst().orElseThrow(()->failure(503,"PRIVACY_RELEASE_UNAVAILABLE"));
        if(!dataset.taxonomyVersion().equals(criteria.taxonomyVersion()) || !dataset.academicCatalogVersion().equals(criteria.academicCatalogVersion()))
            throw failure(409,"PRIVACY_RELEASE_UNAVAILABLE");
        var identity=new ProtectedAggregatePolicy.ReleaseIdentity(enterpriseOrg,criteria.schoolOrgId(),criteria.termId(),datasetId,
            criteria.criteriaHash(),dataset.profileSnapshotVersion(),StrictClassifier.RULE_VERSION,criteria.taxonomyVersion());
        String fingerprint=ProtectedAggregatePolicy.fingerprint(identity);
        var existing=jdbc.query("SELECT fingerprint,dto_json FROM matching_privacy_releases WHERE enterprise_org_id=? AND school_org_id=? AND term_id=? AND demo_batch_id=? FOR UPDATE",
            (r,n)->new Object[]{r.getString(1),JSON.readValue(r.getString(2),ProtectedAggregatePolicy.ProtectedAggregate.class)},enterpriseOrg,criteria.schoolOrgId(),criteria.termId(),batchId);
        if(!existing.isEmpty()) {
            if(!fingerprint.equals(existing.getFirst()[0])) throw failure(409,"PRIVACY_RELEASE_LOCKED");
            var release=(ProtectedAggregatePolicy.ProtectedAggregate)existing.getFirst()[1];
            audit.write(actor(auth),"MATCH_AGGREGATE_VIEWED","match_run",release.matchRunId(),"SUCCESS");
            audit.matchingRunCompleted(actor(auth),criteria.demandId(),release.matchRunId(),enterpriseOrg,Map.of(
                "demandId",criteria.demandId(),"runVersion",1,"status","COMPLETED","ruleVersion",StrictClassifier.RULE_VERSION));
            return new Evaluated("ENTERPRISE",null,release);
        }

        Map<String,String> aliases=new HashMap<>();
        jdbc.query("SELECT a.alias,s.skill_code FROM skill_aliases a JOIN skills s ON s.id=a.skill_id WHERE s.status='ACTIVE' FOR SHARE",r->{aliases.put(r.getString(1),r.getString(2));});
        var profiles=jdbc.query("SELECT data_json FROM student_profile_snapshots WHERE dataset_id=? ORDER BY user_id",
            (r,n)->JSON.readValue(r.getString(1),StudentProfileSnapshotPort.ProfileSnapshot.class),datasetId);
        var results=profiles.stream().map(profile->StrictClassifier.evaluate(profile,criteria.required(),criteria.preferred(),aliases)).toList();
        long eligible=results.stream().filter(result->result.overallStatus().equals("MATCH")).count();

        String runId=UUID.randomUUID().toString();Instant now=Instant.now();
        var run=new MatchRunRef(runId,criteria.demandId(),criteria.demandVersion(),criteria.criteriaVersion(),criteria.criteriaHash(),datasetId,
            dataset.profileSnapshotVersion(),StrictClassifier.RULE_VERSION,criteria.taxonomyVersion(),StrictClassifier.SCORING_VERSION,"COMPLETED",now,Instant.now(),null);
        var stored=new StoredRun(run,criteria.schoolOrgId(),enterpriseOrg,criteria.termId());
        jdbc.update("INSERT INTO match_runs(id,demand_id,school_org_id,enterprise_org_id,term_id,dataset_id,data_json) VALUES(?,?,?,?,?,?,?)",
            runId,run.demandId(),stored.schoolOrgId(),stored.enterpriseOrgId(),stored.termId(),datasetId,JSON.writeValueAsString(stored));
        for(var result:results) jdbc.update("INSERT INTO match_results VALUES(?,?,?,?,?)",runId,result.studentUserId(),result.overallStatus(),result.fitScore(),JSON.writeValueAsString(result));

        var release=new ProtectedAggregatePolicy.ProtectedAggregate(UUID.randomUUID().toString(),runId,ProtectedAggregatePolicy.eligibleBand(eligible));
        jdbc.update("INSERT INTO matching_privacy_releases(enterprise_org_id,school_org_id,term_id,demo_batch_id,fingerprint,release_id,match_run_id,dto_json,created_at) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            enterpriseOrg,criteria.schoolOrgId(),criteria.termId(),batchId,fingerprint,release.releaseId(),runId,JSON.writeValueAsString(release));
        audit.workflow(actor(auth),"PRIVACY_RELEASE_CREATED",release.releaseId(),enterpriseOrg,Map.of(
            "enterpriseOrgId",enterpriseOrg,"schoolOrgId",criteria.schoolOrgId(),"termId",criteria.termId(),
            "demoBatchId",batchId,"eligibleBand",release.eligibleBand()));
        audit.matchingRunCompleted(actor(auth),criteria.demandId(),runId,enterpriseOrg,Map.of(
            "demandId",criteria.demandId(),"runVersion",1,"status","COMPLETED","ruleVersion",StrictClassifier.RULE_VERSION));
        return new Evaluated("ENTERPRISE",null,release);
    }
    /** Controller gọi sau khi giao dịch evaluate thất bại đã rollback để audit không bị rollback theo. */
    public void auditPrivacyReleaseBlocked(Authentication auth,String demandId) {
        var scope=jdbc.query("SELECT enterprise_org_id,school_org_id,term_id FROM demands WHERE id=?",
            (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)},uuid(demandId)).stream().findFirst()
            .orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        audit.writeScoped(actor(auth),"PRIVACY_RELEASE_BLOCKED","privacy_release",null,"DENIED",scope[0],"PRIVACY_RELEASE_LOCKED");
    }
    @Transactional public Details details(Authentication auth,String id,String status,int page,int size) {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM memberships m JOIN users u ON u.id=m.user_id JOIN organizations o ON o.id=m.organization_id JOIN role_permissions p ON p.role=m.role WHERE m.user_id=? AND m.role='SCHOOL_STAFF' AND m.active=true AND u.status='ACTIVE' AND u.security_version=? AND o.status='VERIFIED' AND p.permission_code='matching:school:detail_read'",Integer.class,actor(auth),((vn.utbridora.auth.security.AppPrincipal)auth.getPrincipal()).securityVersion())==0) throw failure(403,"ACCESS_DENIED");
        page(page,size);if(!Set.of("MATCH","NOT_MATCH","MISSING_DATA").contains(status)) throw failure(400,"SCHEMA_VALIDATION");
        var stored=read(auth,id,"matching:school:detail_read");
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM match_results WHERE run_id=? AND overall_status=?",Long.class,id,status);
        // DEV8 đã sửa từ ORDER BY fit_score cho mọi nhóm thành chỉ sắp điểm nhóm MATCH;
        // nhóm NOT_MATCH/MISSING_DATA chỉ xếp studentUserId để không ngụ ý có xếp hạng, phục vụ R11.
        var rows=jdbc.query("SELECT data_json FROM match_results WHERE run_id=? AND overall_status=? ORDER BY CASE WHEN overall_status='MATCH' THEN fit_score ELSE NULL END DESC,student_user_id ASC LIMIT ? OFFSET ?",(r,n)->JSON.readValue(r.getString(1),MatchResult.class),id,status,size,page*size);
        audit.workflow(actor(auth),"MATCH_DETAILS_VIEWED",id,stored.schoolOrgId(),Map.of("schoolOrgId",stored.schoolOrgId(),"termId",stored.termId(),"page",page,"size",size));
        return new Details(stored.run(),new Page<>(rows,page,size,count,(int)((count+size-1)/size)));
    }
    @Transactional public ProtectedAggregatePolicy.ProtectedAggregate protectedRelease(Authentication auth,String matchRunId) {
        uuid(matchRunId);
        var rows=jdbc.query("SELECT enterprise_org_id,dto_json FROM matching_privacy_releases WHERE match_run_id=?",
            (r,n)->new Object[]{r.getString(1),JSON.readValue(r.getString(2),ProtectedAggregatePolicy.ProtectedAggregate.class)},matchRunId);
        if(rows.size()!=1) throw failure(404,"RESOURCE_NOT_FOUND");
        String enterpriseOrg=(String)rows.getFirst()[0];
        if(!authorization.hasOrganization(auth,enterpriseOrg,Role.ENTERPRISE,"matching:enterprise:aggregate_read")) throw failure(404,"RESOURCE_NOT_FOUND");
        audit.write(actor(auth),"MATCH_AGGREGATE_VIEWED","match_run",matchRunId,"SUCCESS");
        return (ProtectedAggregatePolicy.ProtectedAggregate)rows.getFirst()[1];
    }
    @Transactional public String protectedReleaseCsv(Authentication auth,String matchRunId) {
        var release=protectedRelease(auth,matchRunId);
        audit.write(actor(auth),"MATCH_EXPORT_ATTEMPTED","match_run",matchRunId,"SUCCESS");
        return "releaseId,matchRunId,eligibleBand\r\n"+release.releaseId()+","+release.matchRunId()+","+release.eligibleBand()+"\r\n";
    }
    @Transactional public StoredRun read(Authentication auth,String id,String permission) {
        var run=jdbc.query("SELECT data_json FROM match_runs WHERE id=?",(r,n)->JSON.readValue(r.getString(1),StoredRun.class),uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        access.require(auth,run.schoolOrgId(),run.termId(),permission);return run;
    }
}
