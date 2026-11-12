package vn.utbridora.auth;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Upgrade populated week-2 tables in an ephemeral database; never modifies shared data. */
class T307SchemaUpgradeIT {
    @Test void v27AssignmentsSurviveOrderedV28V29AndOwnershipConstraintsAreEnforced() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            migrate(mysql,"27");
            String user=id(),school=id(),enterprise=id(),term=id(),caseId=id(),cohort=id(),group=id(),task=id(),assignment=id();
            try(var c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword())) {
                sql(c,"INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES (?,'upgrade-t307@example.test','synthetic-unusable','Synthetic','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",user);
                sql(c,"INSERT INTO organizations(id,name,type,status,created_at) VALUES (?,'Synthetic School','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6)),(?,'Synthetic Enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",school,enterprise);
                sql(c,"INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,'UPGRADE','2026-01-01','2027-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school);
                sql(c,"INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,id(),user,school,enterprise,term);
                sql(c,"INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name) VALUES (?,?,?,?,'UPGRADE','Synthetic Cohort')",cohort,enterprise,school,term);
                sql(c,"INSERT INTO internship_groups(id,cohort_id,code) VALUES (?,?,'G1')",group,cohort);
                sql(c,"INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,'Original task','Original description','2027-01-01','NORMAL',true,1)",task,group);
                sql(c,"INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',9)",assignment,task,caseId,user);
                assertEquals(1,migrate(mysql,"28"));assertEquals(1,migrate(mysql,"29"));
                var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("29").load();
                flyway.validate();assertEquals(0,flyway.migrate().migrationsExecuted);
                try(var s=c.createStatement();var r=s.executeQuery("SELECT state,version,current_submission_id FROM internship_task_assignments WHERE id='"+assignment+"'")) {
                    assertTrue(r.next());assertEquals("IN_PROGRESS",r.getString(1));assertEquals(9,r.getLong(2));assertNull(r.getString(3));
                }
                String artifact=id();
                sql(c,"INSERT INTO internship_submissions VALUES (?,?,1,'https://example.test/v1','Original note',UTC_TIMESTAMP(6),false)",artifact,assignment);
                sql(c,"UPDATE internship_task_assignments SET current_submission_id=? WHERE id=?",artifact,assignment);
                String secondTask=id(),secondAssignment=id();
                sql(c,"INSERT INTO internship_tasks SELECT ?,group_id,title,description,due_at,priority,late_allowed,version FROM internship_tasks WHERE id=?",secondTask,task);
                sql(c,"INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',1)",secondAssignment,secondTask,caseId,user);
                assertThrows(SQLException.class,()->sql(c,"UPDATE internship_task_assignments SET current_submission_id=? WHERE id=?",artifact,secondAssignment));
                assertThrows(SQLException.class,()->sql(c,"INSERT INTO internship_submissions VALUES (?,?,1,'https://example.test/duplicate',NULL,UTC_TIMESTAMP(6),false)",id(),assignment));
                String review="INSERT INTO internship_submission_reviews VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6),10)";
                assertThrows(SQLException.class,()->sql(c,review,id(),artifact,2,"ACCEPT","Wrong version",user));
                assertThrows(SQLException.class,()->sql(c,review,id(),artifact,1,"ACCEPT","   ",user));
                sql(c,review,id(),artifact,1,"REQUEST_REVISION","Revise",user);
                assertThrows(SQLException.class,()->sql(c,review,id(),artifact,1,"ACCEPT","Duplicate",user));
                assertThrows(SQLException.class,()->sql(c,"DELETE FROM internship_submissions WHERE id=?",artifact));
                sql(c,"INSERT INTO internship_task_comments VALUES (?,?,?,'Schema only',UTC_TIMESTAMP(6))",id(),assignment,user);
                assertThrows(SQLException.class,()->sql(c,"INSERT INTO internship_task_comments VALUES (?,?,?,' ',UTC_TIMESTAMP(6))",id(),assignment,user));
            }
        }
    }
    private static int migrate(MySQLContainer<?> mysql,String target) {
        return Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target(target).load().migrate().migrationsExecuted;
    }
    private static void sql(Connection c,String sql,Object... values) throws SQLException {
        try(var s=c.prepareStatement(sql)) {for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}
    }
    private static String id(){return UUID.randomUUID().toString();}
}
