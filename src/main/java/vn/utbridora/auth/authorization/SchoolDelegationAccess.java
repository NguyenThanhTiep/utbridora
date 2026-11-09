package vn.utbridora.auth.authorization;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;
import vn.utbridora.auth.operations.OperationFailure;

/** T1-owned read gate. Membership is a ceiling, never a substitute for a current SD chain. */
@Service
public class SchoolDelegationAccess {
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy access;
    public SchoolDelegationAccess(JdbcTemplate jdbc,AuthorizationPolicy access) {this.jdbc=jdbc;this.access=access;}
    private record Grant(String id,String user,String school,String term,String parent,String grantor) {}
    public void require(Authentication actor,String school,String term,String permission) {
        if(!access.hasOrganization(actor,school,Role.SCHOOL_STAFF,permission)) throw missing();
        String user=((AppPrincipal)actor.getPrincipal()).userId();
        List<String> roots=jdbc.queryForList("SELECT id FROM school_delegations WHERE user_id=? AND school_org_id=? AND (? IS NULL OR term_id=?) AND active=true AND valid_from<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<valid_until",String.class,user,school,term,term);
        if(roots.stream().noneMatch(id->current(id,school,term,permission,new HashSet<>(),0))) throw missing();
    }
    private boolean current(String id,String school,String term,String permission,Set<String> seen,int depth) {
        if(depth>=32 || !seen.add(id)) return false;
        var rows=jdbc.query("SELECT d.id,d.user_id,d.school_org_id,d.term_id,d.parent_id,d.granted_by FROM school_delegations d JOIN terms t ON t.id=d.term_id AND t.school_org_id=d.school_org_id JOIN users u ON u.id=d.user_id JOIN users g ON g.id=d.granted_by WHERE d.id=? AND d.active=true AND d.valid_from<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<d.valid_until AND u.status='ACTIVE' AND g.status='ACTIVE' AND EXISTS(SELECT 1 FROM memberships m WHERE m.user_id=d.user_id AND m.organization_id=d.school_org_id AND m.role='SCHOOL_STAFF' AND m.active=true) AND EXISTS(SELECT 1 FROM school_delegation_permissions p WHERE p.assignment_id=d.id AND p.permission_code=?)"+lockSuffix(),
            (r,n)->new Grant(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),id,permission);
        if(rows.size()!=1) return false;Grant grant=rows.getFirst();
        if(!school.equals(grant.school()) || term!=null && !term.equals(grant.term())) return false;
        if(grant.parent()==null) return jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=? AND active=true AND ((role='ADMIN' AND organization_id IS NULL) OR (role='SCHOOL_STAFF' AND organization_id=?))",Integer.class,grant.grantor(),school)>0;
        var parent=jdbc.queryForList("SELECT user_id FROM school_delegations WHERE id=? AND valid_from<=(SELECT valid_from FROM school_delegations WHERE id=?) AND valid_until>=(SELECT valid_until FROM school_delegations WHERE id=?)"+lockSuffix(),String.class,grant.parent(),id,id);
        return parent.size()==1 && grant.grantor().equals(parent.getFirst()) && current(grant.parent(),school,grant.term(),permission,seen,depth+1);
    }
    /** Revalidate the EXACT delegation referenced by a downstream confirmation, including its ancestry. */
    public boolean currentAssignment(String id,String subject,String school,String term,String permission) {
        var auth=access.currentSubject(subject).orElse(null);
        return access.hasOrganization(auth,school,Role.SCHOOL_STAFF,permission)
            && jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations WHERE id=? AND user_id=?",Integer.class,id,subject)==1
            && current(id,school,term,permission,new HashSet<>(),0);
    }
    public Set<String> profileFields(Authentication actor,String school,String term) {
        require(actor,school,term,"student_profile:school:read");String user=((AppPrincipal)actor.getPrincipal()).userId();
        var result=new HashSet<String>();
        for(String id:jdbc.queryForList("SELECT id FROM school_delegations WHERE user_id=? AND school_org_id=? AND term_id=?",String.class,user,school,term)) {
            if(currentAssignment(id,user,school,term,"student_profile:school:read")) {
                String value=jdbc.queryForObject("SELECT profile_read_fields_json FROM school_delegations WHERE id=? FOR SHARE",String.class,id);
                result.addAll(List.of(vn.utbridora.auth.operations.WorkflowValues.JSON.readValue(value,String[].class)));
            }
        }
        return Set.copyOf(result);
    }
    private static String lockSuffix() {
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()?" FOR UPDATE":"";
    }
    private static OperationFailure missing() {return new OperationFailure(404,"RESOURCE_NOT_FOUND","Không tìm thấy tài nguyên trong phạm vi được ủy quyền hiện hành.",null);}
}
