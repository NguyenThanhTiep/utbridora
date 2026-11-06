package vn.utbridora.auth.student;

import java.time.Instant;

public class StudentProfileResponse {
    private String id;
    private String userId;
    private String studentCode;
    private String universityId;
    private String majorCode;
    private String academicTerm;
    private String phoneNumber;
    private String bio;
    private StudentProfileEntity.ProfileStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public StudentProfileResponse() {}

    public StudentProfileResponse(String id, String userId, String studentCode, String universityId,
                                 String majorCode, String academicTerm, String phoneNumber, String bio,
                                 StudentProfileEntity.ProfileStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.studentCode = studentCode;
        this.universityId = universityId;
        this.majorCode = majorCode;
        this.academicTerm = academicTerm;
        this.phoneNumber = phoneNumber;
        this.bio = bio;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
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

    public StudentProfileEntity.ProfileStatus getStatus() { return status; }
    public void setStatus(StudentProfileEntity.ProfileStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}