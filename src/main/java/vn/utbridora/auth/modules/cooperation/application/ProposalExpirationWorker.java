package vn.utbridora.auth.modules.cooperation.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SYSTEM workflow; each cooperation expires in its own owner transaction. */
@Component @org.springframework.scheduling.annotation.EnableScheduling
@ConditionalOnProperty(name="app.t5.proposal-expiration.enabled",havingValue="true",matchIfMissing=true)
public class ProposalExpirationWorker {
    private final JdbcTemplate jdbc;
    private final ProposalService proposals;
    public ProposalExpirationWorker(JdbcTemplate jdbc,ProposalService proposals) {this.jdbc=jdbc;this.proposals=proposals;}
    @Scheduled(initialDelay=30000,fixedDelay=30000)
    public void sweep() {
        var ids=jdbc.queryForList("SELECT DISTINCT b.cooperation_id FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id WHERE i.placement_id IS NULL AND i.state IN ('RESERVED','INVITED','ACCEPTED','SHARED','ENTERPRISE_ACCEPTED') AND i.reservation_until<=UTC_TIMESTAMP(6) ORDER BY b.cooperation_id LIMIT 50",String.class);
        for(String id:ids) try {proposals.expireReservations(id);}
        catch(RuntimeException retryNextTick) {
            org.slf4j.LoggerFactory.getLogger(ProposalExpirationWorker.class).debug("Reservation expiry deferred by concurrent owner workflow");
        }
    }
}
