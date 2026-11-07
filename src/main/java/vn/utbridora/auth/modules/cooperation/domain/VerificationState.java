package vn.utbridora.auth.modules.cooperation.domain;

/** State machine for School-Enterprise Verification.
 *  Terminal states: APPROVED, REJECTED.
 *  Open states: DRAFT, PENDING_VERIFICATION, NEEDS_INFO.
 *
 *  Transitions:
 *    DRAFT             → PENDING_VERIFICATION (enterprise submits)
 *    NEEDS_INFO        → PENDING_VERIFICATION (enterprise resubmits after fixing)
 *    PENDING_VERIFICATION → APPROVED          (school approves)
 *    PENDING_VERIFICATION → REJECTED          (school rejects)
 *    PENDING_VERIFICATION → NEEDS_INFO        (school requests more info)
 */
public enum VerificationState {
    DRAFT,
    PENDING_VERIFICATION,
    NEEDS_INFO,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    public boolean isOpen() {
        return !isTerminal();
    }
}
