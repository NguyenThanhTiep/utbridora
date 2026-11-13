package vn.utbridora.auth.modules.cooperation.application;

import java.util.List;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort;
import vn.utbridora.auth.modules.cooperation.port.ProfileShareReadPort;
import vn.utbridora.auth.modules.student.port.StudentProfileProjectionPort;

/** Keeps the proposal, share and consent checks in the same transaction as profile access. */
@Service
public class ProposalProfileService {
    public record SharedProfile(long profileVersion,List<String> allowedFields,Instant validUntil,StudentProfileProjectionPort.Data data) {}
    private final ProposalService proposals;
    private final ProfileShareReadPort shares;
    private final StudentProfileProjectionPort profiles;
    public ProposalProfileService(ProposalService proposals,ProfileShareReadPort shares,StudentProfileProjectionPort profiles) {this.proposals=proposals;this.shares=shares;this.profiles=profiles;}
    @Transactional public SharedProfile read(Authentication auth,String org,String id) {
        var target=proposals.sharedProfileTarget(auth,org,id);
        var item=target.item();
        var context=ActorContext.fromAuthenticatedWorkflow(auth,org);
        var grant=shares.readCurrent(context,item.shareId());
        var profile=profiles.readAllowed(context,new ProfileAccessPort.Query(item.studentUserId(),target.termId(),item.shareId(),grant.fieldWhitelist(),"INTERNSHIP_SELECTION"));
        return new SharedProfile(profile.profileVersion(),profile.allowedFields(),profile.validUntil(),profile.data());
    }
}
