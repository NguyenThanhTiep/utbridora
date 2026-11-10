package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.CaseChecks;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.modules.internship.integration.CaseMembershipPort;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

@Service
public class CaseRuntime implements CaseChecks,CaseMembershipPort {
    public record CaseRef(UUID caseId,UUID placementId,UUID studentUserId,UUID schoolOrgId,UUID enterpriseOrgId,UUID termId,String state,long version) {}
    private final JdbcTemplate jdbc;private final PlacementReadPort placements;
    public CaseRuntime(JdbcTemplate jdbc,PlacementReadPort placements) {this.jdbc=jdbc;this.placements=placements;}
    @Transactional(propagation=Propagation.MANDATORY)
    public CaseRef read(UUID id) {
        return jdbc.query("SELECT * FROM internship_cases WHERE id=?",(r,n)->new CaseRef(UUID.fromString(r.getString("id")),
            UUID.fromString(r.getString("placement_id")),UUID.fromString(r.getString("student_user_id")),UUID.fromString(r.getString("school_org_id")),
            UUID.fromString(r.getString("enterprise_org_id")),UUID.fromString(r.getString("term_id")),r.getString("state"),r.getLong("version")),id.toString())
            .stream().findFirst().orElseThrow(()->new WorkspaceRuleException(NOT_FOUND));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void requireMembership(Authentication actor,Cohort cohort,UUID caseId,long expectedVersion) {
        var c=read(caseId);var p=current(c,cohort);
        if(c.version()!=expectedVersion) throw new WorkspaceRuleException(VERSION_CONFLICT);
        if(!Set.of("WAITING_START","IN_PROGRESS").contains(c.state()) || !Set.of("APPROVED","ACTIVE").contains(p.state())) throw new WorkspaceRuleException(STATE_CONFLICT);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE cohort_id=? AND case_id=?",Integer.class,cohort.id().toString(),caseId.toString())!=1)
            throw new WorkspaceRuleException(SCOPE_MISMATCH);
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public UUID requireTaskParticipant(Authentication actor,Cohort cohort,InternshipGroup group,UUID caseId,Instant now) {
        var c=read(caseId);var p=current(c,cohort);
        if(!InternshipActivityPolicy.hasRequiredStateAndStartTime(c.state(),p.state(),p.effectiveAt(),now)) throw new WorkspaceRuleException(STATE_CONFLICT);
        if(p.approvedAt()==null || p.approvedAt().isAfter(now)) throw new WorkspaceRuleException(STATE_CONFLICT);
        return c.studentUserId();
    }
    private PlacementReadPort.PlacementView current(CaseRef c,Cohort cohort) {
        // Lock source first, then Case; a current T5 mutation cannot race the Task commit.
        var p=placements.readCurrentForGuard(c.placementId().toString()).orElseThrow(()->new WorkspaceRuleException(UNAVAILABLE));
        jdbc.queryForObject("SELECT version FROM internship_cases WHERE id=? FOR UPDATE",Long.class,c.caseId().toString());
        var fresh=read(c.caseId());
        if(fresh.version()!=c.version()) throw new WorkspaceRuleException(VERSION_CONFLICT);
        var scope=cohort.scope();
        if(!p.studentUserId().equals(c.studentUserId().toString()) || !p.schoolOrgId().equals(scope.schoolOrgId().toString())
            || !p.enterpriseOrgId().equals(scope.enterpriseOrgId().toString()) || !p.termId().equals(scope.termId().toString())
            || !c.schoolOrgId().equals(scope.schoolOrgId()) || !c.enterpriseOrgId().equals(scope.enterpriseOrgId()) || !c.termId().equals(scope.termId()))
            throw new WorkspaceRuleException(SCOPE_MISMATCH);
        return p;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public long advanceMembershipVersion(UUID id,long expected) {
        if(expected>=9_007_199_254_740_991L || jdbc.update("UPDATE internship_cases SET version=version+1 WHERE id=? AND version=?",id.toString(),expected)!=1)
            throw new WorkspaceRuleException(VERSION_CONFLICT);
        return expected+1;
    }
}
