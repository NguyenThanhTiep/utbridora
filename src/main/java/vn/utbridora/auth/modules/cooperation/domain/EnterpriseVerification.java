package vn.utbridora.auth.modules.cooperation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * School-Enterprise Verification — owned by T5.
 * Represents one attempt by an enterprise to be verified by a specific university school.
 * Table: enterprise_verifications
 */
@Entity
@Table(name = "enterprise_verifications")
public class EnterpriseVerification {

    @Id
    @Column(length = 36)
    public String id;

    /** FK to organizations — must be ENTERPRISE type. */
    @Column(name = "enterprise_org_id", nullable = false, length = 36)
    public String enterpriseOrgId;

    /** FK to organizations — must be UNIVERSITY type. */
    @Column(name = "school_org_id", nullable = false, length = 36)
    public String schoolOrgId;

    /** Links to previous attempt (only for REJECTED re-submissions). */
    @Column(name = "previous_verification_id", length = 36)
    public String previousVerificationId;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false)
    public VerificationState state = VerificationState.DRAFT;

    /** Monotonically increasing counter: 0=draft never submitted, 1=first submission, etc. */
    @Column(name = "submission_no", nullable = false)
    public int submissionNo = 0;

    // --- Contact / registration data (staging; never sources from users table) ---
    @Column(name = "registration_code", length = 64)
    public String registrationCode;

    @Column(name = "registered_name", length = 200)
    public String registeredName;

    @Column(length = 500)
    public String address;

    @Column(name = "contact_name", length = 150)
    public String contactName;

    @Column(name = "contact_email", length = 254)
    public String contactEmail;

    @Column(name = "contact_phone", length = 20)
    public String contactPhone;

    /** Comma-separated field names requested by reviewer (REQUEST_INFO). */
    @Column(name = "missing_fields", length = 500)
    public String missingFields;

    /** Reference to the formal school decision document/record. */
    @Column(name = "decision_ref", length = 120)
    public String decisionRef;

    @Column(name = "submitted_at")
    public Instant submittedAt;

    @Column(name = "decided_at")
    public Instant decidedAt;

    /** Optimistic-lock version. */
    @Version
    @Column(nullable = false)
    public long version = 0;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
