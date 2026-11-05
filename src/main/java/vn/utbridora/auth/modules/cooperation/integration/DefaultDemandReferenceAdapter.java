package vn.utbridora.auth.modules.cooperation.integration;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.SkillCriterion;

/**
 * Explicit local demo adapter with static sample codes, NOT an authoritative catalog.
 * Does not verify school ownership, term availability or catalog versions.
 * Disable it when installing the T2/T4/T5 reference adapter.
 */
@Component
@ConditionalOnProperty(name = "app.t5.demand.default-reference-adapter.enabled", havingValue = "true", matchIfMissing = false)
public class DefaultDemandReferenceAdapter implements DemandReferencePort {

    private static final Set<String> ALLOWED_MAJORS = Set.of("IT", "SE", "CS", "IS", "DS", "AI");
    private static final Set<String> ALLOWED_SKILLS = Set.of(
            "JAVA", "SPRING_BOOT", "PYTHON", "REACT", "NODEJS", "DOCKER", "KUBERNETES",
            "SQL", "MYSQL", "POSTGRESQL", "AWS", "GIT", "REST_API", "TYPESCRIPT", "JAVASCRIPT"
    );

    @Override
    public void validate(String enterpriseOrgId, DemandInput input) {
        if (input == null) {
            throw DemandFailure.invalid("data", "Dữ liệu nhu cầu không được để trống.");
        }

        // Validate major codes against allowed catalog
        if (input.required() != null && input.required().majorCodes() != null) {
            for (String major : input.required().majorCodes()) {
                if (!ALLOWED_MAJORS.contains(major.trim().toUpperCase())) {
                    throw new DemandFailure(422, "INVALID_CATALOG_REFERENCE", "Mã ngành '" + major + "' không thuộc danh mục đào tạo của trường.");
                }
            }
        }

        // Validate required skills
        if (input.required() != null && input.required().skills() != null) {
            for (SkillCriterion s : input.required().skills()) {
                if (!ALLOWED_SKILLS.contains(s.skillCode().trim().toUpperCase())) {
                    throw new DemandFailure(422, "INVALID_SKILL_CODE", "Mã kỹ năng '" + s.skillCode() + "' chưa được chuẩn hóa theo danh mục T4.");
                }
            }
        }

        // Validate preferred skills
        if (input.preferred() != null) {
            for (SkillCriterion s : input.preferred()) {
                if (!ALLOWED_SKILLS.contains(s.skillCode().trim().toUpperCase())) {
                    throw new DemandFailure(422, "INVALID_SKILL_CODE", "Mã kỹ năng ưu tiên '" + s.skillCode() + "' chưa được chuẩn hóa theo danh mục T4.");
                }
            }
        }
    }
}
