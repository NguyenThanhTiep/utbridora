package vn.utbridora.auth.contracts.placement;
import java.time.Instant;
import java.util.UUID;
import vn.utbridora.auth.contracts.ActorContext;
public interface InternshipCaseReadPort {
    record CaseClosureProof(String caseId,String placementId,long caseVersion,String state,String decision,String decisionRef,String reasonCode,
        String decisionActorUserId,String schoolOrgId,String termId,Instant closedAt,boolean endDateReached,boolean allTasksCompleted,boolean allReportsReviewed,boolean evaluationPublished) {}
    CaseClosureProof readClosureProof(ActorContext actor,UUID caseId);
    boolean authenticDelivery(TrustedEventContext context,CaseClosedEvent event);
    /** Trusted in-process delivery only; provider revalidates stored proof and original school authority. */
    CaseClosureProof proofForDelivery(TrustedEventContext context,CaseClosedEvent event);
}
