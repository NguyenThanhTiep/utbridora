package vn.utbridora.auth.modules.cooperation.port;

import java.time.Instant;
import java.util.List;
import vn.utbridora.auth.contracts.ActorContext;

/** T5 owns share state; the access facade consumes this internal snapshot, never profile storage. */
public interface ProfileShareReadPort {
    record CurrentShare(String shareId,long shareVersion,String consentId,long consentVersion,
                        String studentUserId,String schoolOrgId,String termId,String recipientOrgId,
                        String purpose,List<String> fieldWhitelist,Instant validFrom,Instant expiresAt,Instant revokedAt) {}
    CurrentShare readCurrent(ActorContext actor,String shareId);
}
