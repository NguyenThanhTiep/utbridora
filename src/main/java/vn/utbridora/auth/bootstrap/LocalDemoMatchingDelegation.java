package vn.utbridora.auth.bootstrap;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.operations.OperationFailure;

/** Upgrade only the explicitly opted-in synthetic fixture through T1's audited owner service. */
@Configuration(proxyBeanMethods=false)
@Profile("local")
@ConditionalOnProperty(name="app.demo.local.enabled",havingValue="true")
public class LocalDemoMatchingDelegation {
    static final String DECISION="DEMO-T5-WEEK3-MATCHING";
    static final List<String> PERMISSIONS=List.of("matching:run:execute","matching:school:detail_read","matching:school:nominate");
    static final String WORKFLOW_DECISION="DEMO-T5-WEEK3-WORKFLOW";
    static final List<String> WORKFLOW_PERMISSIONS=List.of("cooperation:school:review","t5:proposal:manage",
        "opportunity:school:publish","student_profile:share:school:manage","student:eligibility:school:approve","placement:school:write");
    private record Upgrade(String decision,List<String> permissions) {}
    private record Root(String id,String user,String school,String term,String grantor,Instant from,Instant until) {}

    @Bean @Order(110)
    CommandLineRunner seedLocalMatchingDelegation(JdbcTemplate jdbc,TransactionTemplate tx,AuthorizationPolicy authority,
            SchoolDelegationAccess access,SchoolDelegationService delegations) {
        return args->tx.executeWithoutResult(status->{
            jdbc.queryForObject("SELECT lock_name FROM core_locks WHERE lock_name='GLOBAL_ADMIN' FOR UPDATE",String.class);
            if(jdbc.queryForObject("SELECT COUNT(*) FROM local_demo_seed_receipts WHERE fixture_id='LOCAL_SYNTHETIC_V1' AND state='DONE'",Integer.class)!=1) return;
            var roots=jdbc.query("SELECT d.* FROM school_delegations d JOIN users u ON u.id=d.user_id WHERE d.id='00000000-0000-4000-8000-000000000051' AND d.user_id='00000000-0000-4000-8000-000000000002' AND d.school_org_id='00000000-0000-4000-8000-000000000011' AND d.term_id='00000000-0000-4000-8000-000000000021' AND d.decision_ref='SYNTHETIC_WEEK1_DEMO' AND d.parent_id IS NULL AND d.active=true AND u.email='school@example.test' AND d.valid_from<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<d.valid_until FOR UPDATE",
                (r,n)->new Root(r.getString("id"),r.getString("user_id"),r.getString("school_org_id"),r.getString("term_id"),r.getString("granted_by"),instant(r,"valid_from"),instant(r,"valid_until")));
            if(roots.size()!=1) return;
            var root=roots.getFirst();
            for(var upgrade:List.of(new Upgrade(DECISION,PERMISSIONS),new Upgrade(WORKFLOW_DECISION,WORKFLOW_PERMISSIONS))) {
                // A prior explicit assignment, including a revoked/expired one, is an operator decision.
                // Startup must neither replace it nor renew it.
                var parameters=new ArrayList<Object>(List.of(root.user(),root.school(),root.term(),root.id(),upgrade.decision()));
                parameters.addAll(upgrade.permissions());
                String placeholders=String.join(",",Collections.nCopies(upgrade.permissions().size(),"?"));
                if(jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations d WHERE d.user_id=? AND d.school_org_id=? AND d.term_id=? AND d.id<>? AND (d.decision_ref=? OR EXISTS(SELECT 1 FROM school_delegation_permissions p WHERE p.assignment_id=d.id AND p.permission_code IN ("+placeholders+")))",Integer.class,parameters.toArray())>0) continue;
                var subject=authority.currentSubject(root.user()).orElse(null);
                if(subject==null) continue;
                boolean complete=true;
                for(String permission:upgrade.permissions()) {
                    try {access.require(subject,root.school(),root.term(),permission);}
                    catch(OperationFailure denied) {if(denied.status!=404) throw denied;complete=false;}
                }
                if(complete) continue;
                var admin=authority.currentSubject(root.grantor()).orElse(null);
                if(admin==null || !authority.hasGlobal(admin,"membership:admin:write")) continue;
                var previous=SecurityContextHolder.getContext();
                var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(admin);
                SecurityContextHolder.setContext(context);
                try {
                    delegations.bootstrap(admin,UUID.randomUUID().toString(),new SchoolDelegationService.Bootstrap(
                        root.user(),root.school(),root.term(),upgrade.permissions(),List.of(),root.from(),root.until(),upgrade.decision()));
                } finally {SecurityContextHolder.setContext(previous);}
            }
        });
    }
    private static Instant instant(ResultSet row,String column) throws SQLException {
        return row.getTimestamp(column).toLocalDateTime().toInstant(ZoneOffset.UTC);
    }
}
