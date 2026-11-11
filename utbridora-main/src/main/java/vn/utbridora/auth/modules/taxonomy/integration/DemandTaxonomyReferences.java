package vn.utbridora.auth.modules.taxonomy.integration;

import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.modules.cooperation.integration.DemandAcademicPort;
import vn.utbridora.auth.modules.cooperation.integration.DemandReferencePort;
import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.taxonomy.repository.*;
import vn.utbridora.auth.modules.taxonomy.domain.SkillStatus;

/** T4 owner adapter. A real catalog version and canonical ACTIVE skill are mandatory. */
@Service
public class DemandTaxonomyReferences implements DemandReferencePort {
    private final DemandAcademicPort academic;
    private final TaxonomyCatalogStateRepository state;
    private final SkillRepository skills;
    public DemandTaxonomyReferences(DemandAcademicPort academic,TaxonomyCatalogStateRepository state,SkillRepository skills) {
        this.academic=academic;this.state=state;this.skills=skills;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void validate(String enterpriseOrgId,DemandInput input) {
        academic.validate(enterpriseOrgId,input);
        var current=state.findLockedById(1).orElseThrow(()->new DemandFailure(503,"DEPENDENCY_UNAVAILABLE","Danh mục kỹ năng chưa sẵn sàng."));
        if(!input.taxonomyVersion().equals("skills-v"+current.taxonomyVersion))
            throw new DemandFailure(409,"CATALOG_CHANGED","Danh mục kỹ năng đã thay đổi.");
        Stream.concat(input.required().skills().stream(),input.preferred().stream()).forEach(criterion->{
            if(skills.findBySkillCode(criterion.skillCode()).filter(skill->skill.status==SkillStatus.ACTIVE).isEmpty())
                throw new DemandFailure(422,"INVALID_SKILL_CODE","Kỹ năng chưa thuộc danh mục chuẩn đang hoạt động.");
        });
    }
}
