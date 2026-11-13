package vn.utbridora.auth;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class Week3MigrationIT {
    @Test void cleanAndEveryPublishedLineageConvergeWithoutRepairOrHistoryChanges() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.4").withUsername("root")) {
            mysql.start();
            List<String> reference = null;
            String[][] paths = {{"main", "0"}, {"main", "27"}, {"main", "28"}, {"main", "29"},
                {"main", "31"}, {"t2", "28"}, {"t4", "28"}, {"t4", "29"}, {"t4", "30"},
                {"t4", "31"}, {"t5", "28"}, {"t5", "29"}, {"t5", "30"}};
            for (int i = 0; i < paths.length; i++) {
                String lineage = paths[i][0], target = paths[i][1], db = "week3_" + i;
                try (var c = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()); var s = c.createStatement()) {
                    s.execute("CREATE DATABASE " + db);
                }
                String url = mysql.getJdbcUrl().replace("/" + mysql.getDatabaseName(), "/" + db);
                String[] locations = lineage.equals("main") ? new String[]{"classpath:db/migration"}
                    : new String[]{"classpath:db/migration/base", "classpath:db/legacy/" + lineage};
                var builder = Flyway.configure().dataSource(url, mysql.getUsername(), mysql.getPassword()).locations(locations);
                if (!target.equals("0")) builder.target(target).load().migrate();
                var before = history(url, mysql);
                var flyway = Flyway.configure().dataSource(url, mysql.getUsername(), mysql.getPassword()).locations(locations).load();
                flyway.migrate(); flyway.validate();
                assertEquals(0, flyway.migrate().migrationsExecuted, lineage + target);
                var after = history(url, mysql);
                before.forEach((key, value) -> assertEquals(value, after.get(key), "Changed installed history " + lineage + target + ":" + key));
                assertEquals("33", flyway.info().current().getVersion().getVersion());
                try (var c = DriverManager.getConnection(url, mysql.getUsername(), mysql.getPassword()); var s = c.createStatement()) {
                    var schema = new ArrayList<String>();
                    try (var rows = s.executeQuery("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COALESCE(COLUMN_DEFAULT,'<null>') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME<>'flyway_schema_history' AND COLUMN_NAME<>'gpa' ORDER BY TABLE_NAME,COLUMN_NAME")) {
                        while (rows.next()) schema.add(rows.getString(1)+"|"+rows.getString(2)+"|"+rows.getString(3)+"|"+rows.getString(4)+"|"+rows.getString(5));
                    }
                    if (reference == null) reference = schema;
                    assertEquals(reference, schema, lineage + target);
                    assertTrue(schema.stream().anyMatch(v -> v.startsWith("placement_history|")));
                    assertTrue(schema.stream().anyMatch(v -> v.startsWith("student_preference_history|")));
                    assertTrue(schema.stream().anyMatch(v -> v.startsWith("matching_privacy_releases|")));
                    assertTrue(schema.stream().anyMatch(v -> v.startsWith("internship_weekly_report_reviews|")));
                    try (var rows = s.executeQuery("SELECT enabled FROM matching_privacy_release_settings")) { assertTrue(rows.next()); assertFalse(rows.getBoolean(1)); }
                    try (var rows = s.executeQuery("SELECT COUNT(*) FROM role_permissions WHERE permission_code IN ('file:upload','file:download')")) { rows.next(); assertEquals(8, rows.getInt(1)); }
                }
            }
        }
    }
    private static Map<String, String> history(String url, MySQLContainer<?> mysql) throws SQLException {
        Map<String, String> result = new TreeMap<>();
        try (var c = DriverManager.getConnection(url, mysql.getUsername(), mysql.getPassword()); var s = c.createStatement()) {
            try (var rows = s.executeQuery("SELECT version,script,checksum,success FROM flyway_schema_history WHERE version IS NOT NULL")) {
                while (rows.next()) result.put(rows.getString(1), rows.getString(2)+"|"+rows.getString(3)+"|"+rows.getBoolean(4));
            } catch (SQLException absent) { if (!"42S02".equals(absent.getSQLState())) throw absent; }
        }
        return result;
    }
}
