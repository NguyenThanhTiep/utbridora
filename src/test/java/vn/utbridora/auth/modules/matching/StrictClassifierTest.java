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
    // DEV8 đã bổ sung từ kiểm tra một trường hợp Preferred sang bao phủ làm tròn, UNKNOWN, điểm null và tie-break, phục vụ nghiệm thu R11.
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

    @Test void r11PreferredScoresUnknownNoPreferredAndStableTies() {
        var required=new RequiredCriteria(List.of(),List.of(),new DateRange(LocalDate.parse("2027-01-01"),LocalDate.parse("2027-01-02")));
        var available=List.of(new vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange("2027-01-01","2027-01-02"));
        var preferred=List.of(new SkillCriterion("JAVA",null,null),new SkillCriterion("SQL",null,null),new SkillCriterion("GO",null,null));
        var twoOfThree=profile("u-2",true,List.of("JAVA","SQL"),true,available);
        var oneOfThree=profile("u-1",true,List.of("JAVA"),true,available);
        var unknownPreferred=profile("u-3",false,List.of(),true,available);
        var noPreferred=profile("u-4",true,List.of(),true,available);

        assertEquals(new BigDecimal("66.67"),StrictClassifier.evaluate(twoOfThree,required,preferred,Map.of()).fitScore());
        assertEquals(new BigDecimal("33.33"),StrictClassifier.evaluate(oneOfThree,required,preferred,Map.of()).fitScore());
        assertEquals(new BigDecimal("0.00"),StrictClassifier.evaluate(unknownPreferred,required,preferred,Map.of()).fitScore());
        assertEquals("UNKNOWN",StrictClassifier.evaluate(unknownPreferred,required,preferred,Map.of()).details().get(2).status().name());
        assertEquals(new BigDecimal("0.00"),StrictClassifier.evaluate(noPreferred,required,List.of(),Map.of()).fitScore());
        assertNull(StrictClassifier.evaluate(profile("u-5",true,List.of(),false,List.of()),required,preferred,Map.of()).fitScore());
        assertNull(StrictClassifier.evaluate(profile("u-6",true,List.of(),true,available),
            new RequiredCriteria(List.of("REQUIRED_MAJOR"),List.of(),required.dateRange()),preferred,Map.of()).fitScore());

        var tiedA=StrictClassifier.evaluate(profile("student-a",true,List.of("JAVA"),true,available),required,preferred,Map.of());
        var tiedB=StrictClassifier.evaluate(profile("student-b",true,List.of("JAVA"),true,available),required,preferred,Map.of());
        assertEquals(List.of("student-a","student-b"),List.of(tiedB,tiedA).stream().sorted(StrictClassifier.ranking()).map(StrictClassifier.MatchResult::studentUserId).toList());
        assertEquals(List.of("student-a","student-b"),List.of(
            new StrictClassifier.MatchResult("student-b","s",1,"h","NOT_MATCH",new BigDecimal("99.00"),List.of()),
            new StrictClassifier.MatchResult("student-a","s",1,"h","MISSING_DATA",null,List.of()))
            .stream().sorted(StrictClassifier.ranking()).map(StrictClassifier.MatchResult::studentUserId).toList());
    }

    private static ProfileSnapshot profile(String userId,boolean skillsDeclared,List<String> skillCodes,boolean availabilityDeclared,
                                           List<vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange> periods) {
        var skills=skillCodes.stream().map(code->new SkillValue(code,1,BigDecimal.ZERO,"SELF_DECLARED")).toList();
        return new ProfileSnapshot("snapshot-"+userId,userId,"school","term",null,skillsDeclared,skills,availabilityDeclared,periods,
            1,"hash","demo-v1","academic-v1","skills-v1",Instant.EPOCH);
    }
}
