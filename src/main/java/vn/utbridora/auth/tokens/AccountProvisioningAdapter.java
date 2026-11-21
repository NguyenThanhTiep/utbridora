package vn.utbridora.auth.tokens;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.onboarding.AccountProvisioningPort;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class AccountProvisioningAdapter implements AccountProvisioningPort {
    private final JdbcTemplate jdbc;
    private final SchoolDelegationAccess access;
    public AccountProvisioningAdapter(JdbcTemplate jdbc,SchoolDelegationAccess access) {this.jdbc=jdbc;this.access=access;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public AccountOnboardingRef inspectForOnboarding(ActorContext actor,String email,String school,String term) {
        access.require(actor.authentication(),uuid(school),uuid(term),"schoolRosterImport:manage");
        return jdbc.query("SELECT id,status,security_version FROM users WHERE email=? FOR UPDATE",(r,n)->{
            String user=r.getString("id");
            boolean membership=jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=? AND organization_id=? AND role='STUDENT' AND active=true",Integer.class,user,school)>0;
            return new AccountOnboardingRef(user,r.getString("status"),membership,r.getLong("security_version"));
        },email).stream().findFirst().orElse(new AccountOnboardingRef(null,null,false,null));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public String requireRosterActivation(RosterActivationProof proof,String roster) {
        if(proof==null || proof.tokenHash()==null || !proof.tokenHash().matches("[a-f0-9]{64}")) throw failure(422,"TOKEN_INVALID");
        var values=jdbc.queryForList("SELECT c.email_canonical FROM invitation_claims c JOIN one_time_tokens t ON t.id=c.current_token_id AND t.claim_id=c.id AND t.generation=c.generation WHERE c.id=? AND c.roster_id=? AND c.context_kind='ROSTER' AND c.claim_state='PENDING' AND t.token_hash=? AND t.purpose='INVITATION' AND t.consumed_at IS NULL AND t.revoked_at IS NULL AND t.expires_at>UTC_TIMESTAMP(6)",String.class,uuid(proof.claimId()),uuid(roster),proof.tokenHash());
        if(values.size()!=1) throw failure(409,"ACTIVATION_NOT_PENDING");
        return values.getFirst();
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void requireActivatedRosterIdentity(RosterActivationProof proof,String roster,String user,String school) {
        String email=requireRosterActivation(proof,roster);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN memberships m ON m.user_id=u.id WHERE u.id=? AND u.email=? AND u.status='ACTIVE' AND m.role='STUDENT' AND m.active=true AND m.organization_id=?",Integer.class,uuid(user),email,uuid(school))!=1) throw failure(409,"ACTIVATION_NOT_PENDING");
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public boolean hasActivatedRosterBinding(String user,String roster,String school,long version) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM invitation_claims WHERE user_id=? AND roster_id=? AND school_org_id=? AND context_kind='ROSTER' AND claim_state='ACTIVATED' AND version>=?",Integer.class,uuid(user),uuid(roster),uuid(school),version)==1;
    }
}
