package vn.utbridora.auth.authorization;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Minimal names/IDs only. T3 must validate its current authority before invoking this internal port. */
@Component
public class EnterpriseMemberReferenceAdapter implements EnterpriseMemberReferencePort {
    private final JdbcTemplate jdbc;
    public EnterpriseMemberReferenceAdapter(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override public Page<Member> candidates(String enterprise,String excludedUser,String search,int page,int size) {
        page(page,size);uuid(enterprise);uuid(excludedUser);
        String query=search==null?"":search.strip().toLowerCase(Locale.ROOT);
        if(query.length()>120) throw failure(400,"SCHEMA_VALIDATION");
        String where=" FROM users u WHERE u.status='ACTIVE' AND u.id<>? AND LOCATE(?,LOWER(u.display_name))>0 AND EXISTS(SELECT 1 FROM memberships m JOIN organizations o ON o.id=m.organization_id WHERE m.user_id=u.id AND m.organization_id=? AND m.role='ENTERPRISE' AND m.active=true AND o.type='ENTERPRISE' AND o.status='VERIFIED')";
        long total=jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,excludedUser,query,enterprise);
        var rows=jdbc.query("SELECT u.id,u.display_name"+where+" ORDER BY u.display_name,u.id LIMIT ? OFFSET ?",
            (r,n)->new Member(r.getString(1),r.getString(2)),excludedUser,query,enterprise,size,(long)page*size);
        return new Page<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    @Override public Map<String,String> names(String enterprise,Collection<String> userIds) {
        uuid(enterprise);var ids=userIds.stream().distinct().map(vn.utbridora.auth.operations.WorkflowValues::uuid).toList();
        if(ids.size()>100) throw failure(400,"SCHEMA_VALIDATION");
        if(ids.isEmpty()) return Map.of();
        var args=new ArrayList<Object>();args.add(enterprise);args.addAll(ids);
        var result=new LinkedHashMap<String,String>();
        jdbc.query("SELECT u.id,u.display_name FROM users u WHERE EXISTS(SELECT 1 FROM memberships m WHERE m.user_id=u.id AND m.organization_id=? AND m.role='ENTERPRISE') AND u.id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",
            r->{result.put(r.getString(1),r.getString(2));},args.toArray());
        return Map.copyOf(result);
    }
    @Override public Map<String,String> organizationNames(Collection<String> enterpriseIds) {
        var ids=enterpriseIds.stream().distinct().map(vn.utbridora.auth.operations.WorkflowValues::uuid).toList();
        if(ids.size()>100) throw failure(400,"SCHEMA_VALIDATION");
        if(ids.isEmpty()) return Map.of();
        var result=new LinkedHashMap<String,String>();
        jdbc.query("SELECT id,name FROM organizations WHERE type='ENTERPRISE' AND id IN ("+String.join(",",Collections.nCopies(ids.size(),"?"))+")",r->{result.put(r.getString(1),r.getString(2));},ids.toArray());
        return Map.copyOf(result);
    }
}
