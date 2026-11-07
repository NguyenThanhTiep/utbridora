package vn.utbridora.auth.modules.student.application;

import java.util.List;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/**
 * Interim CompletenessResult until T2-04 wires the T4 CompletenessPolicy (BC11 v1.0 lines 750, 784).
 * The academic catalog, roster and skill taxonomy are not available yet, so MAJOR_VALID only checks presence,
 * STUDENT_TERM_PRESENT relies on the verified STUDENT membership and SKILLS_VALID on the input format checks.
 */
final class InterimCompleteness {
    private InterimCompleteness() {}

    static CompletenessResult evaluate(SelfProfileData profile) {
        List<CompletenessCheck> checks=List.of(
            check("MAJOR_VALID",profile.majorCode()!=null,"MAJOR_MISSING"),
            check("STUDENT_TERM_PRESENT",true,"STUDENT_TERM_MISSING"),
            check("SKILLS_DECLARED",profile.skillsDeclared(),"SKILLS_NOT_DECLARED"),
            check("SKILLS_VALID",true,"SKILL_INVALID"),
            check("AVAILABILITY_DECLARED",profile.availabilityDeclared(),"AVAILABILITY_NOT_DECLARED"),
            check("AVAILABILITY_VALID",profile.availablePeriods().stream().allMatch(p->p.startDate().compareTo(p.endDate())<=0),"PERIOD_INVALID"));
        return new CompletenessResult("demo-v1",checks.stream().allMatch(CompletenessCheck::passed),checks);
    }
    private static CompletenessCheck check(String code,boolean passed,String failure) {
        return new CompletenessCheck(code,passed,passed?"OK":failure);
    }
}
