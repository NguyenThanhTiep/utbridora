package vn.utbridora.auth.modules.internship.infrastructure;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.utbridora.auth.contracts.placement.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;
@Component
public class CaseClosureOutboxWorker {
    private final JdbcTemplate jdbc;private final CaseClosedConsumer consumer;private final boolean enabled;
    public CaseClosureOutboxWorker(JdbcTemplate jdbc,CaseClosedConsumer consumer,@Value("${app.t3.case-closure-delivery.enabled:false}") boolean enabled) {this.jdbc=jdbc;this.consumer=consumer;this.enabled=enabled;}
    @Scheduled(fixedDelay=5000,initialDelay=5000) public void tick() {if(enabled) deliverPending();}
    public void deliverPending() {
        for(var row:jdbc.queryForList("SELECT * FROM internship_case_closed_outbox WHERE state='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(6) ORDER BY next_attempt_at,event_id LIMIT 25")) {
            String id=row.get("event_id").toString();int attempts=((Number)row.get("attempts")).intValue();EventConsumeResult result;
            try {var event=JSON.readValue(row.get("payload_json").toString(),CaseClosedEvent.class);
                if(!id.equals(event.eventId())) throw new IllegalArgumentException("Delivery identity mismatch");
                result=consumer.consume(new TrustedEventContext("T3",row.get("operation_id").toString(),event.traceId()),event);
                if(result==null || !id.equals(result.eventId()) || result.outcome().startsWith("ACK_") && result.appliedAggregateVersion()==null) result=new EventConsumeResult(id,"RETRY",null,"SOURCE_UNAVAILABLE",1);
            } catch(tools.jackson.core.JacksonException|IllegalArgumentException invalid) {result=new EventConsumeResult(id,"REJECT",null,"INVALID_SCHEMA",null);}
            catch(RuntimeException unavailable) {result=new EventConsumeResult(id,"RETRY",null,"SOURCE_UNAVAILABLE",1);}
            if(result.outcome().startsWith("ACK_")) jdbc.update("UPDATE internship_case_closed_outbox SET state='DONE',last_reason=? WHERE event_id=? AND state='PENDING'",result.reasonCode(),id);
            else if(result.outcome().equals("REJECT") || attempts>=5) jdbc.update("UPDATE internship_case_closed_outbox SET state='DLQ',last_reason=? WHERE event_id=? AND state='PENDING'",result.reasonCode(),id);
            else jdbc.update("UPDATE internship_case_closed_outbox SET attempts=attempts+1,next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL ? SECOND),last_reason=? WHERE event_id=? AND state='PENDING' AND attempts=?",new int[]{1,5,30,300,1800}[attempts],result.reasonCode(),id,attempts);
        }
    }
}
