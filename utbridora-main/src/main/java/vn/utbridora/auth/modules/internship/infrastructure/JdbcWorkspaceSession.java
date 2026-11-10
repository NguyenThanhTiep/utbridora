package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.Timestamp;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts.Session;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.integration.CaseMembershipPort;
import static vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind.*;

@Repository
public class JdbcWorkspaceSession implements Session {
    private final JdbcTemplate jdbc;private final AuditService audit;private final CaseMembershipPort cases;
    public JdbcWorkspaceSession(JdbcTemplate jdbc,AuditService audit,CaseMembershipPort cases) {this.jdbc=jdbc;this.audit=audit;this.cases=cases;}
    @Override public Cohort cohort(UUID id) {
        return jdbc.query("SELECT * FROM internship_cohorts WHERE id=? FOR UPDATE",(r,n)->new Cohort(UUID.fromString(r.getString("id")),
            new WorkspaceScope(UUID.fromString(r.getString("enterprise_org_id")),UUID.fromString(r.getString("school_org_id")),UUID.fromString(r.getString("term_id"))),
            r.getString("code"),r.getString("name"),r.getLong("version")),id.toString()).stream().findFirst().orElse(null);
    }
    @Override public Cohort cohortByKey(Cohort.Key key) {
        var ids=jdbc.queryForList("SELECT id FROM internship_cohorts WHERE enterprise_org_id=? AND school_org_id=? AND term_id=? AND code=?",String.class,
            key.scope().enterpriseOrgId().toString(),key.scope().schoolOrgId().toString(),key.scope().termId().toString(),key.code());
        return ids.isEmpty()?null:cohort(UUID.fromString(ids.getFirst()));
    }
    @Override public InternshipGroup group(UUID id) {
        return jdbc.query("SELECT * FROM internship_groups WHERE id=? FOR UPDATE",(r,n)->new InternshipGroup(id,UUID.fromString(r.getString("cohort_id")),r.getString("code"),
            jdbc.queryForList("SELECT case_id FROM internship_cohort_cases WHERE group_id=? ORDER BY case_id",String.class,id.toString()).stream().map(UUID::fromString).toList(),
            List.of(),r.getLong("version")),id.toString()).stream().findFirst().orElse(null);
    }
    @Override public InternshipTask task(UUID id) {
        return jdbc.query("SELECT * FROM internship_tasks WHERE id=?",(r,n)->new InternshipTask(new TaskDto(id,UUID.fromString(r.getString("group_id")),
            r.getString("title"),r.getString("description"),r.getObject("due_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),TaskPriority.valueOf(r.getString("priority")),r.getBoolean("late_allowed"),r.getLong("version"),
            jdbc.query("SELECT * FROM internship_task_assignments WHERE task_id=? ORDER BY id",(a,i)->new AssignmentDto(UUID.fromString(a.getString("id")),
                UUID.fromString(a.getString("case_id")),UUID.fromString(a.getString("student_user_id")),AssignmentState.valueOf(a.getString("state")),a.getLong("version"),
                a.getString("current_submission_id")==null?null:UUID.fromString(a.getString("current_submission_id"))),id.toString()))),id.toString()).stream().findFirst().orElse(null);
    }
    @Override public UUID currentGroup(UUID cohort,UUID caseId) {
        var values=jdbc.queryForList("SELECT group_id FROM internship_cohort_cases WHERE cohort_id=? AND case_id=? FOR UPDATE",String.class,cohort.toString(),caseId.toString());
        return values.isEmpty() || values.getFirst()==null?null:UUID.fromString(values.getFirst());
    }
    @Override public void insertCohort(Cohort c) {
        try {jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,?,?)",c.id().toString(),
            c.scope().enterpriseOrgId().toString(),c.scope().schoolOrgId().toString(),c.scope().termId().toString(),c.code(),c.name(),c.version());}
        catch(DuplicateKeyException duplicate) {throw new WorkspaceRuleException(DUPLICATE_COHORT);}
        audit.cohortCreated(actor(),c.id().toString(),c.scope().enterpriseOrgId().toString(),c.scope().schoolOrgId().toString(),c.scope().termId().toString(),c.version());
    }
    @Override public void addMember(InternshipGroup group,UUID caseId,long expectedCase,long expectedGroup) {
        version(group,expectedGroup);
        if(jdbc.update("UPDATE internship_cohort_cases SET group_id=? WHERE cohort_id=? AND case_id=? AND group_id IS NULL",group.id().toString(),group.cohortId().toString(),caseId.toString())!=1)
            throw new WorkspaceRuleException(GROUP_MEMBERSHIP_CONFLICT);
        changed(group,caseId,null,cases.advanceMembershipVersion(caseId,expectedCase),"MEMBERSHIP_ADDED");
    }
    @Override public void moveMember(InternshipGroup.Transfer transfer,UUID caseId,long expectedCase,long expectedSource,long expectedTarget,String reason) {
        version(transfer.source(),expectedSource);version(transfer.target(),expectedTarget);
        if(jdbc.update("UPDATE internship_cohort_cases SET group_id=? WHERE cohort_id=? AND case_id=? AND group_id=?",transfer.target().id().toString(),transfer.target().cohortId().toString(),
            caseId.toString(),transfer.source().id().toString())!=1) throw new WorkspaceRuleException(GROUP_MEMBERSHIP_CONFLICT);
        changed(transfer.target(),caseId,transfer.source().id(),cases.advanceMembershipVersion(caseId,expectedCase),reason);
    }
    private void version(InternshipGroup group,long expected) {
        if(jdbc.update("UPDATE internship_groups SET version=? WHERE id=? AND version=?",group.version(),group.id().toString(),expected)!=1) throw new WorkspaceRuleException(VERSION_CONFLICT);
    }
    private void changed(InternshipGroup group,UUID caseId,UUID from,long version,String reason) {
        jdbc.update("INSERT INTO internship_group_history(id,cohort_id,case_id,from_group_id,to_group_id,reason_code,changed_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            UUID.randomUUID().toString(),group.cohortId().toString(),caseId.toString(),from==null?null:from.toString(),group.id().toString(),reason);
        audit.groupMembershipChanged(actor(),caseId.toString(),cohort(group.cohortId()).scope().schoolOrgId().toString(),group.cohortId().toString(),from==null?null:from.toString(),group.id().toString(),version,reason);
    }
    @Override public void insertTask(InternshipTask task) {
        var t=task.value();
        jdbc.update("INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,?,?,?,?,?,?)",t.taskId().toString(),t.groupId().toString(),
            t.title(),t.description(),java.time.LocalDateTime.ofInstant(t.dueAt(),java.time.ZoneOffset.UTC),t.priority().name(),t.lateAllowed(),t.version());
        Cohort cohort=cohort(group(t.groupId()).cohortId());
        for(var a:t.assignments()) {
            jdbc.update("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,?,?)",a.assignmentId().toString(),t.taskId().toString(),
                a.caseId().toString(),a.studentUserId().toString(),a.state().name(),a.version());
            audit.taskAssigned(actor(),a.assignmentId().toString(),cohort.scope().schoolOrgId().toString(),t.taskId().toString(),a.caseId().toString(),t.groupId().toString(),a.version());
        }
    }
    private static String actor() {return WorkspaceAuthority.actor(SecurityContextHolder.getContext().getAuthentication());}
}
