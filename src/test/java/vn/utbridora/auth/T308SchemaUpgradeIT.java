package vn.utbridora.auth;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

class T308SchemaUpgradeIT {
    @Test void upgradeFromV29PreservesCaseAndEnforcesReportConstraints() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("29").load().migrate();
            try(var c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var s=c.createStatement()) {
                String user=UUID.randomUUID().toString(),school=UUID.randomUUID().toString(),enterprise=UUID.randomUUID().toString(),term=UUID.randomUUID().toString(),caseId=UUID.randomUUID().toString();
                s.executeUpdate("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES ('"+user+"','t308-upgrade@example.test','unusable','Synthetic','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO organizations(id,name,type,status,created_at) VALUES ('"+school+"','School','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6)),('"+enterprise+"','Enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES ('"+term+"','"+school+"','UPGRADE','2026-01-01','2027-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES ('"+caseId+"',UUID(),'"+user+"','"+school+"','"+enterprise+"','"+term+"','IN_PROGRESS',2,2)");
                var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
                assertEquals(3,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
                try(var r=s.executeQuery("SELECT version,state FROM internship_cases WHERE id='"+caseId+"'")){assertTrue(r.next());assertEquals(2,r.getLong(1));assertEquals("IN_PROGRESS",r.getString(2));}
                try(var r=s.executeQuery("SELECT COUNT(*) FROM role_permissions WHERE permission_code='t3:comments:write'")){assertTrue(r.next());assertEquals(2,r.getInt(1));}
                String report=UUID.randomUUID().toString();
                s.executeUpdate("INSERT INTO internship_weekly_reports VALUES ('"+report+"','"+caseId+"','2026-11-09','2026-11-17 16:59:59',1,1)");
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO internship_weekly_reports VALUES (UUID(),'"+caseId+"','2026-11-09','2026-11-17 16:59:59',1,1)"));
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO internship_weekly_reports VALUES (UUID(),'"+caseId+"','2026-11-10','2026-11-17 16:59:59',1,1)"));
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO internship_weekly_reports VALUES (UUID(),UUID(),'2026-11-16','2026-11-24 16:59:59',1,1)"));
                s.executeUpdate("INSERT INTO internship_weekly_report_revisions VALUES ('"+report+"',1,1,'DRAFT','Work','','Plan',NULL,NULL,NULL,false)");
                assertThrows(SQLException.class,()->s.executeUpdate("UPDATE internship_weekly_report_revisions SET state='SUBMITTED' WHERE report_id='"+report+"'"));
                assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM internship_weekly_reports WHERE id='"+report+"'"));
            }
        }
    }
}
