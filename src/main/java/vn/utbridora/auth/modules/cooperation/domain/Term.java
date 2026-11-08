package vn.utbridora.auth.modules.cooperation.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * TermReference — owned by T5.
 * Represents one academic internship term belonging to a university organization.
 * Table: terms
 */
@Entity
@Table(name = "terms")
public class Term {

    @Id
    @Column(length = 36)
    public String id;

    /** FK to organizations (T1). Must be UNIVERSITY type. */
    @Column(name = "school_org_id", nullable = false, length = 36)
    public String schoolOrgId;

    /** Short human-readable code unique per school, e.g. "2026-T1". */
    @Column(name = "term_code", nullable = false, length = 64)
    public String termCode;

    @Column(name = "start_date", nullable = false)
    public LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    public LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false)
    public TermStatus status = TermStatus.DRAFT;

    /** Optimistic-lock version. Incremented on every mutation. */
    @Version
    @Column(nullable = false)
    public long version = 1;

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
