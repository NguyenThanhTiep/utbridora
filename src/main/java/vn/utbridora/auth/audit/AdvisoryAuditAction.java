package vn.utbridora.auth.audit;

/** Separate implementation-approved extension; the frozen baseline enum remains traceable. */
enum AdvisoryAuditAction {
    AI_ADVISORY_REQUESTED, AI_ADVISORY_COMPLETED, AI_ADVISORY_FAILED, AI_ADVISORY_INVALIDATED, AI_ADVISORY_VIEWED;
    static boolean supported(String action) {try {valueOf(action);return true;}catch(IllegalArgumentException invalid){return false;}}
}
