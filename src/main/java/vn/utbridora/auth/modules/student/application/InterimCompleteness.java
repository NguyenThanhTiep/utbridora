package vn.utbridora.auth.modules.student.application;

import java.util.List;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/**
 * Interim CompletenessResult until T2-04 wires the T4 CompletenessPolicy (BC11 v1.0 lines 750, 784).
 * Checks that need sources not available yet fail closed with contract reason codes instead of passing:
 * the T4 policy/catalog result is not wired, so a major is not certified ACTIVE here (MAJOR_INVALID).
 * The caller has verified the student-term link; canonical skills cannot be certified against the
 * taxonomy (SKILL_INVALID when any are declared). complete is the AND of the six checks, so it stays false.
 */
final class InterimCompleteness {
    private InterimCompleteness() {}

    static CompletenessResult evaluate(SelfProfileData profile,boolean studentTermPresent) {
        List<CompletenessCheck> checks=List.of(
            new CompletenessCheck("MAJOR_VALID",false,profile.majorCode()==null?"MAJOR_MISSING":"MAJOR_INVALID"),
            check("STUDENT_TERM_PRESENT",studentTermPresent,"STUDENT_TERM_MISSING"),
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
