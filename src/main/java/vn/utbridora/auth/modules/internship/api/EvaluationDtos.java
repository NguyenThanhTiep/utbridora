package vn.utbridora.auth.modules.internship.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;

/** CP-WS4 values. Decimal inputs never pass through double or silently round. */
public final class EvaluationDtos {
    private EvaluationDtos() {}
    public enum EvaluationState { DRAFT, SUBMITTED, PUBLISHED }
    public record Score(String criterionCode, BigDecimal score, String comment, List<UUID> evidenceFileIds) {
        public Score {
            criterionCode = text(criterionCode, 64, "criterionCode");
            score = decimal(score, BigDecimal.ZERO, new BigDecimal("5"));
            if (comment != null) {
                comment=comment.replaceAll("(?U)^\\s+|\\s+$", "");
                if (comment.codePointCount(0,comment.length()) > 2000) throw new IllegalArgumentException("comment length");
            }
            evidenceFileIds = ids(evidenceFileIds, "evidenceFileIds");
            if (evidenceFileIds.size() > 20 || new HashSet<>(evidenceFileIds).size() != evidenceFileIds.size())
                throw new IllegalArgumentException("Invalid evidenceFileIds");
        }
    }
    public record RubricCriterion(String criterionCode, String label, BigDecimal weight) {
        public RubricCriterion {
            criterionCode = text(criterionCode, 64, "criterionCode");
            label = text(label, 200, "label");
            weight = decimal(weight, new BigDecimal("0.01"), new BigDecimal("999.99"));
        }
    }
    public record RubricTemplate(UUID templateId, Long templateVersion, List<RubricCriterion> criteria) {
        public RubricTemplate {
            required(templateId, "templateId"); version(templateVersion, "templateVersion");
            criteria = bounded(criteria);
        }
    }
    public record EvaluationInput(UUID caseId, UUID templateId, Long templateVersion, List<Score> scores) {
        public EvaluationInput {
            required(caseId, "caseId"); required(templateId, "templateId"); version(templateVersion, "templateVersion");
            scores = bounded(scores);
        }
    }
    public record UpdateEvaluation(Long expectedVersion, List<Score> scores) {
        public UpdateEvaluation { version(expectedVersion, "expectedVersion"); scores = bounded(scores); }
    }
    public record SubmitEvaluation(Long expectedVersion) {
        public SubmitEvaluation { version(expectedVersion, "expectedVersion"); }
    }
    public record EvaluationDto(UUID evaluationId, UUID caseId, RubricTemplate template, List<Score> scores,
            BigDecimal total100, EvaluationState state, Integer revisionNo, Long version,
            Instant publishedAt, String decisionRef) {
        public EvaluationDto {
            required(evaluationId, "evaluationId"); required(caseId, "caseId"); required(template, "template");
            scores = bounded(scores); total100 = decimal(total100, BigDecimal.ZERO, new BigDecimal("100"));
            required(state, "state"); CohortGroupDtos.version(version, "version");
            if (revisionNo == null || revisionNo < 1) throw new IllegalArgumentException("revisionNo");
            if (state == EvaluationState.PUBLISHED && (publishedAt == null || decisionRef == null)
                    || state != EvaluationState.PUBLISHED && (publishedAt != null || decisionRef != null))
                throw new IllegalArgumentException("publication fields");
        }
    }
    private static <T> List<T> bounded(List<T> items) {
        if (items == null || items.isEmpty() || items.size() > 100 || items.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Expected 1..100 values");
        return List.copyOf(items);
    }
    private static BigDecimal decimal(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || value.compareTo(min) < 0 || value.compareTo(max) > 0
                || value.stripTrailingZeros().scale() > 2) throw new IllegalArgumentException("Invalid decimal");
        return value.setScale(2);
    }
}
