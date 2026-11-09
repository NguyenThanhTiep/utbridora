package vn.utbridora.auth;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.student.application.StudentProfileService;
import vn.utbridora.auth.modules.cooperation.application.DemandService;

/** Real MySQL, Flyway, owner services and committed Placement source/outbox/inbox. */
@SpringBootTest(properties={"app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-demo-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-admin-test-password-456",
    "app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("local")
class LocalDemoIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static {MYSQL.start();}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthorizationPolicy authority;
    @Autowired StudentProfileService profiles;
    @Autowired DemandService demands;
    @Autowired @Qualifier("seedLocalDemo") CommandLineRunner bootstrap;

    @Test void seedOwnersResumeAndRestartWithoutOverwritingAndRejectUnmarkedExistingDatabase() throws Exception {
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM organizations",Integer.class));
        assertEquals("DONE",jdbc.queryForObject("SELECT state FROM local_demo_seed_receipts",String.class));
        String adminHash=jdbc.queryForObject("SELECT password_hash FROM users WHERE email='admin@example.test'",String.class);
        assertTrue(passwords.matches("Synthetic-admin-test-password-456",adminHash));
        assertFalse(passwords.matches("Synthetic-demo-test-password-123",adminHash));
        for(String email:List.of("school","coordinator","hr","mentor","student","intern"))
            assertTrue(passwords.matches("Synthetic-demo-test-password-123",jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?",String.class,email+"@example.test")));
        assertEquals("00123",jdbc.queryForObject("SELECT student_code FROM student_rosters",String.class));
        assertFalse(jdbc.queryForObject("SELECT school_approved FROM student_term_eligibilities",Boolean.class));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT state FROM internship_cases",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM placement_inbox",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM internship_case_projection_history",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE group_id IS NOT NULL",Integer.class));
        var student=authority.currentSubject("00000000-0000-4000-8000-000000000006").orElseThrow();
        var profile=profiles.read(student,"00000000-0000-4000-8000-000000000021");
        assertEquals("SE",profile.profile().majorCode());
        assertEquals("0901234567",profile.profile().phone());
        var hr=authority.currentSubject("00000000-0000-4000-8000-000000000004").orElseThrow();
        assertEquals("DEMO - Java Internship",demands.list(hr,"00000000-0000-4000-8000-000000000012",null,null,false,0,10).content().getFirst().data().title());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM files",Integer.class));

        jdbc.update("UPDATE users SET display_name='Preserved local edit' WHERE email='student@example.test'");
        String editedHash=passwords.encode("Changed-local-demo-password-789");
        jdbc.update("UPDATE users SET password_hash=? WHERE email='student@example.test'",editedHash);
        jdbc.update("UPDATE student_profiles SET phone='0901111222'");
        int auditCount=jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs",Integer.class);
        bootstrap.run();
        assertEquals(auditCount,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs",Integer.class));
        assertEquals(editedHash,jdbc.queryForObject("SELECT password_hash FROM users WHERE email='student@example.test'",String.class));
        assertEquals("Preserved local edit",jdbc.queryForObject("SELECT display_name FROM users WHERE email='student@example.test'",String.class));
        assertEquals("0901111222",profiles.read(student,"00000000-0000-4000-8000-000000000021").profile().phone());

        // Simulate a stop after source commit: resume only delivery, never reset business data.
        jdbc.update("UPDATE local_demo_seed_receipts SET state='SOURCE_READY'");
        bootstrap.run();
        assertEquals("DONE",jdbc.queryForObject("SELECT state FROM local_demo_seed_receipts",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM placement_inbox",Integer.class));
        assertEquals("0901111222",profiles.read(student,"00000000-0000-4000-8000-000000000021").profile().phone());

        // An existing unmarked database must fail closed, preserving all preexisting users.
        jdbc.update("DELETE FROM local_demo_seed_receipts");
        assertThrows(IllegalStateException.class,()->bootstrap.run());
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(editedHash,jdbc.queryForObject("SELECT password_hash FROM users WHERE email='student@example.test'",String.class));
        assertEquals(adminHash,jdbc.queryForObject("SELECT password_hash FROM users WHERE email='admin@example.test'",String.class));
    }
}
