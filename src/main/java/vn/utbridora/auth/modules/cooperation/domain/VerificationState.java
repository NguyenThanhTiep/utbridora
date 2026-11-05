package vn.utbridora.auth.modules.cooperation.domain;

/** State machine for School-Enterprise Verification.
 *  Terminal states: APPROVED, REJECTED.
 *  Open states: DRAFT, SUBMITTED, NEEDS_CHANGES.
 */
public enum VerificationState {
    DRAFT,
    SUBMITTED,
    NEEDS_CHANGES,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED;
    }

    public boolean isOpen() {
        return !isTerminal();
    }
}
