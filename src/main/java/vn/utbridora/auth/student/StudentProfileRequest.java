package vn.utbridora.auth.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StudentProfileRequest {
    @NotBlank(message = "MSSV không được để trống")
    @Size(max = 50)
    private String studentCode;

    @NotBlank(message = "Mã trường không được để trống")
    private String universityId;

    @NotBlank(message = "Mã ngành không được để trống")
    private String majorCode;

    @NotBlank(message = "Kỳ học không được để trống")
    private String academicTerm;

    @Size(max = 20)
    private String phoneNumber;

    private String bio;

    public StudentProfileRequest() {}

    public StudentProfileRequest(String studentCode, String universityId, String majorCode,
                                String academicTerm, String phoneNumber, String bio) {
        this.studentCode = studentCode;
        this.universityId = universityId;
        this.majorCode = majorCode;
        this.academicTerm = academicTerm;
        this.phoneNumber = phoneNumber;
        this.bio = bio;
    }

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
}