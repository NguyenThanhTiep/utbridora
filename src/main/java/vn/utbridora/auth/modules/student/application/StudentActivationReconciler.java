package vn.utbridora.auth.modules.student.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.contracts.onboarding.AccountProvisioningPort;
import vn.utbridora.auth.modules.student.port.StudentActivationEventPort;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class StudentActivationReconciler implements StudentActivationEventPort {
    private final JdbcTemplate jdbc;private final AccountProvisioningPort accounts;
    public StudentActivationReconciler(JdbcTemplate jdbc,AccountProvisioningPort accounts) {this.jdbc=jdbc;this.accounts=accounts;}
    public String reconcile(StudentAccountActivatedEvent e) {
        uuid(e.eventId());uuid(e.aggregateId());uuid(e.userId());uuid(e.rosterId());uuid(e.schoolOrgId());uuid(e.traceId());
        if(!e.eventType().equals("StudentAccountActivated") || e.schemaVersion()!=1 || !e.aggregateId().equals(e.userId()) || e.aggregateVersion()<1 || e.rosterVersion()<1 || e.termIds()==null || e.termIds().isEmpty()) throw failure(422,"EVENT_INVALID");
        e.termIds().forEach(vn.utbridora.auth.operations.WorkflowValues::uuid);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM student_activation_inbox WHERE event_id=?",Integer.class,e.eventId())>0) return "ACK_DUPLICATE";
        if(!accounts.hasActivatedRosterBinding(e.userId(),e.rosterId(),e.schoolOrgId(),e.aggregateVersion())) throw failure(503,"ACTIVATION_PROOF_UNAVAILABLE");
        var rosters=jdbc.queryForList("SELECT id FROM student_rosters WHERE id=? AND school_org_id=? AND user_id=? AND version>=? FOR SHARE",String.class,e.rosterId(),e.schoolOrgId(),e.userId(),e.rosterVersion());
        if(rosters.size()!=1) throw failure(503,"ACTIVATION_BINDING_UNAVAILABLE");
        var terms=jdbc.queryForList("SELECT term_id FROM student_roster_terms WHERE roster_id=? ORDER BY term_id FOR SHARE",String.class,e.rosterId());
        if(!terms.containsAll(e.termIds())) throw failure(503,"ACTIVATION_BINDING_UNAVAILABLE");
        Long prior=jdbc.queryForObject("SELECT MAX(aggregate_version) FROM student_activation_inbox WHERE aggregate_id=?",Long.class,e.aggregateId());
        jdbc.update("INSERT INTO student_activation_inbox(event_id,aggregate_id,aggregate_version,applied_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",e.eventId(),e.aggregateId(),e.aggregateVersion());
        return prior!=null && prior>=e.aggregateVersion()?"ACK_STALE":"ACK_APPLIED";
    }
}
