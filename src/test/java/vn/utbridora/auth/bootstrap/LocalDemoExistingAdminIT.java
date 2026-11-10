package vn.utbridora.auth.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.application.*;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;
import vn.utbridora.auth.modules.student.application.StudentProfileService;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.demo.local.enabled=false",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Original-admin-password-123",
    "app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("local")
class LocalDemoExistingAdminIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static { MYSQL.start(); }
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired PasswordEncoder passwords;
    @Autowired PlacementLifecyclePublisher publisher;
    @Autowired PlacementLifecycleConsumer consumer;
    @Autowired StudentProfileService profiles;
    @Autowired DemandService demands;
    @Autowired AuthorizationPolicy authority;

    CommandLineRunner seed(boolean allowExistingAdmin) {
        return new LocalDemoBootstrap().seedLocalDemo(jdbc,tx,passwords,publisher,consumer,profiles,demands,authority,
            "admin@example.test","Original-admin-password-123","Synthetic-demo-password-456",allowExistingAdmin);
    }

    @Test void explicitImportPreservesExistingAdminAndRemainsRepeatable() throws Exception {
        String adminId=jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'",String.class);
        jdbc.update("UPDATE users SET password_hash=?,display_name='My existing admin' WHERE id=?",passwords.encode("Changed-admin-password-789"),adminId);
        var originalAdmin=jdbc.queryForMap("SELECT * FROM users WHERE id=?",adminId);
        var originalMembership=jdbc.queryForMap("SELECT * FROM memberships WHERE user_id=?",adminId);
        assertThrows(IllegalStateException.class,()->seed(false).run());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));

        // A sole account that lacks the active global admin grant cannot be preserved as an admin.
        jdbc.update("UPDATE memberships SET active=false WHERE user_id=?",adminId);
        assertThrows(IllegalStateException.class,()->seed(true).run());
        jdbc.update("UPDATE memberships SET active=true WHERE user_id=?",adminId);

        seed(true).run();
        assertEquals(originalAdmin,jdbc.queryForMap("SELECT * FROM users WHERE id=?",adminId));
        assertEquals(originalMembership,jdbc.queryForMap("SELECT * FROM memberships WHERE user_id=?",adminId));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE role='ADMIN'",Integer.class));
        assertEquals(adminId,jdbc.queryForObject("SELECT granted_by FROM school_delegations",String.class));
        assertEquals("DONE",jdbc.queryForObject("SELECT state FROM local_demo_seed_receipts",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demands",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases",Integer.class));
        seed(true).run();
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(originalAdmin,jdbc.queryForMap("SELECT * FROM users WHERE id=?",adminId));
        // Explicit opt-in is not permission to mix fixtures into a populated business database.
        jdbc.update("DELETE FROM local_demo_seed_receipts");
        assertThrows(IllegalStateException.class,()->seed(true).run());
        assertEquals(originalAdmin,jdbc.queryForMap("SELECT * FROM users WHERE id=?",adminId));
    }
}
