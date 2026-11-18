package vn.utbridora.auth.modules.internship.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.internship.api.GradeDtos.*;
import static org.junit.jupiter.api.Assertions.*;

class GradeJsonTest {
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{\"enabled\":true}","{\"expectedVersion\":0,\"enabled\":null}","{\"expectedVersion\":\"0\",\"enabled\":true}","{\"expectedVersion\":0.5,\"enabled\":true}","{\"expectedVersion\":-1,\"enabled\":true}","{\"expectedVersion\":0,\"enabled\":\"true\"}","{\"expectedVersion\":0,\"enabled\":true,\"enabled\":false}","{\"expectedVersion\":0,\"enabled\":true,\"actor\":\"spoof\"}","{\"expectedVersion\":0,\"enabled\":true} {}"})
    void rejectsMalformedOrCoercedPolicy(String json){assertEquals(400,assertThrows(OperationFailure.class,()->GradeController.parse(json,SetVisibility.class)).status);}
    @Test void acceptsMissingPolicyVersionAndUnicodeDecisionReference(){
        assertEquals(new SetVisibility(0L,true),GradeController.parse("{\"expectedVersion\":0,\"enabled\":true}",SetVisibility.class));
        assertEquals("Quyết định 01",new PublishEvaluation(1L,"  Quyết định 01  ").decisionRef());
        assertThrows(OperationFailure.class,()->new PublishEvaluation(0L,"DECISION"));
        assertThrows(OperationFailure.class,()->new CreateEvaluationRevision(1L,"DECISION","invalid reason"));
    }
}
