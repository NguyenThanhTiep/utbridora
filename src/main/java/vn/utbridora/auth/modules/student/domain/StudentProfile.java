package vn.utbridora.auth.modules.student.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Own student profile per term (BC11). Collections are stored as JSON documents in the row. */
@Entity @Table(name="student_profiles")
public class StudentProfile {
    @Id @Column(length=36) public String id;
    @Column(name="student_user_id", nullable=false, length=36) public String studentUserId;
    @Column(name="school_org_id", nullable=false, length=36) public String schoolOrgId;
    @Column(name="term_id", nullable=false, length=36) public String termId;
    @Column(name="major_code", length=64) public String majorCode;
    @Column(length=20) public String phone;
    @Column(name="cv_file_id", length=36) public String cvFileId;
    @Column(name="skills_declared", nullable=false) public boolean skillsDeclared;
    @Column(name="availability_declared", nullable=false) public boolean availabilityDeclared;
    @Column(nullable=false, columnDefinition="json") public String skills;
    @Column(name="custom_skills", nullable=false, columnDefinition="json") public String customSkills;
    @Column(name="available_periods", nullable=false, columnDefinition="json") public String availablePeriods;
    @Column(name="orientation_codes", nullable=false, columnDefinition="json") public String orientationCodes;
    @Column(name="subject_codes", nullable=false, columnDefinition="json") public String subjectCodes;
    @Column(nullable=false, columnDefinition="json") public String projects;
    @Column(nullable=false, columnDefinition="json") public String certificates;
    @Column(name="profile_version", nullable=false) public long profileVersion;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    @PrePersist void onCreate(){ if(id==null) id=UUID.randomUUID().toString(); createdAt=Instant.now(); updatedAt=createdAt; }
    @PreUpdate void onUpdate(){ updatedAt=Instant.now(); }
}
