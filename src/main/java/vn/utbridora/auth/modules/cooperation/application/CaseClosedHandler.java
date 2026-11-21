package vn.utbridora.auth.modules.cooperation.application;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.authorization.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** T5 owns outcome mutation. Producer provenance, immutable proof and current school authority are independent gates. */
@Service
public class CaseClosedHandler implements CaseClosedConsumer {
    private final JdbcTemplate jdbc;private final InternshipCaseReadPort cases;private final PlacementReadPort source;
    private final AuthorizationPolicy core;private final SchoolDelegationAccess delegates;private final PlacementLifecyclePublisher publisher;
    private final TransactionTemplate transaction;
    public CaseClosedHandler(JdbcTemplate jdbc,InternshipCaseReadPort cases,PlacementReadPort source,AuthorizationPolicy core,SchoolDelegationAccess delegates,PlacementLifecyclePublisher publisher,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.cases=cases;this.source=source;this.core=core;this.delegates=delegates;this.publisher=publisher;transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override public EventConsumeResult consume(TrustedEventContext context,CaseClosedEvent event) {
        Objects.requireNonNull(context);Objects.requireNonNull(event);
        try {return transaction.execute(status->apply(context,event));}
        catch(RuntimeException unavailable) {return new EventConsumeResult(event.eventId(),"RETRY",null,"SOURCE_UNAVAILABLE",1);}
    }
    private EventConsumeResult apply(TrustedEventContext context,CaseClosedEvent event) {
        if(!cases.authenticDelivery(context,event)) return result(event,"REJECT",null,"INVALID_SCHEMA");
        var ids=jdbc.queryForList("SELECT demand_id FROM placements WHERE id=?",String.class,event.placementId());
        if(ids.isEmpty()) return new EventConsumeResult(event.eventId(),"RETRY",null,"SOURCE_NOT_FOUND",1);
        // Same Demand -> Placement -> Case order as the existing T5 capacity/lifecycle fence.
        jdbc.queryForObject("SELECT id FROM demands WHERE id=? FOR UPDATE",String.class,ids.getFirst());
        jdbc.queryForObject("SELECT id FROM placements WHERE id=? FOR UPDATE",String.class,event.placementId());
        var p=source.readCurrentForGuard(event.placementId()).orElseThrow();
        var inbox=jdbc.queryForList("SELECT placement_version FROM case_closed_inbox WHERE event_id=? OR (case_id=? AND case_version=?)",Long.class,event.eventId(),event.caseId(),event.caseVersion());
        if(!inbox.isEmpty()) return result(event,"ACK_DUPLICATE",inbox.getFirst(),"DUPLICATE");
        var proof=cases.proofForDelivery(context,event);
        if(!proof.placementId().equals(p.placementId()) || !proof.schoolOrgId().equals(p.schoolOrgId()) || !proof.termId().equals(p.termId())) return result(event,"REJECT",null,"SCOPE_MISMATCH");
        var auth=core.currentSubject(proof.decisionActorUserId()).orElseThrow();
        delegates.require(auth,proof.schoolOrgId(),proof.termId(),"t3:cases:close");
        boolean normal=proof.decision().equals("NORMAL_COMPLETION");String target=normal?"COMPLETED":"CANCELLED";
        if(normal && (!proof.state().equals("COMPLETED") || !proof.endDateReached() || !proof.allTasksCompleted() || !proof.allReportsReviewed() || !proof.evaluationPublished() || LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(p.endDate()))) return result(event,"REJECT",null,"STATE_CONFLICT");
        if(!normal && !proof.state().equals("TERMINATED")) return result(event,"REJECT",null,"STATE_CONFLICT");
        boolean already=p.state().equals(target);
        if(!already && (!Set.of("APPROVED","ACTIVE").contains(p.state()) || normal && !p.state().equals("ACTIVE"))) return result(event,"REJECT",null,"STATE_CONFLICT");
        long version=p.version();
        if(!already) {
            version(p.version());version++;
            jdbc.update("UPDATE placements SET state=?,version=?,decision_ref=?,completed_at=IF(?='COMPLETED',UTC_TIMESTAMP(6),completed_at),cancelled_at=IF(?='CANCELLED',UTC_TIMESTAMP(6),cancelled_at) WHERE id=?",target,version,proof.decisionRef(),target,target,p.placementId());
            jdbc.update("INSERT INTO placement_history(id,placement_id,from_state,to_state,actor_id,reason_code,decision_ref,operation_id,actor_kind,changed_at) VALUES(?,?,?,?,?,?,?,?, 'USER',UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),p.placementId(),p.state(),target,proof.decisionActorUserId(),proof.reasonCode(),proof.decisionRef(),context.deliveryOperationId());
            publisher.publishClosure(proof);
        }
        jdbc.update("INSERT INTO case_closed_inbox(event_id,case_id,case_version,placement_id,placement_version,outcome,applied_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",event.eventId(),event.caseId(),event.caseVersion(),event.placementId(),version,already?"ACK_STALE":"ACK_APPLIED");
        return result(event,already?"ACK_STALE":"ACK_APPLIED",version,already?"STALE":"APPLIED");
    }
    private static EventConsumeResult result(CaseClosedEvent e,String outcome,Long version,String reason) {return new EventConsumeResult(e.eventId(),outcome,version,reason,null);}
}
