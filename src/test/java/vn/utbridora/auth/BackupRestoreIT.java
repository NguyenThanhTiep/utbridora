package vn.utbridora.auth;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** T1-08: real dump/restore of synthetic data on an ephemeral runner, never staging/shared Docker. */
class BackupRestoreIT {
    @Test void restoresEveryTableAndImmutableFlywayHistoryBeforeNoOpMigration() throws Exception {
        try (var mysql=new MySQLContainer<>("mysql:8.4").withUsername("root").withDatabaseName("rehearsal_source")) {
            mysql.start();
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            flyway.migrate();
            try (var c=connection(mysql,mysql.getDatabaseName())) {
                ScriptUtils.executeSqlScript(c,new FileSystemResource("scripts/fixtures/t4/T4-02_seed.sql"));
            }
            var before=fingerprints(mysql,mysql.getDatabaseName());
            assertTrue(before.size()>90,"Include all application tables and Flyway history");
            assertTrue(before.get("users").rows()>=100,"Populated synthetic owner data required");
            String clone="rehearsal_restore_"+UUID.randomUUID().toString().replace("-","");
            try (var c=connection(mysql,mysql.getDatabaseName());var s=c.createStatement()) {s.execute("CREATE DATABASE `"+clone+"`");}
            // Credentials stay inside the container. No dump, password, email or row values enter the report.
            long started=System.nanoTime();
            exec(mysql,"export MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\"; exec mysqldump -uroot --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF --routines --events --triggers --result-file=/tmp/rehearsal.sql rehearsal_source");
            var digest=exec(mysql,"sha256sum /tmp/rehearsal.sql").split(" ")[0];
            exec(mysql,"export MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\"; exec mysql -uroot "+clone+" < /tmp/rehearsal.sql");
            var after=fingerprints(mysql,clone);
            assertEquals(before,after,"Every row in every table must survive restore, including empty tables and history");
            var restored=Flyway.configure().dataSource(url(mysql,clone),mysql.getUsername(),mysql.getPassword()).load();
            restored.validate();assertEquals(0,restored.migrate().migrationsExecuted);
            assertEquals(before,fingerprints(mysql,clone),"No-op migration must not change restored history/data");
            var report=new LinkedHashMap<String,Object>();
            report.put("task","T1-08");report.put("status","PASS");report.put("completedAt",Instant.now().toString());
            report.put("commit",System.getenv().getOrDefault("GITHUB_SHA","LOCAL_UNCOMMITTED"));
            report.put("environment",Map.of("mysql","mysql:8.4","java",System.getProperty("java.version"),"isolated",true));
            report.put("dumpSha256",digest);report.put("durationMs",(System.nanoTime()-started)/1_000_000);
            report.put("tables",before);report.put("restoredRows",before.values().stream().mapToLong(TableDigest::rows).sum());
            report.put("flywayValidation","PASS");report.put("noOpMigration",true);
            report.put("limits",List.of("Synthetic ephemeral DB only; no staging deployment or real-data recovery", "Database rehearsal does not back up private file storage or certify RPO/RTO"));
            Files.createDirectories(Path.of("target"));
            Files.writeString(Path.of("target/week5-restore-verification.json"),JsonMapper.builder().build().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
    record TableDigest(long rows,String sha256) {}
    private static Map<String,TableDigest> fingerprints(MySQLContainer<?> mysql,String schema) throws Exception {
        var result=new TreeMap<String,TableDigest>();
        try (var c=connection(mysql,schema)) {
            var tables=new ArrayList<String>();
            try(var s=c.createStatement();var r=s.executeQuery("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")) {while(r.next())tables.add(r.getString(1));}
            for(String table:tables) {
                var rows=new ArrayList<String>();
                try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM `"+table.replace("`","``")+"`")) {
                    while(r.next()) {
                        var hash=MessageDigest.getInstance("SHA-256");
                        for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                            byte[] value=r.getBytes(i);
                            hash.update(ByteBuffer.allocate(4).putInt(value==null?-1:value.length).array());
                            if(value!=null)hash.update(value);
                        }
                        rows.add(HexFormat.of().formatHex(hash.digest()));
                    }
                }
                Collections.sort(rows);var hash=MessageDigest.getInstance("SHA-256");
                for(String row:rows)hash.update(HexFormat.of().parseHex(row));
                result.put(table,new TableDigest(rows.size(),HexFormat.of().formatHex(hash.digest())));
            }
        }
        return result;
    }
    private static String url(MySQLContainer<?> mysql,String schema) {return mysql.getJdbcUrl().replace("/"+mysql.getDatabaseName(),"/"+schema);}
    private static Connection connection(MySQLContainer<?> mysql,String schema) throws SQLException {return DriverManager.getConnection(url(mysql,schema),mysql.getUsername(),mysql.getPassword());}
    private static String exec(MySQLContainer<?> mysql,String command) throws Exception {
        var result=mysql.execInContainer("sh","-c",command);
        assertEquals(0,result.getExitCode(),"Isolated MySQL backup/restore command failed");return result.getStdout().strip();
    }
}
