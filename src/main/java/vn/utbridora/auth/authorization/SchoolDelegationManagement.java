package vn.utbridora.auth.authorization;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Administrative staff metadata only. Does not grant access to student profiles or business data. */
@Service
@Transactional(readOnly=true)
public class SchoolDelegationManagement {
    public record Scope(String schoolOrgId,String schoolName,String termId,String termCode,String termStatus) {}
    public record Options(Scope scope,List<SchoolDelegationService.Delegation> authorities,
                          List<String> permissionCodes,List<String> profileReadFields) {}
    public record Candidate(String userId,String displayName,String email) {}
    public record Detail(SchoolDelegationService.Delegation delegation,String parentId,
                         String displayName,String email,String grantorName,List<String> effectivePermissionCodes,
                         boolean canRevoke) {}
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    private final SchoolDelegationAccess access;
    private final SchoolDelegationService service;
    public SchoolDelegationManagement(JdbcTemplate jdbc,AuthorizationPolicy core,SchoolDelegationAccess access,SchoolDelegationService service) {
        this.jdbc=jdbc;this.core=core;this.access=access;this.service=service;
    }
    private void admin(Authentication auth) {
        if(!core.hasGlobal(auth,"membership:admin:write")) throw failure(403,"ACCESS_DENIED");
    }
    private Scope scope(Authentication auth,boolean admin,String term) {
        term=uuid(term);
        if(admin) admin(auth);
        var scopes=jdbc.query("SELECT t.*,o.name FROM terms t JOIN organizations o ON o.id=t.school_org_id WHERE t.id=? AND o.type='UNIVERSITY' AND o.status='VERIFIED'",
            (r,n)->new Scope(r.getString("school_org_id"),r.getString("name"),r.getString("id"),r.getString("term_code"),r.getString("status")),term);
        if(scopes.isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
        var result=scopes.getFirst();
        if(!admin) access.require(auth,result.schoolOrgId(),term,"school:delegation:manage");
        return result;
    }
    public Page<Scope> scopes(Authentication auth,boolean admin,int page,int size) {
        page(page,size);String user=actor(auth);
        if(admin) admin(auth);
        // Restrict candidates in SQL before checking the current authority chain.
        String where=admin?"":" AND EXISTS(SELECT 1 FROM school_delegations d WHERE d.term_id=t.id AND d.user_id=? AND d.active=true AND d.valid_from<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<d.valid_until)";
        Object[] args=admin?new Object[]{}:new Object[]{user};
        var candidates=jdbc.query("SELECT t.*,o.name FROM terms t JOIN organizations o ON o.id=t.school_org_id WHERE o.type='UNIVERSITY' AND o.status='VERIFIED'"+where+" ORDER BY o.name,t.term_code,t.id",
            (r,n)->new Scope(r.getString("school_org_id"),r.getString("name"),r.getString("id"),r.getString("term_code"),r.getString("status")),args);
        var visible=admin?candidates:candidates.stream().filter(s->canManage(auth,s.schoolOrgId(),s.termId())).toList();
        int from=Math.min(page*size,visible.size());
        return new Page<>(visible.subList(from,Math.min(from+size,visible.size())),page,size,visible.size(),(visible.size()+size-1)/size);
    }
    private boolean canManage(Authentication auth,String school,String term) {
        try {access.require(auth,school,term,"school:delegation:manage");return true;}
        catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status!=404) throw denied;return false;}
    }
    public Options options(Authentication auth,boolean admin,String term) {
        var s=scope(auth,admin,term);
        var ceiling=jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role='SCHOOL_STAFF' ORDER BY permission_code",String.class);
        var authorities=admin?List.<SchoolDelegationService.Delegation>of():jdbc.queryForList(
            "SELECT id FROM school_delegations WHERE user_id=? AND school_org_id=? AND term_id=? ORDER BY id",String.class,actor(auth),s.schoolOrgId(),s.termId()).stream()
            .filter(id->access.currentAssignment(id,actor(auth),s.schoolOrgId(),s.termId(),"school:delegation:manage"))
            .map(service::read).toList();
        return new Options(s,authorities,ceiling,SchoolDelegationService.FIELDS.stream().sorted().toList());
    }
    public Page<Candidate> candidates(Authentication auth,boolean admin,String term,String search,int page,int size) {
        page(page,size);var s=scope(auth,admin,term);
        if(search!=null && search.length()>150) throw failure(400,"SCHEMA_VALIDATION");
        String q="%"+(search==null?"":search.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_"))+"%";
        String where=" FROM users u WHERE u.status='ACTIVE' AND u.id<>? AND EXISTS(SELECT 1 FROM memberships m WHERE m.user_id=u.id AND m.organization_id=? AND m.role='SCHOOL_STAFF' AND m.active=true) AND (LOWER(u.email) LIKE ? ESCAPE '!' OR LOWER(u.display_name) LIKE ? ESCAPE '!')";
        Object[] args={actor(auth),s.schoolOrgId(),q,q};
        long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,args);
        var parameters=new ArrayList<Object>(Arrays.asList(args));parameters.add(size);parameters.add(page*size);
        var rows=jdbc.query("SELECT u.id,u.display_name,u.email"+where+" ORDER BY u.display_name,u.id LIMIT ? OFFSET ?",(r,n)->new Candidate(r.getString(1),r.getString(2),r.getString(3)),parameters.toArray());
        return new Page<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    public Page<Detail> list(Authentication auth,boolean admin,String term,int page,int size) {
        page(page,size);var s=scope(auth,admin,term);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations WHERE school_org_id=? AND term_id=?",Long.class,s.schoolOrgId(),s.termId());
        var ids=jdbc.queryForList("SELECT id FROM school_delegations WHERE school_org_id=? AND term_id=? ORDER BY id LIMIT ? OFFSET ?",String.class,s.schoolOrgId(),s.termId(),size,page*size);
        return new Page<>(ids.stream().map(id->describe(auth,admin,id)).toList(),page,size,total,(int)((total+size-1)/size));
    }
    public Detail detail(Authentication auth,boolean admin,String id) {
        uuid(id);if(admin) admin(auth);
        var d=service.read(id);scope(auth,admin,d.termId());return describe(auth,admin,id);
    }
    private Detail describe(Authentication auth,boolean admin,String id) {
        var d=service.read(id);
        var names=jdbc.queryForObject("SELECT d.parent_id,u.display_name,u.email,g.display_name AS grantor_name FROM school_delegations d JOIN users u ON u.id=d.user_id JOIN users g ON g.id=d.granted_by WHERE d.id=?",
            (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4)},id);
        var effective=d.permissionCodes().stream().filter(p->access.currentAssignment(id,d.userId(),d.schoolOrgId(),d.termId(),p)).toList();
        boolean canRevoke=d.active() && !d.userId().equals(actor(auth)) && d.grantedBy().equals(actor(auth))
            && (admin?d.decisionRef().startsWith("DEMO-"):canManage(auth,d.schoolOrgId(),d.termId()));
        return new Detail(d,names[0],names[1],names[2],names[3],effective,canRevoke);
    }
}
