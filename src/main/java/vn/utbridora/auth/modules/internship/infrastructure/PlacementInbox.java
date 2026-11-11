package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;

/** Inbox, current-source projection and technical audit commit together. No browser callback. */
@Service
public class PlacementInbox implements PlacementLifecycleConsumer {
    private final JdbcTemplate jdbc;private final PlacementReadPort source;
    private final TransactionTemplate transactions;
    public PlacementInbox(JdbcTemplate jdbc,PlacementReadPort source,PlatformTransactionManager manager) {
        this.jdbc=jdbc;this.source=source;this.transactions=new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Override
    public EventConsumeResult consume(TrustedEventContext context,PlacementLifecycleEvent e) {
        Objects.requireNonNull(e,"Validated event required");
        try {return transactions.execute(status->apply(context,e));}
        catch(org.springframework.dao.DataAccessException unavailable) {return result(e,"RETRY",null,"SOURCE_UNAVAILABLE");}
        catch(tools.jackson.core.JacksonException invalid) {return result(e,"REJECT",null,"INVALID_SCHEMA");}
    }
    private EventConsumeResult apply(TrustedEventContext context,PlacementLifecycleEvent e) {
        if(!source.authenticDelivery(context,e)) return result(e,"REJECT",null,"INVALID_SCHEMA");
        jdbc.update("INSERT INTO internship_projection_locks(placement_id) VALUES (?) ON DUPLICATE KEY UPDATE placement_id=placement_id",e.placementId());
        var p=source.readCurrentForGuard(e.placementId()).orElse(null);
        if(p==null) return result(e,"REJECT",null,"SOURCE_NOT_FOUND");
        if(!p.studentUserId().equals(e.studentUserId()) || !p.schoolOrgId().equals(e.schoolOrgId())
            || !p.enterpriseOrgId().equals(e.enterpriseOrgId()) || !p.termId().equals(e.termId())) return result(e,"REJECT",null,"SCOPE_MISMATCH");
        if(p.version()<e.aggregateVersion()) return result(e,"RETRY",null,"SOURCE_UNAVAILABLE");
        var duplicate=jdbc.queryForList("SELECT applied_version FROM placement_inbox WHERE event_id=? OR (placement_id=? AND aggregate_version=? AND event_type=?) FOR UPDATE",Long.class,e.eventId(),e.placementId(),e.aggregateVersion(),e.eventType());
        if(!duplicate.isEmpty()) return result(e,"ACK_DUPLICATE",duplicate.getFirst(),"DUPLICATE");
        var rows=jdbc.queryForList("SELECT id,state,version,last_applied_version FROM internship_cases WHERE placement_id=? FOR UPDATE",p.placementId());
        var old=rows.isEmpty()?null:rows.getFirst();
        long previous=old==null?-1:((Number)old.get("last_applied_version")).longValue();
        if(e.aggregateVersion()<=previous) {record(e,previous,"ACK_STALE");return result(e,"ACK_STALE",previous,"STALE");}
        if(Set.of("APPROVED","ACTIVE","COMPLETED").contains(p.state()) && (p.approvedAt()==null || p.approvedAt().isAfter(java.time.Instant.now())))
            return result(e,"REJECT",null,"STATE_CONFLICT");
        if(p.state().equals("ACTIVE") && p.effectiveAt().isAfter(java.time.Instant.now())) return result(e,"RETRY",null,"STATE_CONFLICT");
        String state=switch(p.state()) {
            case "APPROVED"->p.approvedAt()==null?null:"WAITING_START";
            case "ACTIVE"->p.approvedAt()==null?null:!p.effectiveAt().isAfter(java.time.Instant.now())?"IN_PROGRESS":"WAITING_START";
            case "CANCELLED"->old!=null && Set.of("IN_PROGRESS","TERMINATED").contains(old.get("state"))?"TERMINATED":"CANCELLED";
            case "COMPLETED"->"COMPLETED";default->null;
        };
        if(state==null) return result(e,"REJECT",null,"STATE_CONFLICT");
        if(old!=null && Set.of("COMPLETED","TERMINATED","CANCELLED").contains(old.get("state")) && !old.get("state").equals(state))
            return result(e,"REJECT",previous,"STATE_CONFLICT");
        String caseId=old==null?UUID.randomUUID().toString():(String)old.get("id");
        long version=old==null?1:((Number)old.get("version")).longValue()+1;
        String from=old==null?null:(String)old.get("state");
        if(old==null) {
            String initial=state.equals("IN_PROGRESS")?"WAITING_START":state;
            jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,?,?,?)",
                caseId,p.placementId(),p.studentUserId(),p.schoolOrgId(),p.enterpriseOrgId(),p.termId(),initial,version,p.version());
            if(state.equals("IN_PROGRESS")) {
                history(context,e,caseId,null,initial,1,p.version());
                from=initial;version=2;
                jdbc.update("UPDATE internship_cases SET state=?,version=? WHERE id=?",state,version,caseId);
            }
        }
        else jdbc.update("UPDATE internship_cases SET state=?,version=?,last_applied_version=? WHERE id=?",state,version,p.version(),caseId);
        history(context,e,caseId,from,state,version,p.version());
        record(e,p.version(),"ACK_APPLIED");
        return result(e,"ACK_APPLIED",p.version(),"APPLIED");
    }
    private void history(TrustedEventContext context,PlacementLifecycleEvent e,String caseId,String from,String to,long version,long sourceVersion) {
        jdbc.update("INSERT INTO internship_case_projection_history(id,case_id,event_id,delivery_operation_id,from_state,to_state,case_version,source_version,received_at) VALUES (?,?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            UUID.randomUUID().toString(),caseId,e.eventId(),context.deliveryOperationId(),from,to,version,sourceVersion);
    }
    private void record(PlacementLifecycleEvent e,long version,String outcome) {
        jdbc.update("INSERT INTO placement_inbox(event_id,placement_id,aggregate_version,event_type,applied_version,outcome,received_at) VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6))",
            e.eventId(),e.placementId(),e.aggregateVersion(),e.eventType(),version,outcome);
    }
    private static EventConsumeResult result(PlacementLifecycleEvent e,String outcome,Long version,String reason) {
        return new EventConsumeResult(e.eventId(),outcome,version,reason,outcome.equals("RETRY")?1:null);
    }
}
