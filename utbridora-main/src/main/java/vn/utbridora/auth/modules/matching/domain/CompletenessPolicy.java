package vn.utbridora.auth.modules.matching.domain;

import java.util.List;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/** Pure BC11 policy owned by T4. Completeness does not imply MATCH or school approval. */
public final class CompletenessPolicy {
    private CompletenessPolicy() {}
    public static CompletenessResult evaluate(SelfProfileData p,boolean linked,boolean majorValid,boolean skillsValid) {
        var checks=List.of(
            new CompletenessCheck("MAJOR_VALID",majorValid,p.majorCode()==null?"MAJOR_MISSING":majorValid?"OK":"MAJOR_INVALID"),
            check("STUDENT_TERM_PRESENT",linked,"STUDENT_TERM_MISSING"),
            check("SKILLS_DECLARED",p.skillsDeclared(),"SKILLS_NOT_DECLARED"),
            check("SKILLS_VALID",skillsValid,"SKILL_INVALID"),
            check("AVAILABILITY_DECLARED",p.availabilityDeclared(),"AVAILABILITY_NOT_DECLARED"),
            check("AVAILABILITY_VALID",p.availablePeriods().stream().allMatch(r->r.startDate().compareTo(r.endDate())<=0),"PERIOD_INVALID"));
        return new CompletenessResult("demo-v1",checks.stream().allMatch(CompletenessCheck::passed),checks);
    }
    private static CompletenessCheck check(String code,boolean ok,String reason) {return new CompletenessCheck(code,ok,ok?"OK":reason);}
}
