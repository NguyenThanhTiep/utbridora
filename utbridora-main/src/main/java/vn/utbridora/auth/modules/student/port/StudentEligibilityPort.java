package vn.utbridora.auth.modules.student.port;

import org.springframework.security.core.Authentication;
public interface StudentEligibilityPort {
    void assertEligibleForNomination(Authentication actor,String student,String school,String term);
}
