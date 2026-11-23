package vn.utbridora.auth.authorization;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import vn.utbridora.auth.security.AppPrincipal;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Current USER authority. Persisted stamps are references, never grants. */
@Service
public class AdvisoryAuthority {
    public static final String READ="matching:school:advisory_read", MANAGE="matching:school:advisory_manage";
    public record Grant(String permission,String id,long version) {}
    public record Ancestor(String id,long version,Instant until) {}
    public record Stamp(String subject,long securityVersion,String school,String term,List<Grant> grants,List<Ancestor> chain,Instant until) {}
    private final SchoolDelegationAccess scope; private final AuthorizationPolicy core; private final JdbcTemplate jdbc;
    public AdvisoryAuthority(SchoolDelegationAccess scope,AuthorizationPolicy core,JdbcTemplate jdbc) {this.scope=scope;this.core=core;this.jdbc=jdbc;}
    public void require(Authentication auth,String school,String term,boolean manage) {
        actor(auth);
        for(String p:permissions(manage)) {
            if(!core.holdOrganization(auth,school,vn.utbridora.auth.user.Role.SCHOOL_STAFF,p)) throw failure(404,"RESOURCE_NOT_FOUND");
            scope.require(auth,school,term,p);
        }
        if(!scope.profileFields(auth,school,term).containsAll(Set.of("skills","projects"))) throw failure(404,"RESOURCE_NOT_FOUND");
    }
    public void requireEither(Authentication auth,String school,String term) {
        try {require(auth,school,term,false);}
        catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status!=404) throw denied;require(auth,school,term,true);}
    }
    public Stamp capture(Authentication auth,String school,String term) {
        require(auth,school,term,true);var grants=new ArrayList<Grant>();
        for(String p:permissions(true)) {var g=scope.capture(auth,school,term,p);grants.add(new Grant(p,g.id(),g.version()));}
        // Capture every exact field-bearing root, not an arbitrary profile-read root.
        for(String id:jdbc.queryForList("SELECT id FROM school_delegations WHERE user_id=? AND school_org_id=? AND term_id=? ORDER BY id",String.class,actor(auth),school,term)) {
            if(scope.currentAssignment(id,actor(auth),school,term,"student_profile:school:read")) {
                var fields=List.of(JSON.readValue(jdbc.queryForObject("SELECT profile_read_fields_json FROM school_delegations WHERE id=? FOR SHARE",String.class,id),String[].class));
                if(fields.contains("skills") || fields.contains("projects")) grants.add(new Grant("student_profile:school:read",id,jdbc.queryForObject("SELECT version FROM school_delegations WHERE id=? FOR SHARE",Long.class,id)));
            }
        }
        var chain=new TreeMap<String,Ancestor>();
        for(var g:grants) {
            String id=g.id();var seen=new HashSet<String>();
            while(id!=null) {
                if(!seen.add(id) || seen.size()>32) throw failure(404,"RESOURCE_NOT_FOUND");
                var rows=jdbc.query("SELECT id,version,valid_until,parent_id FROM school_delegations WHERE id=? FOR SHARE",(r,n)->new Object[]{new Ancestor(r.getString(1),r.getLong(2),instant(r,"valid_until")),r.getString(4)},id);
                if(rows.size()!=1) throw failure(404,"RESOURCE_NOT_FOUND");
                var a=(Ancestor)rows.getFirst()[0];chain.put(a.id(),a);id=(String)rows.getFirst()[1];
            }
        }
        Instant until=chain.values().stream().map(Ancestor::until).min(Instant::compareTo).orElseThrow();
        return new Stamp(actor(auth),((AppPrincipal)auth.getPrincipal()).securityVersion(),school,term,List.copyOf(grants),List.copyOf(chain.values()),until);
    }
    public Optional<Authentication> current(Stamp stamp) {
        var auth=core.currentSubject(stamp.subject()).orElse(null);
        if(auth==null || ((AppPrincipal)auth.getPrincipal()).securityVersion()!=stamp.securityVersion() || !Instant.now().isBefore(stamp.until())) return Optional.empty();
        try {
            require(auth,stamp.school(),stamp.term(),true);
            for(var g:stamp.grants()) scope.requireStamp(g.id(),g.version(),stamp.subject(),stamp.school(),stamp.term(),g.permission());
            for(var a:stamp.chain()) {var versions=jdbc.queryForList("SELECT version FROM school_delegations WHERE id=? FOR SHARE",Long.class,a.id());if(versions.size()!=1 || versions.getFirst()!=a.version())return Optional.empty();}
            return Optional.of(auth);
        } catch(vn.utbridora.auth.operations.OperationFailure denied) {if(denied.status==404 || denied.status==401) return Optional.empty();throw denied;}
    }
    private static List<String> permissions(boolean manage) {
        return manage?List.of(MANAGE,"matching:run:execute","matching:school:detail_read","student_profile:school:read")
            :List.of(READ,"matching:school:detail_read","student_profile:school:read");
    }
}
