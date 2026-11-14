package vn.utbridora.auth.modules.internship.api;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationJsonTest {
    private static final String SCORE="{\"criterionCode\":\"A\",\"score\":3.25,\"comment\":null,\"evidenceFileIds\":[]}";
    private static String update(String score) { return "{\"expectedVersion\":1,\"scores\":["+score+"]}"; }
    @Test void preservesExactDecimalAndNullableComment() {
        var result=EvaluationJson.update(update(SCORE));
        assertEquals(new BigDecimal("3.25"),result.scores().getFirst().score());
        assertNull(result.scores().getFirst().comment());
        assertEquals(1,result.expectedVersion());
        assertEquals(1,EvaluationJson.submit("{\"expectedVersion\":1}").expectedVersion());
    }
    @ParameterizedTest @ValueSource(strings={"\"3.25\"","true","null","5.0000000000000000001","0.001","-0.01","5.01","[]","{}"})
    void rejectsCoercionAndExactOutOfRangeValues(String value) {
        invalid(()->EvaluationJson.update(update(SCORE.replace("3.25",value))));
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{","{\"expectedVersion\":null}",
        "{\"expectedVersion\":1,\"expectedVersion\":2}","{\"expectedVersion\":1} {}",
        "{\"expectedVersion\":1,\"state\":\"SUBMITTED\"}","{\"expectedVersion\":\"1\"}",
        "{\"expectedVersion\":1.0}","{\"expectedVersion\":-1}","{\"expectedVersion\":9007199254740992}"})
    void strictSubmitShape(String input) { invalid(()->EvaluationJson.submit(input)); }
    @Test void scoreFieldsAndEvidenceArrayAreClosed() {
        invalid(()->EvaluationJson.update(update(SCORE.replace(",\"comment\":null",""))));
        invalid(()->EvaluationJson.update(update(SCORE.replace("3.25","3.25,\"score\":4"))));
        invalid(()->EvaluationJson.update(update(SCORE.replace("\"comment\":null","\"comment\":true"))));
        invalid(()->EvaluationJson.update(update(SCORE.replace("\"evidenceFileIds\":[]","\"evidenceFileIds\":[null]"))));
        invalid(()->EvaluationJson.update(update(SCORE.replace("\"criterionCode\":\"A\"","\"criterionCode\":\" \""))));
    }
    private static void invalid(org.junit.jupiter.api.function.Executable call) {
        var error=assertThrows(OperationFailure.class,call); assertEquals(400,error.status);assertEquals("SCHEMA_VALIDATION",error.code);
    }
}
