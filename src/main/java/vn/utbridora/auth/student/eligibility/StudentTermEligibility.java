
package vn.utbridora.auth.student.eligibility;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "student_term_eligibilities",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_student_term_eligibility",
        columnNames = {"student_roster_id", "term_code"}
    )
)
public class StudentTermEligibility {

    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "student_roster_id", nullable = false, length = 36)
    public String studentRosterId;

    @Column(name = "term_code", nullable = false, length = 100)
    public String termCode;

    @Column(name="user_id",length=36) public String userId;
    @Column(name="school_org_id",length=36) public String schoolOrgId;
    @Column(name="term_id",length=36) public String termId;
    @Version @Column(nullable=false) public long version=1;
    @Column(name="approved_by",length=36) public String approvedBy;
    @Column(name="approved_at") public Instant approvedAt;
    @Column(name="reason_code",length=64) public String reasonCode;

    @Column(name = "school_approved", nullable = false)
    public boolean schoolApproved = false;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
