package vn.utbridora.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;

/** Upgrade an existing local DB without changing any old account, organization, membership or taxonomy row. */
@SpringBootTest(properties={"app.demo.local.enabled=true","app.demo.local.append-existing=true",
    "DEMO_PASSWORD=Synthetic-append-demo-password-123","app.bootstrap.admin.email=existing-admin@example.test",
    "app.bootstrap.admin.password=Different-bootstrap-password-999","app.t5.placement-delivery.enabled=false",
    "logging.level.org.springframework.jdbc=WARN"})
@ActiveProfiles("local")
class LocalDemoAppendIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static { MYSQL.start(); }
    static final String ADMIN="11111111-1111-4111-8111-111111111111",USER="22222222-2222-4222-8222-222222222222";
    static final String ORG="33333333-3333-4333-8333-333333333333",CATEGORY="44444444-4444-4444-8444-444444444444",SKILL="55555555-5555-4555-8555-555555555555";
    static final Map<String,String> BEFORE=new LinkedHashMap<>();
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    static String digest(JdbcTemplate jdbc,String query) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jdbc.queryForList(query).toString().getBytes(StandardCharsets.UTF_8)));}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    @TestConfiguration static class ExistingDatabase {
        @Bean @Order(0) CommandLineRunner prepareExisting(JdbcTemplate jdbc,PasswordEncoder encoder) {
            return args->{
                jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,security_version,created_at,updated_at) VALUES (?,?,?,'Existing admin','ACTIVE',7,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",ADMIN,"existing-admin@example.test",encoder.encode("Do-not-reset-old-admin-123"));
                jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,security_version,created_at,updated_at) VALUES (?,?,?,'Old account','DEACTIVATED',9,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",USER,"old-user@example.test",encoder.encode("Do-not-reset-old-user-456"));
                jdbc.update("INSERT INTO organizations(id,name,type,status,created_at) VALUES (?,'Existing school','UNIVERSITY','PENDING',UTC_TIMESTAMP(6))",ORG);
                jdbc.update("INSERT INTO memberships(id,user_id,role,created_at) VALUES (UUID(),?,'ADMIN',UTC_TIMESTAMP(6))",ADMIN);
                jdbc.update("INSERT INTO memberships(id,user_id,organization_id,role,active,created_at) VALUES (UUID(),?,?,'STUDENT',false,UTC_TIMESTAMP(6))",USER,ORG);
                jdbc.update("INSERT INTO skill_categories(id,category_code,name,normalized_name,status,version,created_at,updated_at) VALUES (?,'EXISTING_CODE','Existing category','existing category','ACTIVE',3,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",CATEGORY);
                jdbc.update("INSERT INTO skills(id,skill_code,name,normalized_name,category_id,status,version,created_at,updated_at) VALUES (?,'JAVA','Java','java',?,'ACTIVE',4,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",SKILL,CATEGORY);
                jdbc.update("UPDATE taxonomy_catalog_state SET taxonomy_version=7 WHERE id=1");
                for(String table:List.of("users","organizations","memberships","skill_categories","skills"))
                    BEFORE.put(table,digest(jdbc,"SELECT * FROM "+table+" ORDER BY id"));
            };
        }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired @Qualifier("seedLocalDemo") CommandLineRunner seed;

    @Test void appendReusesAdminAndTaxonomyPreservesOldRowsAndDoesNotResetOnReplayOrCollision() throws Exception {
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM organizations",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM skills",Integer.class));
        assertEquals(7,jdbc.queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id='00000000-0000-4000-8000-000000000001'",Integer.class));
        assertEquals(ADMIN,jdbc.queryForObject("SELECT granted_by FROM school_delegations",String.class));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT state FROM internship_cases",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM demand_revisions",Integer.class));
        assertOldRows();
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,ADMIN);
        assertTrue(passwords.matches("Do-not-reset-old-admin-123",hash));
        assertFalse(passwords.matches("Different-bootstrap-password-999",hash));
        jdbc.update("UPDATE student_profiles SET phone='0909999888'");
        seed.run();seed.run();
        assertEquals("0909999888",jdbc.queryForObject("SELECT phone FROM student_profiles",String.class));
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertOldRows();
        jdbc.update("DELETE FROM local_demo_seed_receipts");
        var failure=assertThrows(IllegalStateException.class,()->seed.run());
        assertTrue(failure.getMessage().contains("refusing to overwrite"));
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals("0909999888",jdbc.queryForObject("SELECT phone FROM student_profiles",String.class));
        assertOldRows();
        // The previous shared-DB importer used a different receipt table; recognize it without resetting data.
        jdbc.execute("CREATE TABLE local_demo_import_receipts (fixture_id VARCHAR(64) PRIMARY KEY,state VARCHAR(16) NOT NULL,admin_user_id VARCHAR(36) NOT NULL,created_at DATETIME(6) NOT NULL)");
        jdbc.update("INSERT INTO local_demo_import_receipts VALUES ('SHARED_WEEK1_SYNTHETIC_V1','DONE',?,UTC_TIMESTAMP(6))",ADMIN);
        seed.run();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM local_demo_seed_receipts",Integer.class));
        assertEquals("0909999888",jdbc.queryForObject("SELECT phone FROM student_profiles",String.class));
        assertOldRows();
    }
    private void assertOldRows() {
        assertEquals(BEFORE.get("users"),digest(jdbc,"SELECT * FROM users WHERE id IN ('"+ADMIN+"','"+USER+"') ORDER BY id"));
        assertEquals(BEFORE.get("organizations"),digest(jdbc,"SELECT * FROM organizations WHERE id='"+ORG+"' ORDER BY id"));
        assertEquals(BEFORE.get("memberships"),digest(jdbc,"SELECT * FROM memberships WHERE user_id IN ('"+ADMIN+"','"+USER+"') ORDER BY id"));
        for(String table:List.of("skill_categories","skills")) assertEquals(BEFORE.get(table),digest(jdbc,"SELECT * FROM "+table+" ORDER BY id"));
    }
}
