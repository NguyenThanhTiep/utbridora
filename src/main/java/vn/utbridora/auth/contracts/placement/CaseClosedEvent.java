package vn.utbridora.auth.contracts.placement;
import java.time.Instant;
import java.util.Set;
import static vn.utbridora.auth.operations.WorkflowValues.*;
public record CaseClosedEvent(String eventId,String eventType,int schemaVersion,String caseId,String placementId,long aggregateVersion,long caseVersion,
    String decision,String decisionRef,String reasonCode,Instant closedAt,String traceId) {
    public CaseClosedEvent {
        uuid(eventId);uuid(caseId);uuid(placementId);uuid(traceId);
        if(aggregateVersion<1 || aggregateVersion>9_007_199_254_740_991L || caseVersion<1 || caseVersion>9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid closure versions");
        if(!"CaseClosed".equals(eventType) || schemaVersion!=1 || aggregateVersion!=caseVersion || !Set.of("NORMAL_COMPLETION","EARLY_TERMINATION").contains(decision==null?"":decision) || closedAt==null) throw new IllegalArgumentException("Invalid closure event");
        text(decisionRef,120);reason(reasonCode);
    }
}
