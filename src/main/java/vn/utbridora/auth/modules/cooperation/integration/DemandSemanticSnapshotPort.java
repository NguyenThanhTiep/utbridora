package vn.utbridora.auth.modules.cooperation.integration;

import java.time.Instant;
import org.springframework.security.core.Authentication;

/** T5 owns demand text, source current checks and deletion. No T4 live-table join. */
public interface DemandSemanticSnapshotPort {
    record Snapshot(String id,String demandId,String schoolOrgId,String termId,long demandVersion,long criteriaVersion,String criteriaHash,String title,String description,String semanticSourceHash,Instant capturedAt,Instant validUntil) {}
    Snapshot capture(Authentication auth,String demand,long demandVersion,long criteriaVersion,String criteriaHash,Instant until);
    Snapshot read(Authentication auth,String id);
    boolean current(Authentication auth,String id);
    void purge(String id);
}
