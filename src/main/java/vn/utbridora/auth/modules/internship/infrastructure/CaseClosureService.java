package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.modules.internship.api.InternshipOperationsDtos.CloseCase;
import vn.utbridora.auth.modules.internship.domain.WeeklyReportPolicy;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
@Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class CaseClosureService implements InternshipCaseReadPort {
    private final JdbcTemplate jdbc;private final CaseRuntime cases;private final OperationsAccess access;private final PlacementReadPort source;
    private final SchoolDelegationAccess delegates;private final OperationLedger ledger;private final AuditService audit;private final Clock clock;
    public CaseClosureService(JdbcTemplate jdbc,CaseRuntime cases,OperationsAccess access,PlacementReadPort source,SchoolDelegationAccess delegates,OperationLedger ledger,AuditService audit,
        @org.springframework.beans.factory.annotation.Qualifier("internshipOperationsClock") Clock clock) {
        this.jdbc=jdbc;this.cases=cases;this.access=access;this.source=source;this.delegates=delegates;this.ledger=ledger;this.audit=audit;this.clock=clock;
    }
    public CaseRuntime.CaseRef close(Authentication auth,UUID id,String key,CloseCase input) {
        var before=cases.read(id);access.schoolCase(auth,before,"t3:cases:close");
        return ledger.execute(new OperationLedger.Command(actor(auth),before.schoolOrgId().toString(),"T3_CASE_CLOSE",key,Map.of("caseId",id,"input",input),200),CaseRuntime.CaseRef.class,()->{
            if(before.version()!=input.expectedVersion()) throw failure(409,"VERSION_CONFLICT");
            var p=source.readCurrentForGuard(before.placementId().toString()).orElseThrow(()->failure(503,"DEPENDENCY_UNAVAILABLE"));
            var c=cases.holdForReport(id,before.version());
            access.schoolCase(auth,c,"t3:cases:close");
            var stamp=delegates.capture(auth,c.schoolOrgId().toString(),c.termId().toString(),"t3:cases:close");
            if(!Set.of("WAITING_START","IN_PROGRESS").contains(c.state()) || !Set.of("APPROVED","ACTIVE").contains(p.state()) || p.approvedAt()==null || p.approvedAt().isAfter(clock.instant())) throw failure(409,"STATE_CONFLICT");
            var gates=gates(c,p);
            boolean normal=input.decision().equals("NORMAL_COMPLETION");
            if(normal && (!c.state().equals("IN_PROGRESS") || !p.state().equals("ACTIVE") || p.effectiveAt().isAfter(clock.instant()) || !gates.endDateReached() || !gates.allTasksCompleted() || !gates.allReportsReviewed() || !gates.evaluationPublished())) throw failure(409,"CASE_CLOSE_PRECONDITION_FAILED");
            long next=c.version()+1;String state=normal?"COMPLETED":"TERMINATED";Instant now=clock.instant();
            var proof=new CaseClosureProof(id.toString(),p.placementId(),next,state,input.decision(),input.decisionRef(),input.reasonCode(),actor(auth),p.schoolOrgId(),p.termId(),now,gates.endDateReached(),gates.allTasksCompleted(),gates.allReportsReviewed(),gates.evaluationPublished());
            jdbc.update("UPDATE internship_cases SET state=?,version=? WHERE id=? AND version=?",state,next,id.toString(),c.version());
            jdbc.update("INSERT INTO internship_case_closures(case_id,case_version,proof_json,actor_id,delegation_id,delegation_version,closed_at) VALUES(?,?,?,?,?,?,?)",id.toString(),next,JSON.writeValueAsString(proof),actor(auth),stamp.id(),stamp.version(),utc(now));
            var event=new CaseClosedEvent(UUID.randomUUID().toString(),"CaseClosed",1,id.toString(),p.placementId(),next,next,input.decision(),input.decisionRef(),input.reasonCode(),now,UUID.randomUUID().toString());
            jdbc.update("INSERT INTO internship_case_closed_outbox(event_id,case_id,aggregate_version,operation_id,payload_json,next_attempt_at) VALUES(?,?,?,?,?,?)",event.eventId(),id.toString(),next,UUID.randomUUID().toString(),JSON.writeValueAsString(event),utc(now));
            audit.workflow(actor(auth),"CASE_CLOSED",id.toString(),p.schoolOrgId(),Map.of("placementId",p.placementId(),"caseVersion",next,"decision",input.decision(),"decisionRef",input.decisionRef(),"reasonCode",input.reasonCode()));
            return cases.read(id);
        },r->r.caseId().toString());
    }
    private record Gates(boolean endDateReached,boolean allTasksCompleted,boolean allReportsReviewed,boolean evaluationPublished) {}
    private Gates gates(CaseRuntime.CaseRef c,PlacementReadPort.PlacementView p) {
        boolean date=!LocalDate.now(clock.withZone(WeeklyReportPolicy.ZONE)).isBefore(p.endDate());
        boolean tasks=jdbc.queryForObject("SELECT COUNT(*) FROM internship_task_assignments WHERE case_id=? AND state<>'COMPLETED'",Long.class,c.caseId().toString())==0;
        Set<LocalDate> reviewed=new HashSet<>(jdbc.query("SELECT r.week_start FROM internship_weekly_reports r JOIN internship_weekly_report_revisions v ON v.report_id=r.id AND v.revision_no=r.current_revision_no WHERE r.case_id=? AND v.state='REVIEWED'",(r,n)->r.getObject(1,LocalDate.class),c.caseId().toString()));
        var week=p.startDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));boolean reports=true;int visited=0;
        while(!week.isAfter(p.endDate())) {if(++visited>10000 || !reviewed.contains(week)) {reports=false;break;}week=week.plusWeeks(1);}
        boolean published=jdbc.queryForObject("SELECT COUNT(*) FROM internship_evaluations e JOIN internship_evaluation_versions v ON v.evaluation_id=e.id AND v.version=e.current_version WHERE e.case_id=? AND v.state='PUBLISHED'",Long.class,c.caseId().toString())==1;
        return new Gates(date,tasks,reports,published);
    }
    @Override public CaseClosureProof readClosureProof(ActorContext actor,UUID id) {
        var c=cases.read(id);access.schoolCase(actor.authentication(),c,"t3:cases:close");return proof(id);
    }
    private CaseClosureProof proof(UUID id) {
        var rows=jdbc.queryForList("SELECT proof_json,actor_id,delegation_id,delegation_version FROM internship_case_closures WHERE case_id=? FOR SHARE",id.toString());
        if(rows.size()!=1) throw failure(503,"DEPENDENCY_UNAVAILABLE");var row=rows.getFirst();
        var proof=JSON.readValue(row.get("proof_json").toString(),CaseClosureProof.class);var c=cases.read(id);
        var current=source.readCurrentForGuard(c.placementId().toString()).orElseThrow(()->failure(503,"DEPENDENCY_UNAVAILABLE"));
        if(!current.studentUserId().equals(c.studentUserId().toString()) || !current.enterpriseOrgId().equals(c.enterpriseOrgId().toString()) || !current.schoolOrgId().equals(c.schoolOrgId().toString()) || !current.termId().equals(c.termId().toString())) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        if(!proof.caseId().equals(id.toString()) || !proof.placementId().equals(c.placementId().toString()) || c.version()<proof.caseVersion() || !c.state().equals(proof.state()) || !proof.schoolOrgId().equals(c.schoolOrgId().toString()) || !proof.termId().equals(c.termId().toString()) || !proof.decisionActorUserId().equals(row.get("actor_id"))) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        delegates.requireStamp(row.get("delegation_id").toString(),((Number)row.get("delegation_version")).longValue(),proof.decisionActorUserId(),proof.schoolOrgId(),proof.termId(),"t3:cases:close");return proof;
    }
    @Override public boolean authenticDelivery(TrustedEventContext context,CaseClosedEvent event) {
        if(context==null || event==null || !context.producerModule().equals("T3")) return false;
        return jdbc.queryForList("SELECT payload_json FROM internship_case_closed_outbox WHERE event_id=? AND case_id=? AND aggregate_version=? AND operation_id=?",String.class,event.eventId(),event.caseId(),event.caseVersion(),context.deliveryOperationId())
            .stream().anyMatch(s->JSON.readValue(s,CaseClosedEvent.class).equals(event));
    }
    @Override public CaseClosureProof proofForDelivery(TrustedEventContext context,CaseClosedEvent event) {
        if(!authenticDelivery(context,event)) throw failure(403,"ACCESS_DENIED");var p=proof(UUID.fromString(event.caseId()));
        if(!p.placementId().equals(event.placementId()) || p.caseVersion()!=event.caseVersion() || !p.decision().equals(event.decision()) || !p.decisionRef().equals(event.decisionRef()) || !p.reasonCode().equals(event.reasonCode()) || !p.closedAt().equals(event.closedAt())) throw failure(503,"DEPENDENCY_UNAVAILABLE");return p;
    }
}
