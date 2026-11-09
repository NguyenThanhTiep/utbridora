package vn.utbridora.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;

/** Validates the actual opt-in demo import in its required separate MySQL schema. */
@SpringBootTest(properties={"app.bootstrap.admin.email=","app.bootstrap.admin.password=",
    "app.demo.week1.enabled=true","DEMO_PASSWORD=Synthetic-test-password-123","app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("demo")
class Week1DemoIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora_week1_demo");
    static {MYSQL.start();}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("seedWeek1Demo") CommandLineRunner bootstrap;
    @Test void sourceCommitsBeforeConsumerAndRestartPreservesUserChanges() throws Exception {
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals("DONE",jdbc.queryForObject("SELECT state FROM demo_fixture_receipts",String.class));
        assertEquals("00123",jdbc.queryForObject("SELECT student_code FROM student_rosters",String.class));
        assertFalse(jdbc.queryForObject("SELECT school_approved FROM student_term_eligibilities",Boolean.class));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT state FROM internship_cases",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM placement_inbox",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM internship_case_projection_history",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE group_id IS NOT NULL",Integer.class));
        jdbc.update("UPDATE users SET display_name='Preserved demo edit' WHERE email='student@example.test'");
        bootstrap.run();
        assertEquals("Preserved demo edit",jdbc.queryForObject("SELECT display_name FROM users WHERE email='student@example.test'",String.class));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM files",Integer.class));
    }
}
