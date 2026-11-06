package vn.utbridora.auth;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.testcontainers.containers.MySQLContainer;

class MigrationUpgradeIT {
    @Test void upgradesV2DataWithoutChangingIdentityAndEnforcesCoreConstraints() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString())) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("2").load().migrate();
            String user=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users VALUES ('"+user+"','upgrade@example.test','unusable-test-hash','Upgrade Test','ACTIVE',0,0,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO memberships VALUES ('"+member+"','"+user+"',NULL,'ADMIN',1,UTC_TIMESTAMP(6))");
            }
            Flyway flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertEquals(5,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                try(ResultSet r=s.executeQuery("SELECT email FROM users WHERE id='"+user+"'")) {assertTrue(r.next());assertEquals("upgrade@example.test",r.getString(1));}
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO memberships (id,user_id,role,active,created_at) VALUES ('"+UUID.randomUUID()+"','"+user+"','ADMIN',1,UTC_TIMESTAMP(6))"));
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO memberships (id,user_id,role,active,created_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','ADMIN',1,UTC_TIMESTAMP(6))"));
                try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('roles','user_roles')")) {r.next();assertEquals(0,r.getInt(1));}
                try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM core_locks WHERE lock_name='GLOBAL_ADMIN'")) {r.next();assertEquals(1,r.getInt(1));}
            }
        }
    }
}
