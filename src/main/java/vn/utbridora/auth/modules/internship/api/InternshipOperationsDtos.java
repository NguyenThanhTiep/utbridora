package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

public final class InternshipOperationsDtos {
    private InternshipOperationsDtos() {}
    public record AssignmentCount(String state,long count) {}
    public record WorkspaceSummary(long cohortCount,long caseCount,long groupCount,long mentorCount,List<AssignmentCount> assignmentCounts,long lateAssignmentCount,long pendingReviewCount) {}
    public record CalendarEvent(UUID taskId,UUID assignmentId,String title,Instant dueAt,String state) {}
    public record EnterpriseQualityCriterion(String code,String label) {public EnterpriseQualityCriterion {text(code,64);text(label,200);}}
    public record EnterpriseQualityTemplate(UUID templateId,long version,List<EnterpriseQualityCriterion> criteria) {
        public EnterpriseQualityTemplate {if(templateId==null || criteria==null || criteria.isEmpty() || criteria.size()>100 || criteria.stream().anyMatch(Objects::isNull)) throw failure(400,"SCHEMA_VALIDATION");vn.utbridora.auth.operations.WorkflowValues.version(version);criteria=List.copyOf(criteria);if(criteria.stream().map(EnterpriseQualityCriterion::code).distinct().count()!=criteria.size()) throw failure(422,"ENTERPRISE_RUBRIC_INVALID");}
    }
    public record EnterpriseRating(String criterionCode,int stars,String comment) {
        public EnterpriseRating {text(criterionCode,64);if(stars<1 || stars>5 || comment!=null && comment.codePointCount(0,comment.length())>2000) throw failure(422,"ENTERPRISE_RUBRIC_INVALID");}
    }
    public record EnterpriseQualityInput(UUID enterpriseOrgId,UUID termId,UUID templateId,Long templateVersion,List<EnterpriseRating> ratings,String note) {
        public EnterpriseQualityInput {if(enterpriseOrgId==null || termId==null || templateId==null || ratings==null || ratings.isEmpty() || ratings.size()>100 || ratings.stream().anyMatch(Objects::isNull) || note!=null && note.codePointCount(0,note.length())>2000) throw failure(400,"SCHEMA_VALIDATION");version(templateVersion);ratings=List.copyOf(ratings);}
    }
    public record EnterpriseQualityDto(UUID id,EnterpriseQualityInput input,UUID schoolOrgId,long version,Instant createdAt) {}
    public enum IssueCategory {DELAY,COMMUNICATION,OTHER}
    public enum IssueState {OPEN,IN_PROGRESS,RESOLVED}
    public record CreateCoordinationIssue(UUID caseId,IssueCategory category,String description,UUID ownerUserId) {
        public CreateCoordinationIssue {if(caseId==null || category==null || ownerUserId==null) throw failure(400,"SCHEMA_VALIDATION");description=text(description,5000);}
    }
    public record UpdateCoordinationIssue(Long expectedVersion,IssueState state) {public UpdateCoordinationIssue {version(expectedVersion);if(state==null || state==IssueState.OPEN) throw failure(400,"SCHEMA_VALIDATION");}}
    public record CoordinationIssue(UUID issueId,UUID caseId,IssueCategory category,String description,IssueState state,UUID ownerUserId,long version,Instant createdAt,Instant updatedAt) {}
    public record CloseCase(Long expectedVersion,String decision,String decisionRef,String reasonCode) {
        public CloseCase {version(expectedVersion);if(!Set.of("NORMAL_COMPLETION","EARLY_TERMINATION").contains(decision==null?"":decision)) throw failure(400,"SCHEMA_VALIDATION");decisionRef=text(decisionRef,120);reason(reasonCode);}
    }
    public record HiringInterestInput(Long expectedVersion,Long expectedCaseVersion,Boolean interested,String note) {
        public HiringInterestInput {if(expectedVersion==null || expectedVersion<0 || expectedVersion>=9_007_199_254_740_991L || interested==null || note!=null && note.codePointCount(0,note.length())>2000) throw failure(400,"SCHEMA_VALIDATION");version(expectedCaseVersion);}
    }
    public record HiringInterestDto(UUID caseId,boolean interested,String note,long version,Instant updatedAt) {}
}
