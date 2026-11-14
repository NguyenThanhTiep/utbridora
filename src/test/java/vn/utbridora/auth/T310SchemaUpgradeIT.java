package vn.utbridora.auth;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Populated V37 -> V38 on a private disposable database; validates pointer/unique/FK constraints. */
class T310SchemaUpgradeIT {
    @Test void forwardUpgradePreservesCasesAndEvaluationPointersCannotCrossRoots() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("37").load().migrate();
            try(var c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword())) {
                String user=id(),school=id(),enterprise=id(),term=id(),caseId=id(),otherCase=id(),template=id(),evaluation=id(),other=id();
                sql(c,"INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES (?,'t310-upgrade@example.test','unusable','Synthetic','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",user);
                sql(c,"INSERT INTO organizations(id,name,type,status,created_at) VALUES (?,'Synthetic School','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6)),(?,'Synthetic Enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",school,enterprise);
                sql(c,"INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,'T310','2026-01-01','2027-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school);
                for(String caseKey:new String[]{caseId,otherCase}) sql(c,"INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',7,2)",caseKey,id(),user,school,enterprise,term);
                var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
                assertEquals(1,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
                try(var s=c.createStatement();var r=s.executeQuery("SELECT version,state FROM internship_cases WHERE id='"+caseId+"'")) {
                    assertTrue(r.next());assertEquals(7,r.getLong(1));assertEquals("IN_PROGRESS",r.getString(2));
                }
                sql(c,"INSERT INTO internship_rubric_templates VALUES (?,1,?,?,'{}')",template,school,term);
                sql(c,"INSERT INTO internship_evaluations(id,case_id) VALUES (?,?),(?,?)",evaluation,caseId,other,otherCase);
                assertThrows(SQLException.class,()->sql(c,"INSERT INTO internship_evaluations(id,case_id) VALUES (?,?)",id(),caseId));
                String insert="INSERT INTO internship_evaluation_versions VALUES (?,1,1,'DRAFT',?,1,'{}',76.00,?,UTC_TIMESTAMP(6))";
                sql(c,insert,evaluation,template,user);
                assertThrows(SQLException.class,()->sql(c,insert,evaluation,template,user));
                assertThrows(SQLException.class,()->sql(c,"UPDATE internship_evaluations SET current_version=1 WHERE id=?",other));
                assertThrows(SQLException.class,()->sql(c,"UPDATE internship_evaluations SET published_version=1 WHERE id=?",other));
                sql(c,"UPDATE internship_evaluations SET current_version=1 WHERE id=?",evaluation);
                assertThrows(SQLException.class,()->sql(c,"DELETE FROM internship_evaluation_versions WHERE evaluation_id=?",evaluation));
                assertThrows(SQLException.class,()->sql(c,"DELETE FROM internship_rubric_templates WHERE id=?",template));
                assertThrows(SQLException.class,()->sql(c,"INSERT INTO internship_evaluation_files VALUES (?,1,?)",evaluation,id()));
                assertThrows(SQLException.class,()->sql(c,"INSERT INTO internship_evaluation_versions VALUES (?,2,1,'DRAFT',?,1,'{}',100.01,?,UTC_TIMESTAMP(6))",evaluation,template,user));
            }
        }
    }
    private static void sql(Connection c,String text,Object... values) throws SQLException {
        try(var s=c.prepareStatement(text)) {for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
