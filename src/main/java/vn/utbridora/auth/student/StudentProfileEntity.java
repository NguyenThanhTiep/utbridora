package vn.utbridora.auth.student;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_profiles", uniqueConstraints = {
    @UniqueConstraint(name = "uk_student_univ", columnNames = {"student_code", "university_id"})
})
public class StudentProfileEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false, unique = true)
    private String userId;

    @Column(name = "student_code", length = 50, nullable = false)
    private String studentCode;

    @Column(name = "university_id", length = 36, nullable = false)
    private String universityId;

    @Column(name = "major_code", length = 50, nullable = false)
    private String majorCode;

    @Column(name = "academic_term", length = 50, nullable = false)
    private String academicTerm;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "status", length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    private ProfileStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ProfileStatus { DRAFT, ACTIVE, DEACTIVATED }

    public StudentProfileEntity() {}

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        if (this.status == null) this.status = ProfileStatus.ACTIVE;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }

    public String getUniversityId() { return universityId; }
    public void setUniversityId(String universityId) { this.universityId = universityId; }

    public String getMajorCode() { return majorCode; }
    public void setMajorCode(String majorCode) { this.majorCode = majorCode; }

    public String getAcademicTerm() { return academicTerm; }
    public void setAcademicTerm(String academicTerm) { this.academicTerm = academicTerm; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public ProfileStatus getStatus() { return status; }
    public void setStatus(ProfileStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}