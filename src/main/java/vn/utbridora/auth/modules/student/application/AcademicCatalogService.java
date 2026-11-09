package vn.utbridora.auth.modules.student.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.integration.DemandMajorCatalogPort;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

/** BC12 fixture owner. Deployment imports reviewed, versioned school/term data; no CRUD or sample fallback. */
@Service
public class AcademicCatalogService implements DemandMajorCatalogPort {
    public enum Kind { MAJOR, SUBJECT, ORIENTATION }
    public record Item(String code,String name,List<String> majorCodes,String status) {}
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
    public record CatalogPage(String schoolOrgId,String termId,Kind kind,String catalogVersion,Page<Item> entries) {}
    public record Snapshot(String schoolOrgId,String termId,String version,Map<Kind,List<Item>> entries) {}
    private final JdbcTemplate jdbc;
    private final StudentTermScopePort terms;
    private final AuthorizationPolicy permissions;
    private final JsonMapper json=JsonMapper.builder().build();
    public AcademicCatalogService(JdbcTemplate jdbc,StudentTermScopePort terms,AuthorizationPolicy permissions) {
        this.jdbc=jdbc;this.terms=terms;this.permissions=permissions;
    }
    @Transactional(readOnly=true)
    public CatalogPage list(Authentication actor,String termId,Kind kind,String expectedVersion,int page,int size) {
        if(!ProfileInputParser.isUuid(termId) || page<0 || page>10000 || size<1 || size>100)
            throw failure(400,"INVALID_REQUEST");
        String school=actor!=null && actor.getPrincipal() instanceof AppPrincipal p
            ? terms.schoolOfLinkedTerm(p.userId(),termId).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND")) : null;
        if(school==null || !permissions.hasOrganization(actor,school,Role.STUDENT,"student_profile:self:read"))
            throw failure(404,"RESOURCE_NOT_FOUND");
        try {
            var versions=jdbc.queryForList("SELECT catalog_version FROM academic_catalogs WHERE term_id=? AND school_org_id=?",String.class,termId,school);
            if(versions.isEmpty()) throw failure(503,"DEPENDENCY_UNAVAILABLE");
            String version=versions.getFirst();
            if(expectedVersion!=null && !expectedVersion.equals(version)) throw failure(409,"CATALOG_CHANGED");
            long total=jdbc.queryForObject("SELECT COUNT(*) FROM academic_catalog_entries WHERE term_id=? AND kind=?",Long.class,termId,kind.name());
            var entries=jdbc.query("SELECT code,name,major_codes,status FROM academic_catalog_entries WHERE term_id=? AND kind=? ORDER BY code LIMIT ? OFFSET ?",
                (r,n)->new Item(r.getString("code"),r.getString("name"),List.of(json.readValue(r.getString("major_codes"),String[].class)),r.getString("status")),termId,kind.name(),size,page*size);
            return new CatalogPage(school,termId,kind,version,new Page<>(entries,page,size,total,(int)((total+size-1)/size)));
        } catch(org.springframework.dao.DataAccessException unavailable) {throw failure(503,"DEPENDENCY_UNAVAILABLE");}
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Catalog read(String enterpriseOrgId,String schoolOrgId,String termId) {
        // Public Demand service has already checked its actor's enterprise and the T5 term.
        Snapshot source=snapshot(schoolOrgId,termId,true);
        Set<String> codes=new HashSet<>();
        source.entries().get(Kind.MAJOR).stream().filter(i->i.status().equals("ACTIVE")).forEach(i->codes.add(i.code()));
        return new Catalog(schoolOrgId,termId,source.version(),codes);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Snapshot forProfile(String school,String term) { return snapshot(school,term,true); }
    private Snapshot snapshot(String school,String term,boolean lock) {
        try {return loadSnapshot(school,term,lock);}
        catch(org.springframework.dao.DataAccessException unavailable) {throw failure(503,"DEPENDENCY_UNAVAILABLE");}
    }
    private Snapshot loadSnapshot(String school,String term,boolean lock) {
        var versions=jdbc.queryForList("SELECT catalog_version FROM academic_catalogs WHERE term_id=? AND school_org_id=?"+(lock?" FOR SHARE":""),String.class,term,school);
        if(versions.isEmpty()) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        Map<Kind,List<Item>> entries=new EnumMap<>(Kind.class);
        for(Kind kind:Kind.values()) entries.put(kind,new ArrayList<>());
        jdbc.query("SELECT kind,code,name,major_codes,status FROM academic_catalog_entries WHERE term_id=? ORDER BY kind,code",rs->{
            entries.get(Kind.valueOf(rs.getString("kind"))).add(new Item(rs.getString("code"),rs.getString("name"),
                List.of(json.readValue(rs.getString("major_codes"),String[].class)),rs.getString("status")));
        },term);
        entries.replaceAll((kind,items)->List.copyOf(items));
        return new Snapshot(school,term,versions.getFirst(),Map.copyOf(entries));
    }
    private static OperationFailure failure(int status,String code) {
        return new OperationFailure(status,code,"Chưa thể đọc danh mục học vụ trong phạm vi hiện tại.",null);
    }
}
