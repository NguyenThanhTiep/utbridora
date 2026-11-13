package vn.utbridora.auth.modules.student.application;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.user.Role;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;

/** Self and explicitly delegated/shared current attachments. T1 still owns storage and CLEAN checks. */
@Component
public class StudentProfileFilePolicy implements FileResourcePolicy {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    private final ObjectProvider<ProfileAccessPort> access;
    public StudentProfileFilePolicy(JdbcTemplate jdbc,AuthorizationPolicy core,ObjectProvider<ProfileAccessPort> access) {this.jdbc=jdbc;this.core=core;this.access=access;}
    @Override public ResourceType resourceType() {return ResourceType.STUDENT_PROFILE;}
    @Override public Grant check(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant now) {
        if(purpose!=FilePurpose.CV && purpose!=FilePurpose.EVIDENCE) return Grant.deny();
        if(!actor.actorId().equals(resource.resourceId())) return action==Action.UPLOAD?Grant.deny():shared(actor,resource,purpose,null);
        var rows=jdbc.queryForList("SELECT profile_version FROM student_profiles WHERE user_id=? FOR SHARE",Long.class,resource.resourceId());
        if(rows.isEmpty()) return Grant.deny();
        var schools=jdbc.queryForList("SELECT e.school_org_id FROM student_term_eligibilities e JOIN student_rosters r ON r.id=e.student_roster_id AND r.user_id=e.user_id AND r.school_org_id=e.school_org_id JOIN terms t ON t.id=e.term_id AND t.school_org_id=e.school_org_id WHERE e.user_id=?"+
            (action==Action.UPLOAD?" AND t.status='ACTIVE'":"")+" ORDER BY e.school_org_id,e.term_id FOR SHARE",String.class,resource.resourceId());
        String permission=action==Action.UPLOAD?"student_profile:self:write":"student_profile:self:read";
        boolean linked=schools.stream().anyMatch(school->core.holdOrganization(actor.authentication(),school,Role.STUDENT,permission));
        // The profile belongs to the student across terms; no arbitrary first school becomes its file scope.
        return linked?Grant.owner(rows.getFirst(),null,false):Grant.deny();
    }
    @Override public boolean mayReadFile(ActorContext actor,String fileId,String uploadedBy,ResourceRef resource,FilePurpose purpose) {
        if(actor.actorId().equals(resource.resourceId())) return actor.actorId().equals(uploadedBy);
        return shared(actor,resource,purpose,fileId).authorization().allowed();
    }
    private Grant shared(ActorContext actor,ResourceRef resource,FilePurpose purpose,String fileId) {
        String field=purpose==FilePurpose.CV?"cv":"evidence";
        var profiles=jdbc.query("SELECT id,profile_version,cv_file_id FROM student_profiles WHERE user_id=? FOR SHARE",
            (r,n)->new Object[]{r.getString(1),r.getLong(2),r.getString(3)},resource.resourceId());
        if(profiles.size()!=1) return Grant.deny();
        var profile=profiles.getFirst();
        if(fileId!=null) {
            if(purpose==FilePurpose.CV && !fileId.equals(profile[2])) return Grant.deny();
            if(purpose==FilePurpose.EVIDENCE && jdbc.queryForList("SELECT pf.file_id FROM student_project_files pf JOIN student_projects p ON p.id=pf.project_id WHERE p.profile_id=? AND pf.file_id=? FOR SHARE",String.class,profile[0],fileId).isEmpty()
                && jdbc.queryForList("SELECT file_id FROM student_certificates WHERE profile_id=? AND file_id=? FOR SHARE",String.class,profile[0],fileId).isEmpty()) return Grant.deny();
        }
        var candidates=jdbc.query("SELECT s.id,JSON_UNQUOTE(JSON_EXTRACT(s.data_json,'$.termId')) AS term_id FROM profile_shares s JOIN proposal_items i ON i.id=s.proposal_item_id AND i.share_id=s.id WHERE i.student_user_id=? ORDER BY s.id FOR SHARE",
            (r,n)->new String[]{r.getString(1),r.getString(2)},resource.resourceId());
        // School review uses its current field-specific delegation, independently from enterprise consent.
        for(String term:jdbc.queryForList("SELECT term_id FROM student_term_eligibilities WHERE user_id=? ORDER BY term_id FOR SHARE",String.class,resource.resourceId()))
            candidates.add(new String[]{null,term});
        for(var candidate:candidates) {
            try {
                var decision=access.getObject().decide(actor,new ProfileAccessPort.Query(resource.resourceId(),candidate[1],candidate[0],List.of(field),candidate[0]==null?"SCHOOL_REVIEW":"INTERNSHIP_SELECTION"));
                if(decision.allowed() && decision.allowedFields().contains(field))
                    return new Grant(new FileAuthorization(true,"SCOPED_PROFILE_FILE",(Long)profile[1],decision.consentVersion(),decision.shareVersion(),decision.validUntil()),null,true);
            } catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status!=403 && denied.status!=404) throw denied;}
        }
        return Grant.deny();
    }
}
