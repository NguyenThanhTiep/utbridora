package vn.utbridora.auth.modules.cooperation.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.contracts.placement.*;

/** T5 owner hook: called in the SAME transaction as the authorized Placement mutation.
 * It is not a public state-change API and does not implement the later approval workflow.
 */
@Service
public class PlacementLifecyclePublisher {
    private final PlacementReadPort source;private final JdbcTemplate jdbc;private final AuditService audit;
    private final JsonMapper json=JsonMapper.builder().build();
    public PlacementLifecyclePublisher(PlacementReadPort source,JdbcTemplate jdbc,AuditService audit) {this.source=source;this.jdbc=jdbc;this.audit=audit;}
    @Transactional(propagation=Propagation.MANDATORY)
    public PlacementLifecycleEvent publish(String actor,String placementId) {
        return publish(actor,placementId,null);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public PlacementLifecycleEvent publish(String actor,String placementId,String reason) {
        var p=source.readCurrentForGuard(placementId).orElseThrow();
        String type=switch(p.state()) {case "APPROVED"->"PlacementApproved";case "ACTIVE"->"PlacementActivated";
            case "CANCELLED"->"PlacementCancelled";default->throw new IllegalStateException("State requires its authorized workflow and closure proof");};
        if(p.state().equals("CANCELLED") && (reason==null || !reason.matches("[A-Z0-9_]{3,64}"))) throw new IllegalArgumentException("Cancellation reason required");
        var old=jdbc.queryForList("SELECT payload_json FROM placement_outbox WHERE placement_id=? AND aggregate_version=? AND event_type=?",String.class,placementId,p.version(),type);
        if(!old.isEmpty()) return json.readValue(old.getFirst(),PlacementLifecycleEvent.class);
        var now=Clock.systemUTC().instant();
        String operation=UUID.randomUUID().toString();
        var event=new PlacementLifecycleEvent(UUID.randomUUID().toString(),type,1,placementId,p.version(),p.version(),
            p.studentUserId(),p.schoolOrgId(),p.enterpriseOrgId(),p.termId(),p.effectiveAt(),now,UUID.randomUUID().toString(),reason,p.decisionRef());
        jdbc.update("INSERT INTO placement_outbox(event_id,delivery_operation_id,placement_id,aggregate_version,event_type,payload_json,next_attempt_at,created_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            event.eventId(),operation,placementId,p.version(),type,json.writeValueAsString(event));
        audit.placementPublished(actor,p,operation,reason);
        return event;
    }
    /** Only the T5 closure consumer calls this after verifying the current T3 proof and authority. */
    @Transactional(propagation=Propagation.MANDATORY)
    public PlacementLifecycleEvent publishClosure(InternshipCaseReadPort.CaseClosureProof proof) {
        if(proof.decision().equals("EARLY_TERMINATION")) return publish(proof.decisionActorUserId(),proof.placementId(),proof.reasonCode());
        var p=source.readCurrentForGuard(proof.placementId()).orElseThrow();
        if(!p.state().equals("COMPLETED") || !proof.state().equals("COMPLETED") || !proof.endDateReached() || !proof.allTasksCompleted() || !proof.allReportsReviewed() || !proof.evaluationPublished()) throw new IllegalStateException("Closure proof required");
        var old=jdbc.queryForList("SELECT payload_json FROM placement_outbox WHERE placement_id=? AND aggregate_version=? AND event_type='PlacementCompleted'",String.class,p.placementId(),p.version());
        if(!old.isEmpty()) return json.readValue(old.getFirst(),PlacementLifecycleEvent.class);
        String operation=UUID.randomUUID().toString();
        var event=new PlacementLifecycleEvent(UUID.randomUUID().toString(),"PlacementCompleted",1,p.placementId(),p.version(),p.version(),p.studentUserId(),p.schoolOrgId(),p.enterpriseOrgId(),p.termId(),p.effectiveAt(),Clock.systemUTC().instant(),UUID.randomUUID().toString(),proof.reasonCode(),proof.decisionRef());
        jdbc.update("INSERT INTO placement_outbox(event_id,delivery_operation_id,placement_id,aggregate_version,event_type,payload_json,next_attempt_at,created_at) VALUES(?,?,?,?,? ,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",event.eventId(),operation,p.placementId(),p.version(),event.eventType(),json.writeValueAsString(event));
        audit.workflow(proof.decisionActorUserId(),"PLACEMENT_COMPLETED",p.placementId(),p.schoolOrgId(),java.util.Map.of("termId",p.termId(),"placementVersion",p.version(),"caseId",proof.caseId(),"caseVersion",proof.caseVersion(),"decisionRef",proof.decisionRef(),"operationId",operation));
        return event;
    }
}
