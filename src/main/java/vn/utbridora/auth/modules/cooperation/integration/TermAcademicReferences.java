package vn.utbridora.auth.modules.cooperation.integration;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.organization.*;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

/** Reads Dev10's actual term registry; canonical majors remain owned by T2. */
public class TermAcademicReferences implements DemandAcademicPort {
    private final TermRepository terms;
    private final OrganizationRepository organizations;
    private final ObjectProvider<DemandMajorCatalogPort> majors;
    public TermAcademicReferences(TermRepository terms,OrganizationRepository organizations,ObjectProvider<DemandMajorCatalogPort> majors) {
        this.terms=terms; this.organizations=organizations; this.majors=majors;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void validate(String enterpriseOrgId,DemandInput input) {
        try {
            var term=terms.findByIdForUpdate(input.termId()).orElseThrow(()->invalid("termId","Không tìm thấy kỳ thực tập hợp lệ."));
            if(!term.schoolOrgId.equals(input.schoolOrgId())) throw invalid("termId","Kỳ thực tập không thuộc trường được chọn.");
            if(term.status!=TermStatus.ACTIVE) throw invalid("termId","Kỳ thực tập chưa hoạt động hoặc đã đóng.");
            var school=organizations.findById(input.schoolOrgId()).orElseThrow(()->invalid("schoolOrgId","Trường không hợp lệ."));
            if(school.type!=Organization.Type.UNIVERSITY || school.status!=Organization.Status.VERIFIED)
                throw invalid("schoolOrgId","Tổ chức nhà trường chưa đủ điều kiện sử dụng.");
            var source=majors.getIfAvailable();
            if(source==null) throw unavailable();
            var catalog=source.read(enterpriseOrgId,input.schoolOrgId(),input.termId());
            if(catalog==null || !input.schoolOrgId().equals(catalog.schoolOrgId()) || !input.termId().equals(catalog.termId())) throw unavailable();
            if(!input.academicCatalogVersion().equals(catalog.version()))
                throw new DemandFailure(409,"CATALOG_CHANGED","Danh mục học vụ đã thay đổi.",
                    List.of(new FieldError("academicCatalogVersion","CATALOG_CHANGED","Tải lại phiên bản danh mục học vụ.")));
            for(String code:input.required().majorCodes()) if(!catalog.activeMajorCodes().contains(code))
                throw invalid("required.majorCodes","Mã ngành không thuộc danh mục đang hoạt động của trường/kỳ.");
        } catch(DataAccessException failure) { throw unavailable(); }
    }
    private DemandFailure unavailable() { return new DemandFailure(503,"DEPENDENCY_UNAVAILABLE","Nguồn kỳ hoặc danh mục ngành chưa sẵn sàng."); }
    private DemandFailure invalid(String field,String message) {
        return new DemandFailure(422,"INVALID_CATALOG_REFERENCE","Thông tin trường/kỳ/ngành không hợp lệ.",List.of(new FieldError(field,"CATALOG_CODE_INVALID",message)));
    }
}
