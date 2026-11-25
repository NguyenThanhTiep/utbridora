package vn.utbridora.auth.student;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.student.dto.StudentRosterDto;

/** Roster-specific projection. A boolean profile gate cannot express a term or field mask. */
@Component
public class SchoolRosterVisibilityAdapter {
    private final JdbcTemplate jdbc;
    private final SchoolDelegationAccess delegations;
    public SchoolRosterVisibilityAdapter(JdbcTemplate jdbc,SchoolDelegationAccess delegations) {
        this.jdbc=jdbc;this.delegations=delegations;
    }
    public List<StudentRosterDto> read(String school) {
        var actor=SecurityContextHolder.getContext().getAuthentication();
        var result=new ArrayList<StudentRosterDto>();
        var seen=new HashSet<String>();
        for(var scope:delegations.currentScopes(actor,"student_profile:school:read")) {
            if(!school.equals(scope.schoolOrgId()) || !seen.add(scope.termId())) continue;
            var fields=delegations.profileFields(actor,school,scope.termId());
            if(Collections.disjoint(fields,Set.of("studentCode","displayName","email","majorCode"))) continue;
            result.addAll(jdbc.query("SELECT r.*,t.term_code AS authorized_term_code FROM student_rosters r "
                +"JOIN student_term_eligibilities e ON e.student_roster_id=r.id AND e.user_id=r.user_id AND e.school_org_id=r.school_org_id "
                +"JOIN terms t ON t.id=e.term_id AND t.school_org_id=e.school_org_id AND t.status='ACTIVE' "
                +"JOIN users u ON u.id=r.user_id AND u.status='ACTIVE' "
                +"WHERE r.school_org_id=? AND e.term_id=? AND EXISTS(SELECT 1 FROM memberships m WHERE m.user_id=r.user_id AND m.organization_id=r.school_org_id AND m.role='STUDENT' AND m.active=true) "
                +"ORDER BY r.id FOR SHARE",(r,n)->new StudentRosterDto(r.getString("id"),school,
                    fields.contains("studentCode")?r.getString("student_code"):null,r.getString("user_id"),
                    fields.contains("displayName")?r.getString("full_name"):null,
                    fields.contains("email")?r.getString("email"):null,
                    fields.contains("majorCode")?r.getString("major_code"):null,
                    r.getString("authorized_term_code"),vn.utbridora.auth.operations.WorkflowValues.instant(r,"created_at")),school,scope.termId()));
        }
        return List.copyOf(result);
    }
}
