package vn.utbridora.auth.modules.student.port;

import java.time.Instant;
import java.util.List;

/** T2 consumes only a notification of the atomic T1/T2 binding; never creates a profile here. */
public interface StudentActivationEventPort {
    record StudentAccountActivatedEvent(String eventId,String eventType,int schemaVersion,String aggregateId,long aggregateVersion,
        String userId,String rosterId,long rosterVersion,String schoolOrgId,List<String> termIds,Instant occurredAt,String traceId) {}
    String reconcile(StudentAccountActivatedEvent event);
}
