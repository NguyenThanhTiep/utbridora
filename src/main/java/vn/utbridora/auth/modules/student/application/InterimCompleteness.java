package vn.utbridora.auth.modules.student.application;

import java.util.List;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/**
 * Interim CompletenessResult until T2-04 wires the T4 CompletenessPolicy (BC11 v1.0 lines 750, 784).
 * Checks that need sources not available yet fail closed with contract reason codes instead of passing:
 * no academic catalog is consulted, so a declared major is not ACTIVE in any catalog (MAJOR_INVALID); no
 * authoritative student-term link exists (STUDENT_TERM_MISSING); canonical skills cannot be checked against the
 * taxonomy (SKILL_INVALID when any are declared). complete is the AND of the six checks, so it stays false.
 */
final class InterimCompleteness {
    private InterimCompleteness() {}

    static CompletenessResult evaluate(SelfProfileData profile) {
        List<CompletenessCheck> checks=List.of(
            new CompletenessCheck("MAJOR_VALID",false,profile.majorCode()==null?"MAJOR_MISSING":"MAJOR_INVALID"),
            new CompletenessCheck("STUDENT_TERM_PRESENT",false,"STUDENT_TERM_MISSING"),
            check("SKILLS_DECLARED",profile.skillsDeclared(),"SKILLS_NOT_DECLARED"),
            check("SKILLS_VALID",profile.skills().isEmpty(),"SKILL_INVALID"),
            check("AVAILABILITY_DECLARED",profile.availabilityDeclared(),"AVAILABILITY_NOT_DECLARED"),
            check("AVAILABILITY_VALID",profile.availablePeriods().stream().allMatch(p->p.startDate().compareTo(p.endDate())<=0),"PERIOD_INVALID"));
        return new CompletenessResult("demo-v1",checks.stream().allMatch(CompletenessCheck::passed),checks);
    }
    private static CompletenessCheck check(String code,boolean passed,String failure) {
        return new CompletenessCheck(code,passed,passed?"OK":failure);
    }
}
