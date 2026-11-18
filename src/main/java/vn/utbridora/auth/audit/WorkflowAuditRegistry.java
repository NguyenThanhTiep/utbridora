package vn.utbridora.auth.audit;

import java.util.Map;
import java.util.Set;

/** Exact E registry subset used by implemented workflow producers. */
final class WorkflowAuditRegistry {
    record Spec(String resourceType,Set<String> fields) {}
    static final Map<String,Spec> ACTIONS=Map.ofEntries(
        Map.entry("FILE_UPLOADED",new Spec("file",Set.of("purpose","contentType","sizeBytes","scanStatus"))),
        Map.entry("FILE_SCAN_COMPLETED",new Spec("file",Set.of("scanStatus","attemptNo","reasonCode"))),
        Map.entry("ACTIVATION_INVITATION_REISSUED",new Spec("account_invitation",Set.of("rowId","operationId","reasonCode","expiresAt"))),
        Map.entry("INTERNSHIP_INVITATION_EXPIRED",new Spec("internship_invitation",Set.of("version","expiredAt"))),
        Map.entry("FILE_DOWNLOADED",new Spec("file",Set.of("purpose","resourceType","resourceId","rangeRequest","consentVersion","shareVersion"))),
        Map.entry("CAPABILITY_GRANTED",new Spec("capability_assignment",Set.of("permissionCode","scopeKind","scopeId","termId","assignmentKind","validFrom","validUntil","securityVersion","decisionRef"))),
        Map.entry("CAPABILITY_REVOKED",new Spec("capability_assignment",Set.of("permissionCode","scopeKind","scopeId","termId","assignmentKind","securityVersion","reasonCode"))),
        Map.entry("CONSENT_GRANTED",new Spec("student_consent",Set.of("recipientOrgId","schoolOrgId","termId","purpose","fieldCodes","consentVersion","expiresAt"))),
        Map.entry("CONSENT_REVOKED",new Spec("student_consent",Set.of("consentVersion","reasonCode"))),
        Map.entry("PREFERENCE_SUBMITTED",new Spec("student_preference",Set.of("opportunityId","termId","version"))),
        Map.entry("PREFERENCE_WITHDRAWN",new Spec("student_preference",Set.of("version","reasonCode"))),
        Map.entry("PREFERENCE_REVIEWED",new Spec("student_preference",Set.of("version","decision","decisionRef"))),
        Map.entry("STUDENT_ELIGIBILITY_CHANGED",new Spec("student_term_eligibility",Set.of("schoolOrgId","termId","fromApproved","toApproved","version","decisionRef"))),
        Map.entry("SCHOOL_PROFILE_SHARING_POLICY_CHANGED",new Spec("school_sharing_policy",Set.of("schoolOrgId","termId","fieldCodes","version"))),
        Map.entry("COOPERATION_DRAFT_SAVED",new Spec("cooperation",Set.of("operation","version"))),
        Map.entry("COOPERATION_SUBMITTED",new Spec("cooperation",Set.of("submissionNo","demandVersion","version"))),
        Map.entry("COOPERATION_REVIEWED",new Spec("cooperation",Set.of("decision","submissionNo","version","reasonCode"))),
        Map.entry("PROPOSAL_ITEM_RESERVED",new Spec("proposal_item",Set.of("batchId","reservationUntil","version","operationId"))),
        Map.entry("PROPOSAL_ITEM_DECIDED",new Spec("proposal_item",Set.of("fromStatus","toStatus","version","reasonCode","operationId"))),
        Map.entry("OPPORTUNITY_PUBLICATION_CHANGED",new Spec("cooperation",Set.of("publishedAt","closedAt","version","operation"))),
        Map.entry("INTERNSHIP_INVITATION_ISSUED",new Spec("internship_invitation",Set.of("proposalItemId","expiresAt","version","operationId"))),
        Map.entry("INVITATION_RESPONDED",new Spec("internship_invitation",Set.of("decision","invitationVersion","operationId"))),
        Map.entry("INTERNSHIP_INVITATION_REVOKED",new Spec("internship_invitation",Set.of("version","reasonCode"))),
        Map.entry("TASK_UPDATED",new Spec("task_assignment",Set.of("taskId","caseId","fromStatus","toStatus","assignmentVersion"))),
        Map.entry("TASK_COMMENTED",new Spec("task_assignment",Set.of("commentId","assignmentVersion"))),
        Map.entry("WEEKLY_REPORT_DRAFT_SAVED",new Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","reportVersion"))),
        Map.entry("WEEKLY_REPORT_REVISION_CREATED",new Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","reportVersion","reasonCode"))),
        Map.entry("WEEKLY_REPORT_SUBMITTED",new Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","lateFlag"))),
        Map.entry("WEEKLY_REPORT_REVIEWED",new Spec("weekly_report",Set.of("caseId","weekStart","revisionNo","reportVersion"))),
        Map.entry("SUBMISSION_CREATED",new Spec("submission",Set.of("assignmentId","versionNo","fileId","fileIds","lateFlag"))),
        Map.entry("SUBMISSION_REVISED",new Spec("submission",Set.of("assignmentId","versionNo","previousSubmissionId","fileId","fileIds","lateFlag"))),
        Map.entry("SUBMISSION_REVIEWED",new Spec("submission",Set.of("assignmentId","reviewedVersion","verdict","assignmentVersion"))),
        Map.entry("MATCH_RUN_REQUESTED",new Spec("match_run",Set.of("demandId","criteriaHash","criteriaVersion","ruleVersion","taxonomyVersion"))),
        Map.entry("MATCH_RUN_COMPLETED",new Spec("match_run",Set.of("demandId","runVersion","status","ruleVersion"))),
        Map.entry("MATCH_DETAILS_VIEWED",new Spec("match_run",Set.of("schoolOrgId","termId","page","size"))),
        Map.entry("PROFILE_SHARED",new Spec("profile_share",Set.of("consentId","consentVersion","shareVersion","fieldCodes","expiresAt"))),
        Map.entry("PROFILE_SHARE_REVOKED",new Spec("profile_share",Set.of("shareVersion","reasonCode"))),
        Map.entry("STUDENT_PROFILE_ACCESSED",new Spec("student_profile",Set.of("accessPurpose","fieldCodes","consentId","consentVersion","shareId","shareVersion"))),
        // DEV08 bổ sung: metadata được cho phép khi tạo release; không gồm fingerprint hoặc số đếm nguồn.
        Map.entry("PRIVACY_RELEASE_CREATED",new Spec("privacy_release",Set.of("enterpriseOrgId","schoolOrgId","termId","demoBatchId","eligibleBand"))),
        // DEV08 bổ sung: metadata được cho phép khi chặn release thứ hai; không gồm bộ lọc yêu cầu hoặc số đếm.
        Map.entry("PRIVACY_RELEASE_BLOCKED",new Spec("privacy_release",Set.of("enterpriseOrgId","schoolOrgId","termId","demoBatchId","reasonCode"))),
        Map.entry("MENTOR_INVITATION_ACCEPTED",new Spec("account_invitation",Set.of("enterpriseOrgId","assignmentId","operationId"))),
        Map.entry("ACCOUNT_INVITATION_REVOKED",new Spec("account_invitation",Set.of("purpose","reasonCode","operationId"))),
        Map.entry("PLACEMENT_CREATED",new Spec("placement",Set.of("termId","placementVersion","decisionRef","effectiveAt","reasonCode","operationId"))),
        Map.entry("PLACEMENT_APPROVED",new Spec("placement",Set.of("termId","placementVersion","decisionRef","effectiveAt","reasonCode","operationId"))),
        Map.entry("PLACEMENT_ACTIVATED",new Spec("placement",Set.of("termId","placementVersion","decisionRef","effectiveAt","reasonCode","operationId"))),
        Map.entry("PLACEMENT_CANCELLED",new Spec("placement",Set.of("termId","placementVersion","decisionRef","effectiveAt","reasonCode","operationId")))
    );
}
