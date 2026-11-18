package vn.utbridora.auth.modules.internship.domain;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationPolicyTest {
    private static final UUID ID=UUID.randomUUID();
    private static RubricCriterion criterion(String code,String weight) { return new RubricCriterion(code,code,new BigDecimal(weight)); }
    private static Score score(String code,String value) { return new Score(code,new BigDecimal(value),null,List.of()); }
    private static RubricTemplate rubric(RubricCriterion... c) { return new RubricTemplate(ID,1L,List.of(c)); }
    @Test void referenceExampleAndUnorderedScores() {
        assertEquals(new BigDecimal("76.00"),EvaluationPolicy.total(rubric(criterion("QUALITY","40"),criterion("PROCESS","60")),
                List.of(score("PROCESS","3"),score("QUALITY","5"))));
    }
    @Test void roundsOnlyOnceAndUsesHalfUp() {
        assertEquals(new BigDecimal("0.20"),EvaluationPolicy.total(rubric(criterion("A","33.33"),criterion("B","33.33"),criterion("C","33.34")),
                List.of(score("A","0.01"),score("B","0.01"),score("C","0.01"))));
        assertEquals(new BigDecimal("0.01"),EvaluationPolicy.total(rubric(criterion("A","2.50"),criterion("B","97.50")),
                List.of(score("A","0.01"),score("B","0"))));
    }
    @ParameterizedTest @ValueSource(strings={"99","101","99.99","100.01"})
    void exactWeightSumRequired(String weight) {
        var failure=assertThrows(OperationFailure.class,()->EvaluationPolicy.total(rubric(criterion("A",weight)),List.of(score("A","3"))));
        assertEquals("RUBRIC_INVALID",failure.code); assertEquals(422,failure.status);
    }
    @Test void missingDuplicateAndUnknownCriteriaRejected() {
        var template=rubric(criterion("A","40"),criterion("B","60"));
        for(var scores:List.of(List.of(score("A","3")),List.of(score("A","3"),score("A","4")),
                List.of(score("A","3"),score("C","4")),List.of(score("A","3"),score("B","4"),score("C","5"))))
            assertEquals("RUBRIC_INVALID",assertThrows(OperationFailure.class,()->EvaluationPolicy.total(template,scores)).code);
        assertThrows(OperationFailure.class,()->EvaluationPolicy.total(rubric(criterion("A","40"),criterion("A","60")),List.of(score("A","3"),score("B","3"))));
    }
    @ParameterizedTest @ValueSource(strings={"-0.01","5.01","5.0000000000000000001","0.001"})
    void scoresCannotBeClampedOrRounded(String value) { assertThrows(IllegalArgumentException.class,()->score("A",value)); }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.001","1000"})
    void weightsHaveStrictBounds(String value) { assertThrows(IllegalArgumentException.class,()->criterion("A",value)); }
    @Test void versionsAndCollectionsAreImmutable() {
        var scores=new ArrayList<>(List.of(score("A","5")));
        var draft=new EvaluationDto(ID,UUID.randomUUID(),rubric(criterion("A","100")),scores,new BigDecimal("100"),EvaluationState.DRAFT,1,1L,null,null);
        scores.clear(); assertEquals(1,draft.scores().size());
        var edited=EvaluationPolicy.change(draft,1,List.of(score("A","3")),false);
        var submitted=EvaluationPolicy.change(edited,2,edited.scores(),true);
        assertEquals(new BigDecimal("100.00"),draft.total100());
        assertEquals(new BigDecimal("60.00"),submitted.total100());
        assertEquals(3,submitted.version()); assertEquals(EvaluationState.SUBMITTED,submitted.state());
        assertEquals("STATE_CONFLICT",assertThrows(OperationFailure.class,()->EvaluationPolicy.change(submitted,3,submitted.scores(),false)).code);
        assertEquals("VERSION_CONFLICT",assertThrows(OperationFailure.class,()->EvaluationPolicy.change(edited,1,edited.scores(),false)).code);
        var published=new EvaluationDto(ID,draft.caseId(),draft.template(),draft.scores(),draft.total100(),EvaluationState.PUBLISHED,1,4L,java.time.Instant.now(),"SYNTHETIC_DECISION");
        assertThrows(OperationFailure.class,()->EvaluationPolicy.change(published,4,published.scores(),false));
    }
}
