package vn.utbridora.auth.modules.matching;

import java.nio.file.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.utbridora.auth.modules.matching.domain.StrictClassifier;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

class StrictClassifierTest {
    @Test void independentSrsOracleS1ThroughS11RunsAgainstRealClassifier() throws Exception {
        var oracle=JSON.readTree(Files.readString(Path.of("docs/testing/fixtures/t4-s1-s11.json")));
        var required=oracle.get("required");
        var skills=new ArrayList<SkillCriterion>();required.get("skills").forEach(s->skills.add(new SkillCriterion(s.asString(),null,null)));
        var majors=new ArrayList<String>();required.get("majorCodes").forEach(m->majors.add(m.asString()));
        var criteria=new RequiredCriteria(majors,skills,new DateRange(LocalDate.parse(required.get("dateRange").get("startDate").asString()),LocalDate.parse(required.get("dateRange").get("endDate").asString())));
        int count=0;
        for(var c:oracle.get("cases")) {
            var p=c.get("profile");var values=new ArrayList<SkillValue>();p.get("skills").forEach(s->values.add(new SkillValue(s.asString(),3,BigDecimal.ONE,"SELF_DECLARED")));
            var periods=new ArrayList<vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange>();
            p.get("availablePeriods").forEach(r->periods.add(new vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange(r.get("startDate").asString(),r.get("endDate").asString())));
            var profile=new ProfileSnapshot("snapshot","student","school","term",p.get("majorCode").isNull()?null:p.get("majorCode").asString(),p.get("skillsDeclared").asBoolean(),values,p.get("availabilityDeclared").asBoolean(),periods,1,"hash","demo-v1","academic-v1","skills-v1",Instant.EPOCH);
            var result=StrictClassifier.evaluate(profile,criteria,List.of(),Map.of("Java Core","JAVA"));
            assertEquals(c.get("expectedOutcome").asString(),result.overallStatus(),c.get("id").asString());count++;
        }
        assertEquals(11,count);
    }
    @Test void preferredScoresOnlyMatchAndUnionCoversAdjacentDates() {
        var profile=new ProfileSnapshot("s","u","school","term","SE",true,List.of(new SkillValue("JAVA",3,new BigDecimal("1.5"),"SELF_DECLARED"),new SkillValue("SQL",2,BigDecimal.ZERO,"SELF_DECLARED")),true,
            List.of(new vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange("2027-01-01","2027-01-15"),new vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange("2027-01-16","2027-02-01")),1,"h","demo-v1","a","t",Instant.EPOCH);
        var criteria=new RequiredCriteria(List.of("SE"),List.of(new SkillCriterion("JAVA",3,new BigDecimal("1.5"))),new DateRange(LocalDate.parse("2027-01-01"),LocalDate.parse("2027-02-01")));
        var preferred=List.of(new SkillCriterion("JAVA",null,null),new SkillCriterion("SQL",null,null),new SkillCriterion("GO",null,null));
        assertEquals(new BigDecimal("66.67"),StrictClassifier.evaluate(profile,criteria,preferred,Map.of()).fitScore());
        var fail=new RequiredCriteria(List.of("OTHER"),criteria.skills(),criteria.dateRange());
        assertNull(StrictClassifier.evaluate(profile,fail,preferred,Map.of()).fitScore());
    }
}
