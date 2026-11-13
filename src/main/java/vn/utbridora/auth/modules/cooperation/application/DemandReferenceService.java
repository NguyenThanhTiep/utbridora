package vn.utbridora.auth.modules.cooperation.application;

import java.time.LocalDate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.cooperation.integration.DemandMajorCatalogPort;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.organization.*;

/** Read-only form choices; saving still validates the current owner catalogs. */
@Service
@Transactional
public class DemandReferenceService {
    public record SchoolOption(String schoolOrgId,String name) {}
    public record TermOption(String termId,String termCode,LocalDate startDate,LocalDate endDate) {}
    public record AcademicOption(String schoolOrgId,String termId,String academicCatalogVersion) {}
    private final DemandService demands;
    private final OrganizationRepository organizations;
    private final TermRepository terms;
    private final ObjectProvider<DemandMajorCatalogPort> catalogs;
    public DemandReferenceService(DemandService demands,OrganizationRepository organizations,TermRepository terms,ObjectProvider<DemandMajorCatalogPort> catalogs) {
        this.demands=demands;this.organizations=organizations;this.terms=terms;this.catalogs=catalogs;
    }
    public Page<SchoolOption> schools(Authentication actor,String enterprise,int page,int size) {
        demands.checkWorkspaceAccess(actor,enterprise);pagination(page,size);
        var result=organizations.findAll((root,query,cb)->cb.and(cb.equal(root.get("type"),Organization.Type.UNIVERSITY),cb.equal(root.get("status"),Organization.Status.VERIFIED)),PageRequest.of(page,size,Sort.by("name").and(Sort.by("id"))));
        return new Page<>(result.stream().map(s->new SchoolOption(s.id,s.name)).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    public Page<TermOption> terms(Authentication actor,String enterprise,String school,int page,int size) {
        demands.checkWorkspaceAccess(actor,enterprise);pagination(page,size);school(school);
        var result=terms.findBySchoolOrgIdAndStatus(school,TermStatus.ACTIVE,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"startDate").and(Sort.by("id"))));
        return new Page<>(result.stream().map(t->new TermOption(t.id,t.termCode,t.startDate,t.endDate)).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    public AcademicOption academic(Authentication actor,String enterprise,String school,String term) {
        demands.checkWorkspaceAccess(actor,enterprise);school(school);DemandValidator.uuid(term,"termId");
        var current=terms.findByIdForUpdate(term).orElseThrow(DemandReferenceService::missing);
        if(!school.equals(current.schoolOrgId) || current.status!=TermStatus.ACTIVE) throw missing();
        var source=catalogs.getIfAvailable();
        if(source==null) throw new DemandFailure(503,"DEPENDENCY_UNAVAILABLE","Chưa thể tải danh mục học vụ.");
        var catalog=source.read(enterprise,school,term);
        if(catalog==null || !school.equals(catalog.schoolOrgId()) || !term.equals(catalog.termId()))
            throw new DemandFailure(503,"DEPENDENCY_UNAVAILABLE","Chưa thể tải danh mục học vụ.");
        return new AcademicOption(school,term,catalog.version());
    }
    private void school(String id) {
        var school=organizations.findById(DemandValidator.uuid(id,"schoolOrgId")).orElseThrow(DemandReferenceService::missing);
        if(school.type!=Organization.Type.UNIVERSITY || school.status!=Organization.Status.VERIFIED) throw missing();
    }
    private static void pagination(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw new DemandFailure(400,"INVALID_PAGINATION","Phân trang không hợp lệ.");
    }
    private static DemandFailure missing() {return new DemandFailure(404,"RESOURCE_NOT_FOUND","Không tìm thấy trường hoặc kỳ đang hoạt động.");}
}
