package vn.utbridora.auth.modules.cooperation.integration;

import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.files.FileResourcePolicy;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** T5's current draft/organization guard; school/shared evidence reads require a later explicit adapter. */
@Component
public class VerificationFilePolicy implements FileResourcePolicy {
    private record Verification(String enterprise,String state,long version) {}
    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    public VerificationFilePolicy(JdbcTemplate jdbc,AuthorizationPolicy core) {this.jdbc=jdbc;this.core=core;}
    @Override public ResourceType resourceType() {return ResourceType.ENTERPRISE_VERIFICATION;}
    @Override public Grant check(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant now) {
        if(purpose!=FilePurpose.VERIFICATION) return Grant.deny();
        var row=jdbc.query("SELECT enterprise_org_id,state,version FROM enterprise_verifications WHERE id=? FOR SHARE",
            (r,n)->new Verification(r.getString(1),r.getString(2),r.getLong(3)),resource.resourceId()).stream().findFirst().orElse(null);
        if(row==null || !core.holdOrganization(actor.authentication(),row.enterprise(),Role.ENTERPRISE,"enterprise_verification:enterprise:manage")) return Grant.deny();
        if(action==Action.UPLOAD && !Set.of("DRAFT","NEEDS_CHANGES").contains(row.state())) throw failure(409,"STATE_CONFLICT");
        return Grant.owner(row.version(),row.enterprise(),true);
    }
}
