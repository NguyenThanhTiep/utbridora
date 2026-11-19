package vn.utbridora.auth.modules.internship.api;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.api.InternshipOperationsDtos.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.contracts.placement.CaseClosedEvent;

class OperationsContractTest {
    static final String ID="00000000-0000-4000-8000-000000000001";
    @Test void approvedInterestCreateAndNullableFieldAreClosedAndNeverAcceptActorAuthority() {
        String valid="{\"expectedVersion\":0,\"expectedCaseVersion\":1,\"interested\":true,\"note\":null}";
        assertEquals(0,InternshipOperationsJson.read(valid,HiringInterestInput.class).expectedVersion());
        for(String invalid:new String[]{valid.replace(",\"note\":null",""),valid.replace("true","null"),valid.replace("0,","\"0\","),valid.replace("0,","-1,"),valid.replace("}",",\"actorId\":\""+ID+"\"}"),valid+valid,valid.replace("true","true,\"interested\":false")}) {
            assertEquals("SCHEMA_VALIDATION",assertThrows(OperationFailure.class,()->InternshipOperationsJson.read(invalid,HiringInterestInput.class)).code);
        }
    }
    @Test void collaborationRatingsRejectOutOfRangeWithoutCoercingUuidOrStars() {
        String value="{\"enterpriseOrgId\":\""+ID+"\",\"termId\":\""+ID+"\",\"templateId\":\""+ID+"\",\"templateVersion\":1,\"ratings\":[{\"criterionCode\":\"MENTOR_SUPPORT\",\"stars\":5,\"comment\":null}],\"note\":null}";
        assertEquals(5,InternshipOperationsJson.read(value,EnterpriseQualityInput.class).ratings().getFirst().stars());
        assertEquals("ENTERPRISE_RUBRIC_INVALID",assertThrows(OperationFailure.class,()->InternshipOperationsJson.read(value.replace("\"stars\":5","\"stars\":0"),EnterpriseQualityInput.class)).code);
        for(String invalid:new String[]{value.replace("\"stars\":5","\"stars\":5.5"),value.replace("\"stars\":5","\"stars\":\"5\""),value.replace(ID,"AAAAAAAAAAAAAAAAAAAAAA==")})
            assertEquals("SCHEMA_VALIDATION",assertThrows(OperationFailure.class,()->InternshipOperationsJson.read(invalid,EnterpriseQualityInput.class)).code);
    }
    @Test void caseEventKeepsCanonicalMaximumReadVersionButRejectsMismatchedAggregateVersion() {
        var event=new CaseClosedEvent(ID,"CaseClosed",1,ID,ID,9_007_199_254_740_991L,9_007_199_254_740_991L,"EARLY_TERMINATION","SYNTHETIC_DECISION","SCHOOL_DECISION",Instant.EPOCH,ID);
        assertEquals(event.caseVersion(),event.aggregateVersion());
        assertThrows(IllegalArgumentException.class,()->new CaseClosedEvent(ID,"CaseClosed",1,ID,ID,2,1,"EARLY_TERMINATION","SYNTHETIC_DECISION","SCHOOL_DECISION",Instant.EPOCH,ID));
    }
}
