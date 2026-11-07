package vn.utbridora.auth.modules.cooperation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only history of every state transition for an EnterpriseVerification.
 * Table: enterprise_verification_history
 */
@Entity
@Table(name = "enterprise_verification_history")
public class VerificationHistory {

    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "verification_id", nullable = false, length = 36)
    public String verificationId;

    @Column(name = "from_state", nullable = false, length = 32)
    public String fromState;

    @Column(name = "to_state", nullable = false, length = 32)
    public String toState;

    /** Actor who caused this transition (enterprise user or school staff). */
    @Column(name = "actor_id", nullable = false, length = 36)
    public String actorId;

    @Column(name = "reason_code", length = 64)
    public String reasonCode;

    @Column(name = "missing_fields", length = 500)
    public String missingFields;

    @Column(name = "decision_ref", length = 120)
    public String decisionRef;

    @Column(name = "changed_at", nullable = false)
    public Instant changedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (changedAt == null) changedAt = Instant.now();
    }
}
