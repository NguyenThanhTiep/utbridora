package vn.utbridora.auth.modules.cooperation.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

/** UTC due scan; the owner service rechecks every source guard under lock. */
@Component @EnableScheduling
@ConditionalOnProperty(name="app.t5.placement-activation.enabled",havingValue="true",matchIfMissing=true)
public class PlacementActivationWorker {
    private final JdbcTemplate jdbc;
    private final PlacementService placements;
    public PlacementActivationWorker(JdbcTemplate jdbc,PlacementService placements) {this.jdbc=jdbc;this.placements=placements;}
    @Scheduled(fixedDelayString="${app.t5.placement-activation.delay-ms:5000}",initialDelay=5000)
    public void activatePending() {
        for(var row:jdbc.queryForList("SELECT id,version FROM placements WHERE state='APPROVED' AND effective_at<=UTC_TIMESTAMP(6) ORDER BY effective_at,id LIMIT 25")) {
            try {placements.activateDue((String)row.get("id"),((Number)row.get("version")).longValue());}
            catch(RuntimeException blocked) {
                // Each transaction rolls back independently; future ticks re-read source. Never log PII/payloads.
                org.slf4j.LoggerFactory.getLogger(PlacementActivationWorker.class).debug("Placement activation deferred by current guard");
            }
        }
    }
}
