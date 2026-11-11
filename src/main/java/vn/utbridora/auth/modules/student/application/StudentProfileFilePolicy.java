package vn.utbridora.auth.modules.student.application;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.user.Role;

/** Self branch of CP-FILE1. Shared CV/evidence remains closed until the BC24 file adapter is implemented. */
@Component
public class StudentProfileFilePolicy implements FileResourcePolicy {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    public StudentProfileFilePolicy(JdbcTemplate jdbc,AuthorizationPolicy core) {this.jdbc=jdbc;this.core=core;}
    @Override public ResourceType resourceType() {return ResourceType.STUDENT_PROFILE;}
    @Override public Grant check(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant now) {
        if((purpose!=FilePurpose.CV && purpose!=FilePurpose.EVIDENCE) || !actor.actorId().equals(resource.resourceId())) return Grant.deny();
        var rows=jdbc.queryForList("SELECT profile_version FROM student_profiles WHERE user_id=? FOR SHARE",Long.class,resource.resourceId());
        if(rows.isEmpty()) return Grant.deny();
        var schools=jdbc.queryForList("SELECT e.school_org_id FROM student_term_eligibilities e JOIN student_rosters r ON r.id=e.student_roster_id AND r.user_id=e.user_id AND r.school_org_id=e.school_org_id JOIN terms t ON t.id=e.term_id AND t.school_org_id=e.school_org_id WHERE e.user_id=?"+
            (action==Action.UPLOAD?" AND t.status='ACTIVE'":"")+" ORDER BY e.school_org_id,e.term_id FOR SHARE",String.class,resource.resourceId());
        String permission=action==Action.UPLOAD?"student_profile:self:write":"student_profile:self:read";
        boolean linked=schools.stream().anyMatch(school->core.holdOrganization(actor.authentication(),school,Role.STUDENT,permission));
        // The profile belongs to the student across terms; no arbitrary first school becomes its file scope.
        return linked?Grant.owner(rows.getFirst(),null,false):Grant.deny();
    }
}
