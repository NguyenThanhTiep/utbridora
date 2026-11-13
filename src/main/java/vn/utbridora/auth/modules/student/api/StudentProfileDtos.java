package vn.utbridora.auth.modules.student.api;

import java.math.BigDecimal;
import java.util.List;

/** Wire shapes of BC11 (Contract Pack v1.0 lines 753-770) and the C.0 error envelope (lines 97-103). */
public final class StudentProfileDtos {
    private StudentProfileDtos() {}

    public record DateRange(String startDate, String endDate) {}
    public record CanonicalSkillInput(String skillCode, int level, BigDecimal yearsPractice, String note) {}
    public record SelfSkillView(String skillCode, int level, BigDecimal yearsPractice, String source, String note) {}
    public record CustomSkillInput(String customSkillId, String name, String categoryCode, int level, BigDecimal yearsPractice, String note) {}
    public record CustomSkillView(String customSkillId, String name, String categoryCode, int level, BigDecimal yearsPractice, String note) {}
    public record Project(String projectId, String title, String description, String role, List<String> skillCodes,
                          String url, List<String> evidenceFileIds) {}
    public record Certificate(String certificateId, String title, String issuer, String issuedOn, String expiresOn, String fileId) {}

    public record ProfileInput(long expectedProfileVersion, String termId, String majorCode,
                               boolean skillsDeclared, List<CanonicalSkillInput> skills, List<CustomSkillInput> customSkills,
                               boolean availabilityDeclared, List<DateRange> availablePeriods,
                               List<String> orientationCodes, List<String> subjectCodes, List<Project> projects,
                               List<Certificate> certificates, String cvFileId, String phone) {}
    public record SelfProfileData(String termId, String majorCode,
                                  boolean skillsDeclared, List<SelfSkillView> skills, List<CustomSkillView> customSkills,
                                  boolean availabilityDeclared, List<DateRange> availablePeriods,
                                  List<String> orientationCodes, List<String> subjectCodes, List<Project> projects,
                                  List<Certificate> certificates, String cvFileId, String phone) {}
    public record CompletenessCheck(String code, boolean passed, String reasonCode) {}
    public record CompletenessResult(String policyVersion, boolean complete, List<CompletenessCheck> checks) {}
    public record SelfProfileView(String studentUserId, String schoolOrgId, long profileVersion,
                                  SelfProfileData profile, CompletenessResult completeness) {}

    public record FieldErrorView(String field, String code, String message) {}
    public record ErrorEnvelope(String errorCode, String error, String message, String requestId,
                                List<FieldErrorView> fieldErrors, Integer retryAfterSeconds) {}
}
