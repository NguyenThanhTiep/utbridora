package vn.utbridora.auth.contracts.profile;

import java.time.Instant;
import java.util.List;
import vn.utbridora.auth.contracts.ActorContext;

/** BC24. Decisions are computed from current authority inside the caller's transaction. */
public interface ProfileAccessPort {
    record Query(String studentUserId,String termId,String shareId,List<String> requestedFields,String purpose) {
        public Query {requestedFields=List.copyOf(requestedFields);}
    }
    record Decision(boolean allowed,List<String> allowedFields,Instant validUntil,Long consentVersion,
                    Long shareVersion,long schoolPolicyVersion,String reasonCode) {
        public Decision {allowedFields=List.copyOf(allowedFields);}
    }
    Decision decide(ActorContext actor,Query request);
}
