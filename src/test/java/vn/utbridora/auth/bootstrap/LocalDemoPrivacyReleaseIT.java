package vn.utbridora.auth.bootstrap;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.application.DemandService;
import vn.utbridora.auth.modules.matching.application.MatchingService;

@SpringBootTest(properties = {
    "app.demo.local.enabled=true",
    "app.demo.local.privacy-release-enabled=true",
    "DEMO_PASSWORD=Synthetic-demo-test-password-123",
    "app.bootstrap.admin.email=admin@example.test",
    "app.bootstrap.admin.password=Synthetic-admin-test-password-456",
    "app.t5.placement-delivery.enabled=false"
})
@ActiveProfiles("local")
class LocalDemoPrivacyReleaseIT {
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora")
        .withCommand("--log-bin-trust-function-creators=1");
    static { MYSQL.start(); }
    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AuthorizationPolicy authority;
    @Autowired DemandService demands;
    @Autowired MatchingService matching;
    @Autowired @Qualifier("seedLocalPrivacyRelease") CommandLineRunner releaseBootstrap;

    @Test
    void localDemoPrivacyReleaseProvidesProtectedBandAndRespectsOperatorDisable() throws Exception {
        assertTrue(jdbc.queryForObject("SELECT enabled FROM matching_privacy_release_settings WHERE id=1", Boolean.class));
        var hr = authority.currentSubject("00000000-0000-4000-8000-000000000004").orElseThrow();
        var demand = demands.list(hr, "00000000-0000-4000-8000-000000000012", null, null, false, 0, 25).content().getFirst();
        var evaluated = matching.evaluate(hr, UUID.randomUUID().toString(),
            new MatchingService.Evaluate(demand.demandId(), demand.version(), demand.criteriaVersion()));
        assertEquals("ENTERPRISE", evaluated.audience());
        assertNotNull(evaluated.release());
        assertEquals("5-9", evaluated.release().eligibleBand());

        // Operator toggle off must be preserved across restarts.
        jdbc.update("UPDATE matching_privacy_release_settings SET enabled=false WHERE id=1");
        releaseBootstrap.run();
        assertFalse(jdbc.queryForObject("SELECT enabled FROM matching_privacy_release_settings WHERE id=1", Boolean.class));
    }
}
