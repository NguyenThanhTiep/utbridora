package vn.utbridora.auth.modules.student.application;

import java.util.Optional;

/**
 * Authoritative student–school–term link for the profile (BC21 v1.1 lines 822-833, K.2 line 1847): the T5
 * TermReference plus the T2 roster/eligibility link. The profile takes its school from here, never from the
 * first STUDENT membership.
 */
public interface StudentTermScopePort {
    /** School of the term when this student is linked to it; empty when the term is unknown or not linked. */
    Optional<String> schoolOfLinkedTerm(String studentUserId,String termId);
}
