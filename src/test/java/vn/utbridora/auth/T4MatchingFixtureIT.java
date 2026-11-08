package vn.utbridora.auth;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Execute the shared fixture only in an isolated database, including its repeat-run path. */
class T4MatchingFixtureIT {
    @Test void seedsOneThousandReferencedCasesAndCanBeReapplied() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("utbridora").withPassword(UUID.randomUUID().toString())) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                    .load().migrate();
            var script = new FileSystemResource(Path.of("scripts", "fixtures", "t4", "T4-02_seed.sql"));
            try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                ScriptUtils.executeSqlScript(connection, script);
                ScriptUtils.executeSqlScript(connection, script);
                try (var query = connection.createStatement()) {
                    try (var rows = query.executeQuery("SELECT expected_outcome,COUNT(*) FROM t4_fixture_cases GROUP BY expected_outcome")) {
                        var counts = new java.util.HashMap<String, Integer>();
                        while (rows.next()) counts.put(rows.getString(1), rows.getInt(2));
                        assertEquals(java.util.Map.of("MATCH", 340, "MISSING_DATA", 330, "NOT_MATCH", 330), counts);
                    }
                    try (var rows = query.executeQuery("SELECT COUNT(*) FROM t4_fixture_cases c LEFT JOIN users u ON u.id=c.student_user_id LEFT JOIN organizations s ON s.id=c.school_org_id LEFT JOIN organizations e ON e.id=c.enterprise_org_id WHERE u.id IS NULL OR s.id IS NULL OR e.id IS NULL")) {
                        assertTrue(rows.next()); assertEquals(0, rows.getInt(1));
                    }
                    try (var rows = query.executeQuery("SELECT COUNT(*) FROM users WHERE email LIKE 't4.student%@example.test'")) {
                        assertTrue(rows.next()); assertEquals(100, rows.getInt(1));
                    }
                }
            }
        }
    }
}
