package vn.utbridora.auth.modules.matching.application;
import org.springframework.stereotype.Service;
import vn.utbridora.auth.modules.matching.domain.CompletenessPolicy;
import vn.utbridora.auth.modules.matching.port.CompletenessPort;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;
@Service
public class CompletenessService implements CompletenessPort {
    @Override public CompletenessResult evaluate(SelfProfileData profile,boolean linked,boolean majorValid,boolean skillsValid) {return CompletenessPolicy.evaluate(profile,linked,majorValid,skillsValid);}
}
