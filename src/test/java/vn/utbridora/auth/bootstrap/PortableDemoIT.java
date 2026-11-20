package vn.utbridora.auth.bootstrap;

import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.application.GradeService;

/** CI-only real MySQL: fresh migrations/import, owner read, rollback, concurrent startup and restart. */
@SpringBootTest(properties={"app.demo.portable.enabled=true","app.demo.local.enabled=false",
    "DEMO_PASSWORD=Synthetic-portable-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-bootstrap-test-password-456",
    "app.t5.placement-delivery.enabled=false","spring.flyway.clean-disabled=false"})
@ActiveProfiles("local")
class PortableDemoIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static {MYSQL.start();}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired Flyway flyway;
    @Autowired AuthorizationPolicy authority;
    @Autowired GradeService grades;
    @Autowired @Qualifier("seedPortableDemo") CommandLineRunner bootstrap;

    @Test void fullCycleSnapshotImportsAtomicallyAndCompletedStartsPreserveDeveloperEdits()throws Exception {
        assertEquals(407,count("users"));assertEquals(9,count("organizations"));assertEquals(1,count("portable_demo_receipts"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM memberships WHERE role='ADMIN' AND active=true AND organization_id IS NULL",Integer.class));
        String admin=jdbc.queryForObject("SELECT password_hash FROM users WHERE email='admin@demo.utbridora.test'",String.class);
        assertTrue(passwords.matches("Synthetic-portable-test-password-123",admin));
        assertTrue(passwords.matches("Synthetic-bootstrap-test-password-456",jdbc.queryForObject("SELECT password_hash FROM users WHERE email='admin@example.test'",String.class)));
        assertEquals(36,jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases c JOIN users u ON u.id=c.student_user_id WHERE u.email LIKE 'cycle.%' AND c.state='COMPLETED'",Integer.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases c JOIN users u ON u.id=c.student_user_id WHERE u.email LIKE 'cycle.%' AND c.state='TERMINATED'",Integer.class));
        assertEquals(48,jdbc.queryForObject("SELECT COUNT(*) FROM internship_evaluations e JOIN internship_cases c ON c.id=e.case_id JOIN users u ON u.id=c.student_user_id WHERE u.email LIKE 'cycle.%' AND e.published_version IS NOT NULL",Integer.class));
        String student=jdbc.queryForObject("SELECT id FROM users WHERE email='cycle.e1.student01@demo.utbridora.test'",String.class);
        String caseId=jdbc.queryForObject("SELECT id FROM internship_cases WHERE student_user_id=?",String.class,student);
        assertTrue(vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(grades.result(authority.currentSubject(student).orElseThrow(),UUID.fromString(caseId))).contains("VISIBLE"));
        for(var empty:List.of("one_time_tokens","invitation_claims","files","activation_mail_outbox","SPRING_SESSION"))assertEquals(0,count(empty));
        assertEquals(1,jdbc.queryForObject("SELECT @@FOREIGN_KEY_CHECKS",Integer.class));
        String changed=passwords.encode("Changed-developer-password-789");
        jdbc.update("UPDATE users SET display_name='Developer changed this',password_hash=? WHERE email='cycle.school1@demo.utbridora.test'",changed);
        bootstrap.run();
        assertEquals(changed,jdbc.queryForObject("SELECT password_hash FROM users WHERE email='cycle.school1@demo.utbridora.test'",String.class));
        assertEquals(407,count("users"));assertEquals(1,count("portable_demo_receipts"));

        // Fresh Flyway schema, then a real late SQL failure must roll back every imported row.
        flyway.clean();flyway.migrate();
        jdbc.execute("CREATE TABLE portable_demo_receipts (fixture_id VARCHAR(64) PRIMARY KEY,sha256 CHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,created_at DATETIME(6) NOT NULL)");
        long taxonomy=jdbc.queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1",Long.class);
        jdbc.execute("CREATE TRIGGER reject_portable_receipt BEFORE INSERT ON portable_demo_receipts FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic late import failure'");
        assertThrows(RuntimeException.class,()->bootstrap.run());
        assertEquals(0,count("users"));assertEquals(0,count("organizations"));assertEquals(0,count("internship_cases"));assertEquals(0,count("portable_demo_receipts"));
        assertEquals(taxonomy,jdbc.queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1",Long.class));
        jdbc.execute("DROP TRIGGER reject_portable_receipt");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{bootstrap.run();return true;});var second=pool.submit(()->{bootstrap.run();return true;});
            assertTrue(first.get(3,TimeUnit.MINUTES));assertTrue(second.get(3,TimeUnit.MINUTES));
        }
        assertEquals(406,count("users"));assertEquals(1,count("portable_demo_receipts"));
        jdbc.update("DELETE FROM portable_demo_receipts");
        bootstrap.run(); // Existing unmarked databases are preserved; replacement is an explicit CLI operation.
        assertEquals(406,count("users"));assertEquals(0,count("portable_demo_receipts"));
        MYSQL.copyFileToContainer(MountableFile.forHostPath("scripts/reset-portable-demo.sh"),"/tmp/reset-portable-demo.sh");
        assertNotEquals(0,MYSQL.execInContainer("sh","/tmp/reset-portable-demo.sh").getExitCode());
        assertEquals(406,count("users"));
        assertEquals(0,MYSQL.execInContainer("sh","-c","UTBRIDORA_DEV_RESET=REPLACE_WITH_PORTABLE_SYNTHETIC sh /tmp/reset-portable-demo.sh").getExitCode());
        flyway.migrate();bootstrap.run();
        assertEquals(406,count("users"));assertEquals(1,count("portable_demo_receipts"));
    }
    private int count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM `"+table+"`",Integer.class);}
}
