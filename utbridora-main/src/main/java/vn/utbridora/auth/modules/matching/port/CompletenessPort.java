package vn.utbridora.auth.modules.matching.port;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;
/** Pure policy boundary. Source booleans are constructed by the T2 owner, never a public request. */
public interface CompletenessPort {
    CompletenessResult evaluate(SelfProfileData profile,boolean studentTermPresent,boolean majorCodeValid,boolean canonicalSkillsValid);
}
