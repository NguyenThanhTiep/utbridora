package vn.utbridora.auth.modules.student.application;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileDecimalTest {
    private String body(String years) {
        return """
          {"expectedProfileVersion":0,"termId":"00000000-0000-4000-8000-000000000021",
           "majorCode":null,"skillsDeclared":true,
           "skills":[{"skillCode":"JAVA","level":3,"yearsPractice":%s,"note":null}],
           "customSkills":[{"customSkillId":null,"name":"Custom","categoryCode":"TECH","level":2,"yearsPractice":%s,"note":null}],
           "availabilityDeclared":false,"availablePeriods":[],"orientationCodes":[],"subjectCodes":[],
           "projects":[],"certificates":[],"cvFileId":null,"phone":null}
          """.formatted(years,years);
    }
    @Test void approvedContractDecimalsHaveNoInventedRangeOrScaleLimit() {
        for(String years:new String[]{"0","1.001","1000","0.12345678901234567890123456789","1e100"}) {
            var input=new ProfileInputParser().parse(body(years));
            assertEquals(0,new BigDecimal(years).compareTo(input.skills().getFirst().yearsPractice()));
            assertEquals(0,new BigDecimal(years).compareTo(input.customSkills().getFirst().yearsPractice()));
        }
    }
    @Test void negativeAndNonNumericExperienceRemainInvalid() {
        for(String years:new String[]{"-0.001","\"1.001\"","null"})
            assertThrows(StudentProfileException.class,()->new ProfileInputParser().parse(body(years)));
    }
}
