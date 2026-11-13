package vn.utbridora.auth.modules.cooperation.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contract v1.0 wire types; runtime readiness and source integration are separate. */
public final class DemandModels {
    private DemandModels() {}
    public record DateRange(LocalDate startDate, LocalDate endDate) {}
    public record SkillCriterion(String skillCode, Integer minLevel, BigDecimal minYearsPractice) {}
    public record RequiredCriteria(List<String> majorCodes, List<SkillCriterion> skills, DateRange dateRange) {}
    public record DemandInput(String schoolOrgId, String termId, String title, Integer quantity,
            String description, String location, String benefits, Instant deadline,
            RequiredCriteria required, List<SkillCriterion> preferred,
            String academicCatalogVersion, String taxonomyVersion) {}
    public record UpdateDemand(Long expectedVersion, DemandInput data) {}
    public record DemandDto(String demandId, String enterpriseOrgId, String state, long version,
            long criteriaVersion, String validationState, DemandInput data,
            String cooperationId, Instant softDeletedAt) {}
    public record DemandCriteriaSnapshot(String demandId, String schoolOrgId, String enterpriseOrgId,
            String termId, int quantity, long demandVersion, long criteriaVersion, String validationState,
            RequiredCriteria required, List<SkillCriterion> preferred, String criteriaHash,
            String academicCatalogVersion, String taxonomyVersion, String cooperationId,
            String cooperationState, Instant capturedAt) {
        public DemandCriteriaSnapshot {
            required = new RequiredCriteria(List.copyOf(required.majorCodes()), List.copyOf(required.skills()), required.dateRange());
            preferred = List.copyOf(preferred);
        }
    }
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record FieldError(String field, String code, String message) {}
}
