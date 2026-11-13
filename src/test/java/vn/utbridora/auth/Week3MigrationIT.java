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
                String[] locations = locations(lineage, Integer.parseInt(target));
                var builder = Flyway.configure().dataSource(url, mysql.getUsername(), mysql.getPassword()).locations(locations);
                if (!target.equals("0")) builder.target(target).load().migrate();
                var before = history(url, mysql);
                var immutable = lineage.equals("t4") ? seedLegacyCriteria(url, mysql, Integer.parseInt(target)) : null;
                var flyway = Flyway.configure().dataSource(url, mysql.getUsername(), mysql.getPassword()).locations(locations).load();
                flyway.migrate(); flyway.validate();
                assertEquals(0, flyway.migrate().migrationsExecuted, lineage + target);
                var after = history(url, mysql);
                before.forEach((key, value) -> assertEquals(value, after.get(key), "Changed installed history " + lineage + target + ":" + key));
                if (immutable != null) assertEquals(immutable, criteria(url, mysql), "Changed immutable criteria " + lineage + target);
                assertEquals("33", flyway.info().current().getVersion().getVersion());
                var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, mysql.getUsername(), mysql.getPassword());
                var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
                var submission = new vn.utbridora.auth.modules.internship.infrastructure.JdbcSubmissionSession(jdbc,
                    org.mockito.Mockito.mock(vn.utbridora.auth.operations.OperationLedger.class),
                    org.mockito.Mockito.mock(vn.utbridora.auth.audit.AuditService.class));
                new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource))
                    .executeWithoutResult(status -> submission.requireBaseline());
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
    private static String[] locations(String lineage, int installed) {
        if (lineage.equals("main")) return new String[]{"classpath:db/migration"};
        var paths = new ArrayList<>(List.of("classpath:db/migration/base", "classpath:db/legacy/" + lineage));
        // V30/V31 remain available only to validate a database that already applied them.
        // In particular, a V28/V29/V30 database must never newly backfill immutable JSON via V31.
        if (lineage.equals("t4") && installed >= 30) paths.add("classpath:db/legacy/t4-v30");
        if (lineage.equals("t4") && installed >= 31) paths.add("classpath:db/legacy/t4-v31");
        return paths.toArray(String[]::new);
    }
    private static List<String> seedLegacyCriteria(String url, MySQLContainer<?> mysql, int installed) throws SQLException {
        String user=UUID.randomUUID().toString(),school=UUID.randomUUID().toString(),org=UUID.randomUUID().toString(),term=UUID.randomUUID().toString(),demand=UUID.randomUUID().toString();
        String json=installed>=31?"{\"required\":{\"minGpa\":null}}":"{\"required\":{}}",hash="a".repeat(64);
        try(var c=DriverManager.getConnection(url,mysql.getUsername(),mysql.getPassword())) {
            sql(c,"INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES (?,'lineage@example.test','synthetic-unusable','Synthetic','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",user);
            sql(c,"INSERT INTO organizations(id,name,type,status,created_at) VALUES (?,'Synthetic School','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6)),(?,'Synthetic Enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",school,org);
            sql(c,"INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,'LINEAGE','2026-01-01','2027-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school);
            sql(c,"INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES (?,?,?,?,'DRAFT','VALID',1,1,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",demand,org,school,term,hash,json);
            sql(c,"INSERT INTO demand_revisions VALUES (?,1,1,?,?,?,?, 'CREATE',UTC_TIMESTAMP(6))",demand,hash,json,json,user);
        }
        return criteria(url,mysql);
    }
    private static List<String> criteria(String url, MySQLContainer<?> mysql) throws SQLException {
        try(var c=DriverManager.getConnection(url,mysql.getUsername(),mysql.getPassword());var s=c.createStatement();var rows=s.executeQuery("SELECT d.data_json,r.data_json,r.snapshot_json,d.criteria_hash,r.criteria_hash FROM demands d JOIN demand_revisions r ON r.demand_id=d.id")) {
            assertTrue(rows.next());return List.of(rows.getString(1),rows.getString(2),rows.getString(3),rows.getString(4),rows.getString(5));
        }
    }
    private static void sql(Connection c,String text,Object...values) throws SQLException {
        try(var s=c.prepareStatement(text)) {for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}
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
