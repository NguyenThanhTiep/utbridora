package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

/** Current assignment and parent-chain checks. The request header only selects; it never grants authority. */
@Service
public class WorkspaceAuthority implements CoreWorkspaceAccess.BusinessAuthority {
    public record Scope(String schoolOrgId,String enterpriseOrgId,String termId,String groupId,String caseId) {}
    public record Assignment(String id,String kind,String userId,Scope scope,boolean active,Instant validFrom,
        Instant validUntil,long version,String grantedBy,String decisionRef) {}
    private record Grant(Assignment value,String parent,String delegation,boolean invitationAccepted) {}
    private final JdbcTemplate jdbc;private final AuthorizationPolicy core;private final SchoolDelegationAccess delegations;
    private final TermReferencePort terms;private final OrganizationSchoolVerificationPort verification;
    public WorkspaceAuthority(JdbcTemplate jdbc,AuthorizationPolicy core,SchoolDelegationAccess delegations,
        TermReferencePort terms,OrganizationSchoolVerificationPort verification) {
        this.jdbc=jdbc;this.core=core;this.delegations=delegations;this.terms=terms;this.verification=verification;
    }
    public List<Assignment> assignments(Authentication actor) {
        String user=actor(actor);
        if(!core.isSelf(actor,user)) throw new WorkspaceRuleException(UNAUTHENTICATED);
        return jdbc.queryForList("SELECT id FROM internship_context_assignments WHERE user_id=? ORDER BY id",String.class,user)
            .stream().map(this::load).filter(Objects::nonNull).filter(g->valid(g,new HashSet<>())).map(Grant::value).toList();
    }
    public Assignment selected(Authentication actor) {
        var attributes=RequestContextHolder.getRequestAttributes();
        String id=attributes instanceof ServletRequestAttributes web?web.getRequest().getHeader("X-Context-Assignment-Id"):null;
        if(id==null || !id.matches("[0-9a-fA-F-]{36}")) throw new WorkspaceRuleException(DENIED);
        Grant grant=load(id);
        if(!core.isSelf(actor,actor(actor)) || grant==null || !grant.value.userId.equals(actor(actor)) || !valid(grant,new HashSet<>())) throw new WorkspaceRuleException(DENIED);
        if(!core.holdOrganization(actor,grant.value.scope.enterpriseOrgId,Role.ENTERPRISE,"t3:workspace:read")) throw new WorkspaceRuleException(DENIED);
        return grant.value;
    }
    @Override public WorkspaceScope resolveCreationScope(Authentication actor,UUID term) {
        Assignment selected=selected(actor);
        if(!selected.kind.equals("HR") || !selected.scope.termId.equals(term.toString())) throw new WorkspaceRuleException(DENIED);
        var reference=terms.findById(term.toString()).orElseThrow(()->new WorkspaceRuleException(NOT_FOUND));
        if(!reference.schoolOrgId().equals(selected.scope.schoolOrgId) || reference.status()!=TermReferencePort.Status.ACTIVE) throw new WorkspaceRuleException(STATE_CONFLICT);
        return new WorkspaceScope(UUID.fromString(selected.scope.enterpriseOrgId),UUID.fromString(selected.scope.schoolOrgId),term);
    }
    @Override public void requireHr(Authentication actor,WorkspaceScope scope,UUID groupId) {
        Assignment selected=selected(actor);
        if(!selected.kind.equals("HR")) throw new WorkspaceRuleException(DENIED);
        if(!same(selected.scope,scope) || selected.scope.groupId!=null && !selected.scope.groupId.equals(String.valueOf(groupId))) throw new WorkspaceRuleException(SCOPE_MISMATCH);
        if(!core.hasOrganization(actor,scope.enterpriseOrgId().toString(),Role.ENTERPRISE,"t3:cohorts:manage")) throw new WorkspaceRuleException(DENIED);
    }
    @Override public void requireMentor(Authentication actor,WorkspaceScope scope,UUID groupId,UUID caseId) {
        Assignment selected=selected(actor);
        if(!selected.kind.equals("MENTOR")) throw new WorkspaceRuleException(DENIED);
        if(!same(selected.scope,scope) || !String.valueOf(groupId).equals(selected.scope.groupId)
            || selected.scope.caseId!=null && !selected.scope.caseId.equals(caseId.toString())
            || jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE group_id=? AND case_id=?",Integer.class,groupId.toString(),caseId.toString())!=1)
            throw new WorkspaceRuleException(SCOPE_MISMATCH);
    }
    @Override public boolean canReadTask(Authentication actor,WorkspaceScope scope,InternshipTask task,UUID caseId) {
        try {
            Assignment selected=selected(actor);
            return same(selected.scope,scope) && (selected.kind.equals("HR") || selected.kind.equals("MENTOR")
                && String.valueOf(task.value().groupId()).equals(selected.scope.groupId)
                && (selected.scope.caseId==null || selected.scope.caseId.equals(caseId.toString()))
                // A new group's mentor has no authority over an old task-group snapshot.
                && jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE case_id=? AND group_id=?",Integer.class,caseId.toString(),task.value().groupId().toString())==1);
        } catch(WorkspaceRuleException unavailable) {return false;}
    }
    public boolean canReadScope(Authentication actor,WorkspaceScope scope,UUID group) {
        try {
            Assignment a=selected(actor);
            return same(a.scope,scope) && core.hasOrganization(actor,scope.enterpriseOrgId().toString(),Role.ENTERPRISE,"t3:workspace:read")
                && (a.kind.equals("HR") || a.kind.equals("MENTOR") && (group==null || group.toString().equals(a.scope.groupId)));
        } catch(WorkspaceRuleException denied) {return false;}
    }
    private boolean valid(Grant grant,Set<String> seen) {
        Assignment a=grant.value;
        if(!seen.add(a.id) || seen.size()>4 || !a.active || a.validFrom.isAfter(Instant.now()) || !Instant.now().isBefore(a.validUntil)) return false;
        Authentication subject=core.currentSubject(a.userId).orElse(null);
        if(!core.holdOrganization(subject,a.scope.enterpriseOrgId,Role.ENTERPRISE,"t3:workspace:read")) return false;
        if(a.kind.equals("COORDINATOR")) {
            if(a.scope.groupId!=null || a.scope.caseId!=null) return false;
            if(grant.delegation==null || !delegations.currentAssignment(grant.delegation,a.grantedBy,a.scope.schoolOrgId,a.scope.termId,"t3:coordinator:confirm")) return false;
            Authentication grantor=core.currentSubject(a.grantedBy).orElse(null);
            if(!core.holdOrganization(grantor,a.scope.schoolOrgId,Role.SCHOOL_STAFF,"t3:coordinator:confirm")) return false;
            var context=OrganizationSchoolVerificationPort.ReadContext.fromAuthenticatedWorkflow(grantor,a.scope.termId,OrganizationSchoolVerificationPort.Purpose.CONFIRM_COORDINATOR);
            return verification.get(context,a.scope.enterpriseOrgId,a.scope.schoolOrgId) instanceof OrganizationSchoolVerificationPort.Success ok && ok.value().schoolApproved();
        }
        Grant parent=load(grant.parent);
        if(parent==null || !valid(parent,seen)) return false;
        Assignment p=parent.value;
        if(!a.grantedBy.equals(p.userId) || a.userId.equals(a.grantedBy) || !same(a.scope,p.scope)
            || a.validFrom.isBefore(p.validFrom) || a.validUntil.isAfter(p.validUntil)) return false;
        return a.kind.equals("HR")?p.kind.equals("COORDINATOR") && a.scope.groupId==null && a.scope.caseId==null
            :a.kind.equals("MENTOR") && p.kind.equals("HR") && grant.invitationAccepted && a.scope.groupId!=null
                && jdbc.queryForObject("SELECT COUNT(*) FROM internship_groups g JOIN internship_cohorts c ON c.id=g.cohort_id WHERE g.id=? AND c.enterprise_org_id=? AND c.school_org_id=? AND c.term_id=?",Integer.class,
                    a.scope.groupId,a.scope.enterpriseOrgId,a.scope.schoolOrgId,a.scope.termId)==1
                && (a.scope.caseId==null || jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE group_id=? AND case_id=?",Integer.class,a.scope.groupId,a.scope.caseId)==1);
    }
    private Grant load(String id) {
        if(id==null) return null;
        return jdbc.query("SELECT * FROM internship_context_assignments WHERE id=?"+lock(),(r,n)->new Grant(
            new Assignment(r.getString("id"),r.getString("kind"),r.getString("user_id"),
                new Scope(r.getString("school_org_id"),r.getString("enterprise_org_id"),r.getString("term_id"),r.getString("group_id"),r.getString("case_id")),
                r.getBoolean("active"),r.getObject("valid_from",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),r.getObject("valid_until",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),r.getLong("version"),r.getString("granted_by"),r.getString("decision_ref")),
            r.getString("parent_assignment_id"),r.getString("school_delegation_id"),r.getBoolean("invitation_accepted")),id).stream().findFirst().orElse(null);
    }
    private static String lock() {return org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()?"":" FOR SHARE";}
    public static String actor(Authentication actor) {
        if(actor==null || !actor.isAuthenticated() || !(actor.getPrincipal() instanceof AppPrincipal p)) throw new WorkspaceRuleException(UNAUTHENTICATED);
        return p.userId();
    }
    public static boolean same(Scope a,WorkspaceScope b) {return a.enterpriseOrgId.equals(b.enterpriseOrgId().toString()) && a.schoolOrgId.equals(b.schoolOrgId().toString()) && a.termId.equals(b.termId().toString());}
    private static boolean same(Scope a,Scope b) {return a.enterpriseOrgId.equals(b.enterpriseOrgId) && a.schoolOrgId.equals(b.schoolOrgId) && a.termId.equals(b.termId);}
}
