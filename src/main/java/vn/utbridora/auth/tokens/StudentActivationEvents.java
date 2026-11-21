package vn.utbridora.auth.tokens;

import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import vn.utbridora.auth.modules.student.port.StudentActivationEventPort;
import vn.utbridora.auth.modules.student.port.StudentActivationEventPort.StudentAccountActivatedEvent;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** In-process durable handoff. A separate consumer TX permits ACK replay after dispatcher failure. */
@Service
public class StudentActivationEvents {
    private final JdbcTemplate jdbc;private final StudentActivationEventPort consumer;private final Clock clock;private final TransactionTemplate delivery;
    public StudentActivationEvents(JdbcTemplate jdbc,StudentActivationEventPort consumer,@Qualifier("onboardingClock") Clock clock,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.consumer=consumer;this.clock=clock;delivery=new TransactionTemplate(manager);
        delivery.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Transactional public boolean dispatchOne() {
        var rows=jdbc.queryForList("SELECT * FROM student_activation_outbox WHERE state='PENDING' AND (next_attempt_at IS NULL OR next_attempt_at<=?) ORDER BY event_id LIMIT 1 FOR UPDATE SKIP LOCKED",utc(clock.instant()));
        if(rows.isEmpty()) return false;var row=rows.getFirst();
        try {
            delivery.execute(status->consumer.reconcile(JSON.readValue(row.get("payload").toString(),StudentAccountActivatedEvent.class)));
            jdbc.update("UPDATE student_activation_outbox SET state='ACKED',next_attempt_at=NULL WHERE event_id=?",row.get("event_id"));
        } catch(RuntimeException unavailable) {
            int attempts=((Number)row.get("attempts")).intValue()+1;
            jdbc.update("UPDATE student_activation_outbox SET attempts=?,state=?,next_attempt_at=? WHERE event_id=?",attempts,attempts>5?"DLQ":"PENDING",attempts>5?null:utc(clock.instant().plusSeconds(new int[]{1,5,30,300,1800}[attempts-1])),row.get("event_id"));
        }
        return true;
    }
}
