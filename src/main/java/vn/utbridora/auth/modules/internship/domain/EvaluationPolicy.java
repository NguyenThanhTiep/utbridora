package vn.utbridora.auth.modules.internship.domain;

import java.math.*;
import java.util.*;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** FR-20/R23. Round only the final exact sum; published/submitted snapshots are immutable. */
public final class EvaluationPolicy {
    private EvaluationPolicy() {}
    public static BigDecimal total(RubricTemplate template, List<Score> scores) {
        var weights = new LinkedHashMap<String, BigDecimal>();
        BigDecimal sum = BigDecimal.ZERO;
        for (var criterion : template.criteria()) {
            if (weights.putIfAbsent(criterion.criterionCode(), criterion.weight()) != null) throw invalid();
            sum = sum.add(criterion.weight());
        }
        if (sum.compareTo(new BigDecimal("100")) != 0 || scores.size() != weights.size()) throw invalid();
        Set<String> seen = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var score : scores) {
            var weight = weights.get(score.criterionCode());
            if (weight == null || !seen.add(score.criterionCode())) throw invalid();
            total = total.add(weight.multiply(score.score()).divide(new BigDecimal("5")));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
    public static void editable(EvaluationDto current, long expected) {
        if (current.version() != expected || expected >= 9_007_199_254_740_991L) throw failure(409, "VERSION_CONFLICT");
        if (current.state() != EvaluationState.DRAFT) throw failure(409, "STATE_CONFLICT");
    }
    public static EvaluationDto change(EvaluationDto current, long expected, List<Score> scores, boolean submit) {
        editable(current, expected);
        return new EvaluationDto(current.evaluationId(), current.caseId(), current.template(), scores,
                total(current.template(), scores), submit ? EvaluationState.SUBMITTED : EvaluationState.DRAFT,
                current.revisionNo(), expected + 1, null, null);
    }
    private static RuntimeException invalid() { return failure(422, "RUBRIC_INVALID"); }
}
