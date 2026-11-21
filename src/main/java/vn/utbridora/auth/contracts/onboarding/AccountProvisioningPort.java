package vn.utbridora.auth.contracts.onboarding;

import vn.utbridora.auth.contracts.ActorContext;

/** T1-only identity inspection for registered onboarding workflows, never a public email lookup. */
public interface AccountProvisioningPort {
    record AccountOnboardingRef(String userId,String status,boolean hasRequiredMembership,Long securityVersion) {}
    record RosterActivationProof(String claimId,String tokenHash) {}
    AccountOnboardingRef inspectForOnboarding(ActorContext actor,String emailCanonical,String scopeOrgId,String termId);
    /** Current token/claim proof is rechecked by T1; callers cannot authorize with a roster ID alone. */
    String requireRosterActivation(RosterActivationProof proof,String rosterId);
    void requireActivatedRosterIdentity(RosterActivationProof proof,String rosterId,String userId,String schoolOrgId);
    /** Trusted IDs-only event proof; no email/password is exposed to T2. */
    boolean hasActivatedRosterBinding(String userId,String rosterId,String schoolOrgId,long claimVersion);
}
