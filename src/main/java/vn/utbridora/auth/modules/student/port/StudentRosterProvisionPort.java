package vn.utbridora.auth.modules.student.port;

import java.util.List;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.onboarding.AccountProvisioningPort.RosterActivationProof;

/** T2 owner boundary for CP-IMPORT1. Every mutation joins the orchestrator's transaction. */
public interface StudentRosterProvisionPort {
    record EnsureRoster(String schoolOrgId,String studentCode,String termId,String majorCode,String emailCanonical,
        String fullName,String cohortCode,String classCode,String expectedCatalogVersion,String operationId) {}
    record RosterProvisionResult(String rosterId,long rosterVersion,String status,String userId,String reasonCode) {}
    record RosterForActivation(String rosterId,long rosterVersion,String schoolOrgId,String studentCode,
        String emailCanonical,String userId,List<String> termIds) {}
    record BindActivatedUser(String rosterId,String userId,long expectedRosterVersion,String operationId) {}
    record BindResult(String rosterId,String userId,long rosterVersion,List<String> linkedTermIds,boolean profileCreated) {}
    RosterProvisionResult ensureRoster(ActorContext actor,EnsureRoster input);
    /** Preview classification only: no roster, term, profile or eligibility is inserted. */
    RosterProvisionResult inspectRoster(ActorContext actor,EnsureRoster input);
    RosterForActivation readForActivation(RosterActivationProof proof,String rosterId);
    BindResult bindActivatedUser(RosterActivationProof proof,BindActivatedUser input);
}
