package vn.utbridora.auth.modules.cooperation.application;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;

/** At-least-once internal delivery. Consumer commit precedes ACK; crash replay is deduplicated. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.t5.placement-delivery.enabled",havingValue="true",matchIfMissing=true)
public class PlacementOutboxWorker {
    private static final int[] RETRY_DELAYS={1,5,30,300,1800};
    private final JdbcTemplate jdbc;private final PlacementLifecycleConsumer consumer;
    private final JsonMapper json=JsonMapper.builder().build();
    public PlacementOutboxWorker(JdbcTemplate jdbc,PlacementLifecycleConsumer consumer) {this.jdbc=jdbc;this.consumer=consumer;}
    @Scheduled(fixedDelayString="${app.t5.placement-delivery.delay-ms:5000}",initialDelay=5000)
    public void deliverPending() {
        for(var row:jdbc.queryForList("SELECT event_id,delivery_operation_id,payload_json,attempt_count FROM placement_outbox WHERE state='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY next_attempt_at,event_id LIMIT 25")) {
            String id=(String)row.get("event_id");int retries=((Number)row.get("attempt_count")).intValue();
            EventConsumeResult result;
            try {
                var event=json.readValue((String)row.get("payload_json"),PlacementLifecycleEvent.class);
                result=consumer.consume(new TrustedEventContext("T5",(String)row.get("delivery_operation_id"),event.traceId()),event);
            } catch(IllegalArgumentException invalid) {result=new EventConsumeResult(id,"REJECT",null,"INVALID_SCHEMA",null);}
            catch(RuntimeException unavailable) {result=new EventConsumeResult(id,"RETRY",null,"SOURCE_UNAVAILABLE",1);}
            if(result.outcome().startsWith("ACK_")) jdbc.update("UPDATE placement_outbox SET state='DONE',last_reason=? WHERE event_id=? AND state='PENDING'",result.reasonCode(),id);
            else if(result.outcome().equals("REJECT") || retries>=RETRY_DELAYS.length)
                jdbc.update("UPDATE placement_outbox SET state='DLQ',last_reason=? WHERE event_id=? AND state='PENDING'",result.reasonCode(),id);
            else jdbc.update("UPDATE placement_outbox SET attempt_count=attempt_count+1,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ? SECOND),last_reason=? WHERE event_id=? AND state='PENDING' AND attempt_count=?",
                RETRY_DELAYS[retries],result.reasonCode(),id,retries);
        }
    }
}
