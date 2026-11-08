
package vn.utbridora.auth.student.roster;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "student_rosters",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_student_roster_school_code",
        columnNames = {"school_org_id", "student_code"}
    )
)
public class StudentRoster {

    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "school_org_id", nullable = false, length = 36)
    public String schoolOrgId;

    @Column(name = "student_code", nullable = false, length = 32)
    public String studentCode;

    @Column(name = "user_id", length = 36)
    public String userId;

    @Column(name = "full_name", nullable = false, length = 150)
    public String fullName;

    @Column(nullable = false, length = 254)
    public String email;

    @Column(name = "major_code", nullable = false)
    public String majorCode;

    @Column(name = "term_code", nullable = false)
    public String termCode;

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
