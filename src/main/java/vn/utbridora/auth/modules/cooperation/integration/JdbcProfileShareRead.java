package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.modules.cooperation.application.ProfileShareService.Share;
import vn.utbridora.auth.modules.cooperation.port.ProfileShareReadPort;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Component
public class JdbcProfileShareRead implements ProfileShareReadPort {
    private final JdbcTemplate jdbc;
    public JdbcProfileShareRead(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public CurrentShare readCurrent(ActorContext context,String id) {
        actor(context.authentication());
        var s=jdbc.query("SELECT data_json FROM profile_shares WHERE id=? FOR SHARE",
            (r,n)->JSON.readValue(r.getString(1),Share.class),uuid(id)).stream().findFirst()
            .orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        return new CurrentShare(s.shareId(),s.shareVersion(),s.consentId(),s.consentVersion(),s.studentUserId(),
            s.schoolOrgId(),s.termId(),s.recipientOrgId(),s.purpose(),s.fieldWhitelist(),s.validFrom(),s.expiresAt(),s.revokedAt());
    }
}
