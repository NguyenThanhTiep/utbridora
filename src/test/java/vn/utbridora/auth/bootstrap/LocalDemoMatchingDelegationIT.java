package vn.utbridora.auth.bootstrap;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.application.DemandService;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.operations.OperationFailure;

@SpringBootTest(properties={"app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-demo-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-admin-test-password-456",
    "app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("local")
class LocalDemoMatchingDelegationIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora")
        .withCommand("--log-bin-trust-function-creators=1");
    static {MYSQL.start();}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationPolicy authority;
    @Autowired SchoolDelegationAccess access;
    @Autowired SchoolDelegationService delegations;
    @Autowired DemandService demands;
    @Autowired MatchingService matching;
    @Autowired @Qualifier("seedLocalMatchingDelegation") CommandLineRunner upgrade;
    @Autowired @Qualifier("seedLocalDemo") CommandLineRunner seed;
    static String id(int n) {return "00000000-0000-4000-8000-"+String.format("%012d",n);}
    int assignments() {return jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations WHERE decision_ref=?",Integer.class,LocalDemoMatchingDelegation.DECISION);}
    long version() {return jdbc.queryForObject("SELECT security_version FROM users WHERE id=?",Long.class,id(2));}
    int grants() {return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CAPABILITY_GRANTED'",Integer.class);}
    void assertMatchingAccess() {
        var school=authority.currentSubject(id(2)).orElseThrow();
        LocalDemoMatchingDelegation.PERMISSIONS.forEach(p->assertDoesNotThrow(()->access.require(school,id(11),id(21),p)));
    }
    @Test void freshAndLegacyDemoHaveAuditedMatchingAccessWithoutRenewingRevokedAssignments() throws Exception {
        try {
            assertEquals(1,assignments());assertMatchingAccess();
            // An existing fixture must gain workflow authority through T1, never by
            // changing its root SQL permissions on every restart.
            for(String permission:LocalDemoMatchingDelegation.WORKFLOW_PERMISSIONS)
                jdbc.update("DELETE FROM school_delegation_permissions WHERE assignment_id=? AND permission_code=?",id(51),permission);
            var rootPermissions=jdbc.queryForList("SELECT permission_code FROM school_delegation_permissions WHERE assignment_id=? ORDER BY permission_code",String.class,id(51));
            seed.run();
            assertEquals(rootPermissions,jdbc.queryForList("SELECT permission_code FROM school_delegation_permissions WHERE assignment_id=? ORDER BY permission_code",String.class,id(51)));
            upgrade.run();
            for(String permission:LocalDemoMatchingDelegation.WORKFLOW_PERMISSIONS)
                assertDoesNotThrow(()->access.require(authority.currentSubject(id(2)).orElseThrow(),id(11),id(21),permission));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations WHERE decision_ref=?",Integer.class,LocalDemoMatchingDelegation.WORKFLOW_DECISION));
            assertEquals(rootPermissions,jdbc.queryForList("SELECT permission_code FROM school_delegation_permissions WHERE assignment_id=? ORDER BY permission_code",String.class,id(51)));
            assertEquals("[]",jdbc.queryForObject("SELECT profile_read_fields_json FROM school_delegations WHERE decision_ref=?",String.class,LocalDemoMatchingDelegation.DECISION));
            var hr=authority.currentSubject(id(4)).orElseThrow();
            var demand=demands.list(hr,id(12),null,null,false,0,25).content().getFirst();
            var result=matching.evaluate(authority.currentSubject(id(2)).orElseThrow(),UUID.randomUUID().toString(),new MatchingService.Evaluate(demand.demandId(),demand.version(),demand.criteriaVersion()));
            assertEquals("COMPLETED",result.summary().run().state());
            long security=version();int audit=grants();upgrade.run();upgrade.run();
            assertEquals(1,assignments());assertEquals(security,version());assertEquals(audit,grants());

            // Reproduce a pre-upgrade demo, retaining its accounts, root and business data.
            String assignment=jdbc.queryForObject("SELECT id FROM school_delegations WHERE decision_ref=?",String.class,LocalDemoMatchingDelegation.DECISION);
            jdbc.update("DELETE FROM school_delegation_permissions WHERE assignment_id=?",assignment);
            jdbc.update("DELETE FROM school_delegations WHERE id=?",assignment);
            var users=jdbc.queryForList("SELECT id,email,password_hash,display_name FROM users ORDER BY id");
            var originalRoot=jdbc.queryForMap("SELECT * FROM school_delegations WHERE id=?",id(51));
            int demandCount=jdbc.queryForObject("SELECT COUNT(*) FROM demands",Integer.class);
            jdbc.execute("CREATE TRIGGER fail_matching_grant_audit BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='CAPABILITY_GRANTED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='audit unavailable'; END IF; END");
            try {assertThrows(RuntimeException.class,()->upgrade.run());}
            finally {jdbc.execute("DROP TRIGGER fail_matching_grant_audit");}
            assertEquals(0,assignments());assertEquals(security,version());assertEquals(audit,grants());
            upgrade.run();assertMatchingAccess();
            assertEquals(1,assignments());assertEquals(security+1,version());assertEquals(audit+1,grants());
            assertEquals(users,jdbc.queryForList("SELECT id,email,password_hash,display_name FROM users ORDER BY id"));
            assertEquals(originalRoot,jdbc.queryForMap("SELECT * FROM school_delegations WHERE id=?",id(51)));
            assertEquals(demandCount,jdbc.queryForObject("SELECT COUNT(*) FROM demands",Integer.class));

            // An explicit T1 revoke remains effective after restarting the local upgrader.
            assignment=jdbc.queryForObject("SELECT id FROM school_delegations WHERE decision_ref=?",String.class,LocalDemoMatchingDelegation.DECISION);
            var admin=authority.currentSubject(jdbc.queryForObject("SELECT granted_by FROM school_delegations WHERE id=?",String.class,assignment)).orElseThrow();
            SecurityContextHolder.getContext().setAuthentication(admin);
            delegations.revoke(admin,assignment,UUID.randomUUID().toString(),new SchoolDelegationService.Revoke(1L,"SECURITY_REVIEW"));
            security=version();audit=grants();upgrade.run();
            assertEquals(1,assignments());assertEquals(security,version());assertEquals(audit,grants());
            var school=authority.currentSubject(id(2)).orElseThrow();
            assertThrows(OperationFailure.class,()->access.require(school,id(11),id(21),"matching:run:execute"));

            String workflow=jdbc.queryForObject("SELECT id FROM school_delegations WHERE decision_ref=?",String.class,LocalDemoMatchingDelegation.WORKFLOW_DECISION);
            SecurityContextHolder.getContext().setAuthentication(admin);
            delegations.revoke(admin,workflow,UUID.randomUUID().toString(),new SchoolDelegationService.Revoke(1L,"SECURITY_REVIEW"));
            security=version();audit=grants();seed.run();upgrade.run();
            assertEquals(security,version());assertEquals(audit,grants());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations WHERE decision_ref=?",Integer.class,LocalDemoMatchingDelegation.WORKFLOW_DECISION));
            for(String permission:LocalDemoMatchingDelegation.WORKFLOW_PERMISSIONS)
                assertThrows(OperationFailure.class,()->access.require(authority.currentSubject(id(2)).orElseThrow(),id(11),id(21),permission));
            assertEquals(rootPermissions,jdbc.queryForList("SELECT permission_code FROM school_delegation_permissions WHERE assignment_id=? ORDER BY permission_code",String.class,id(51)));
        } finally {SecurityContextHolder.clearContext();}
    }
}
