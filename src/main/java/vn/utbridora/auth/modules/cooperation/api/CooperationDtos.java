package vn.utbridora.auth.modules.cooperation.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import vn.utbridora.auth.modules.cooperation.domain.TermStatus;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;

/** All request/response DTOs for the cooperation module (T5). */
public final class CooperationDtos {
    private CooperationDtos() {}

    // ─────────────────────────────────────────────────────────────────────────
    // TermReference
    // ─────────────────────────────────────────────────────────────────────────

    /** Admin/school-staff creates a new term. */
    public record CreateTerm(
            @NotBlank @Size(max = 64) String termCode,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate
    ) {}

    /** Admin activates or closes a term. */
    public record UpdateTermStatus(
            @NotNull TermStatus status,
            @NotNull long expectedVersion
    ) {}

    /** Response DTO for a TermReference. Never exposes internal FKs beyond what UI needs. */
    public record TermView(
            String termId,
            String schoolOrgId,
            String termCode,
            LocalDate startDate,
            LocalDate endDate,
            TermStatus status,
            long version,
            Instant createdAt
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Enterprise Verification
    // ─────────────────────────────────────────────────────────────────────────

    /** Enterprise submits or updates registration/contact data. */
    public record VerificationInput(
            @Size(max = 36) String schoolOrgId,
            @Size(max = 36) String previousVerificationId,
            @NotBlank @Size(max = 64)  String registrationCode,
            @NotBlank @Size(max = 200) String registeredName,
            @NotBlank @Size(max = 500) String address,
            @NotBlank @Size(max = 150) String contactName,
            @NotBlank @Email @Size(max = 254) String contactEmail,
            @NotBlank @Size(max = 20)  String contactPhone
    ) {}

    /** Update draft data only (no schoolOrgId mutation). */
    public record UpdateVerificationData(
            @NotNull long expectedVersion,
            @NotBlank @Size(max = 64)  String registrationCode,
            @NotBlank @Size(max = 200) String registeredName,
            @NotBlank @Size(max = 500) String address,
            @NotBlank @Size(max = 150) String contactName,
            @NotBlank @Email @Size(max = 254) String contactEmail,
            @NotBlank @Size(max = 20)  String contactPhone
    ) {}

    /** School staff submits a decision (APPROVE / REJECT / REQUEST_INFO). */
    public record VerificationDecision(
            @NotNull long expectedVersion,
            @NotNull DecisionType decision,
            @Size(max = 64) @Pattern(regexp = "[A-Z0-9_]{3,64}") String reasonCode,
            @Size(max = 120) String decisionRef,
            /** Comma-separated missing field names; required when decision = REQUEST_INFO. */
            @Size(max = 500) String missingFields
    ) {}

    public enum DecisionType { APPROVE, REJECT, REQUEST_INFO }

    /** Response DTO. Contact fields only visible to enterprise owner and delegated reviewer. */
    public record VerificationView(
            String verificationId,
            String enterpriseOrgId,
            String schoolOrgId,
            VerificationState state,
            int submissionNo,
            long version,
            String registrationCode,
            String registeredName,
            String address,
            String contactName,
            String contactEmail,
            String contactPhone,
            String missingFields,
            String decisionRef,
            Instant submittedAt,
            Instant reviewedAt,   // thay decidedAt
            Instant createdAt,
            Instant updatedAt
    ) {}

    /** Minimal view returned to non-owner callers (e.g. T3 guard check). */
    public record VerificationStatusView(
            String verificationId,
            String enterpriseOrgId,
            String schoolOrgId,
            VerificationState state,
            int submissionNo,
            long version,
            boolean schoolApproved
    ) {}
}
