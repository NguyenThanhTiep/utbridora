package vn.utbridora.auth.modules.cooperation.api;

import java.time.Instant;

/** Persisted assessment of one immutable submission, separate from the frozen verification DTO. */
public record VerificationTaxCheckView(String verificationId, int submissionNo, String state, int attemptCount,
    Instant nextAttemptAt, Instant completedAt, TaxAssessmentView assessment) {}
