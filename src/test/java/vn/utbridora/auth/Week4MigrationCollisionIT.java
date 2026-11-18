package vn.utbridora.auth;

import java.nio.file.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Diagnostic only: no production migration/history repair or convergence implementation.
 * T1 fixture: origin/feature/t1-07-file-scanning at 32478e6b4a7431b33989a8ac318a5cc46fd9eb4b.
 */
class Week4MigrationCollisionIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withUsername("root");
    static int sequence;
    String url;
    @TempDir Path temporary;
    @BeforeAll static void start(){MYSQL.start();}
    @AfterAll static void stop(){MYSQL.stop();}
    @BeforeEach void database() throws Exception {
        String name="collision_"+(++sequence);
        try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
        url=MYSQL.getJdbcUrl().replace("/"+MYSQL.getDatabaseName(),"/"+name);
    }
    private org.flywaydb.core.api.configuration.FluentConfiguration configuration(){return Flyway.configure().dataSource(url,MYSQL.getUsername(),MYSQL.getPassword());}
    private Flyway t1() throws Exception {
        Path source=Path.of("src/main/resources/db/migration");
        try(var files=Files.walk(source)) {
            for(Path file:files.filter(Files::isRegularFile).toList()) {
                String name=file.getFileName().toString();
                if(name.startsWith("V38__") || name.startsWith("V39__"))continue;
                Path destination=temporary.resolve(source.relativize(file));Files.createDirectories(destination.getParent());Files.copy(file,destination);
            }
        }
        Files.copy(Path.of("src/test/resources/migration-collision/t1/V38__durable_private_file_scans.sql"),temporary.resolve("V38__durable_private_file_scans.sql"));
        return configuration().locations("filesystem:"+temporary.toAbsolutePath().toString().replace('\\','/'))
            .javaMigrations(new db.migration.base.V32__converge_week3_lineages()).load();
    }
    private String history() throws Exception {
        var value=new StringBuilder();
        try(var c=DriverManager.getConnection(url,MYSQL.getUsername(),MYSQL.getPassword());var s=c.createStatement();var r=s.executeQuery("SELECT version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")) {
            while(r.next())value.append(r.getString(1)).append('|').append(r.getString(2)).append('|').append(r.getString(3)).append('|').append(r.getBoolean(4)).append('\n');
        }
        return value.toString();
    }
    @Test void combinedDefaultLocationsRejectDuplicate38BeforeDdl() throws Exception {
        var combined=configuration().locations("classpath:db/migration","classpath:migration-collision/t1").load();
        var error=assertThrows(FlywayException.class,combined::migrate);
        assertTrue(error.getMessage().contains("Found more than one migration with version 38"),error.getMessage());
        try(var c=DriverManager.getConnection(url,MYSQL.getUsername(),MYSQL.getPassword());var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('files','internship_evaluations')")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
    }
    @Test void t1HistoryCannotUseT3DefaultEvenThoughBothSay38() throws Exception {
        var original=t1();original.migrate();original.validate();assertEquals(0,original.migrate().migrationsExecuted);
        String before=history();assertTrue(before.contains("V38__durable_private_file_scans.sql"));
        var validation=configuration().load().validateWithResult();
        assertFalse(validation.validationSuccessful);
        assertTrue(validation.invalidMigrations.stream().anyMatch(m->"38".equals(m.version)));
        assertEquals(before,history());
    }
    @Test void t3HistoryCannotUseT1LocationsAndRemainsUnchanged() throws Exception {
        var original=configuration().load();original.migrate();original.validate();assertEquals(0,original.migrate().migrationsExecuted);
        String before=history();assertTrue(before.contains("V39__t3_grade_visibility.sql"));
        var validation=t1().validateWithResult();assertFalse(validation.validationSuccessful);
        assertTrue(validation.invalidMigrations.stream().anyMatch(m->"38".equals(m.version)));
        assertEquals(before,history());
    }
}
