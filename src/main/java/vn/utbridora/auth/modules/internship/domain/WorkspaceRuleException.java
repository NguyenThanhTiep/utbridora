package vn.utbridora.auth.modules.internship.domain;

/** Internal application failure; codes are not a new HTTP error contract. */
public final class WorkspaceRuleException extends RuntimeException {
    public enum Kind { UNAUTHENTICATED, DENIED, UNAVAILABLE, NOT_FOUND, SCOPE_MISMATCH,
        DUPLICATE_COHORT, GROUP_MEMBERSHIP_CONFLICT, VERSION_CONFLICT, STATE_CONFLICT, INVALID_OPERATION }
    private final Kind kind;
    public WorkspaceRuleException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }
    public Kind kind() { return kind; }
}
