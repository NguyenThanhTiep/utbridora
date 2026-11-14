package vn.utbridora.auth.modules.internship.api;

import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.EvaluationDto;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS4 T3-11 wire values; missing policies use version zero. */
public final class GradeDtos {
    private GradeDtos() {}
    public record PublishEvaluation(Long expectedVersion, String decisionRef) {
        public PublishEvaluation { version(expectedVersion); decisionRef=reference(decisionRef); }
    }
    public record CreateEvaluationRevision(Long expectedVersion, String decisionRef, String reasonCode) {
        public CreateEvaluationRevision { version(expectedVersion); decisionRef=reference(decisionRef); reasonCode=reason(reasonCode); }
    }
    public record SetVisibility(Long expectedVersion, Boolean enabled) {
        public SetVisibility {
            if(expectedVersion==null || expectedVersion<0 || expectedVersion>9_007_199_254_740_991L || enabled==null) throw failure(400,"SCHEMA_VALIDATION");
            if(expectedVersion==9_007_199_254_740_991L) throw failure(409,"VERSION_CONFLICT");
        }
    }
    public record SchoolResultVisibilityDto(UUID schoolOrgId,UUID termId,boolean enabled,long version) {}
    public enum VisibilityScope { ORGANIZATION_TERM, CASE }
    public record GradeVisibilityDto(UUID enterpriseOrgId,UUID termId,UUID caseId,VisibilityScope scope,boolean enabled,long version) {}
    public enum Visibility { VISIBLE, HIDDEN }
    public enum ResultReason { VISIBLE, NOT_AVAILABLE }
    public record StudentResultDto(UUID caseId,Visibility visibility,EvaluationDto evaluation,ResultReason reasonCode) {
        public static StudentResultDto hidden(UUID id){return new StudentResultDto(id,Visibility.HIDDEN,null,ResultReason.NOT_AVAILABLE);}
    }
    private static String reference(String value) {
        if(value==null) throw failure(400,"SCHEMA_VALIDATION");
        value=value.replaceAll("(?U)^\\s+|\\s+$","");
        if(value.isEmpty() || value.codePointCount(0,value.length())>120) throw failure(400,"SCHEMA_VALIDATION");
        return value;
    }
}
