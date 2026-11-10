package vn.utbridora.auth.modules.internship.integration;

import java.util.UUID;

/** Case owner performs the CAS; the workspace adapter never writes the Case repository. */
public interface CaseMembershipPort {
    long advanceMembershipVersion(UUID caseId,long expectedVersion);
}
