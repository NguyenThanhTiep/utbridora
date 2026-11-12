package vn.utbridora.auth;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import static org.junit.jupiter.api.Assertions.*;

/** DEV07 browser/HTTP/MySQL harness. Fixture pattern inherited from DEV06 T308ReportRuntimeIT.
 * Never replaces a provider or connects to a developer/shared database. ACTIVE fixtures are
 * explicit synthetic prerequisites, not evidence of T5 approval/activation workflows.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.local.enabled=false","app.t5.placement-delivery.enabled=false",
    "logging.level.org.springframework.jdbc.core=INFO","app.t3.week3.http.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T309WorkflowBrowserIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static final java.nio.file.Path FILE_ROOT=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {try{return java.nio.file.Files.createTempDirectory("t309-browser-files-");}catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
    static {MYSQL.start();} // PER_CLASS Spring injection needs the mapped port before @BeforeAll.
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("app.files.storage-root",FILE_ROOT::toString);
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    @TestBean(methodName="controlledClock") Clock week3Clock;
    static final class ControlledClock extends Clock {
        volatile Instant now=Instant.now();
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        @Override public Instant instant(){return now;}
    }
    static Clock controlledClock(){return new ControlledClock();}
    final JsonMapper json=JsonMapper.builder().build();final String password="Synthetic-T309-"+UUID.randomUUID();String hash;
    String school,enterprise,term,caseId,placement,cohort,group,task,assignment,mentorGrant,hrGrant;
    UserAccount admin,staff,coordinator,hr,mentor,student,outsider;
    @BeforeAll void passwordHash(){hash=encoder.encode(password);}
    @BeforeEach void fixture() {
        assertTrue(MYSQL.isRunning()); // Every write below targets this private ephemeral container.
        ((ControlledClock)week3Clock).now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        school=org(Organization.Type.UNIVERSITY);enterprise=org(Organization.Type.ENTERPRISE);term=id();
        admin=user(Role.ADMIN,null);staff=user(Role.SCHOOL_STAFF,school);coordinator=user(Role.ENTERPRISE,enterprise);
        hr=user(Role.ENTERPRISE,enterprise);mentor=user(Role.ENTERPRISE,enterprise);student=user(Role.STUDENT,school);
        outsider=user(Role.STUDENT,org(Organization.Type.UNIVERSITY));
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2020-01-01','2099-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,"SYNTHETIC-"+id());
        String delegation=id();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_T309_PREREQUISITE',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,'t3:coordinator:confirm')",delegation);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_T309_PREREQUISITE',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        String parent=grant("COORDINATOR",coordinator.id,staff.id,null,delegation,null);
        hrGrant=grant("HR",hr.id,coordinator.id,parent,null,null);
        placement=id();caseId=id();cohort=id();group=id();task=id();assignment=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2020-01-01','2099-01-01',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'SYNTHETIC_T309_PREREQUISITE')",placement,student.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,placement,student.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,'Synthetic T309',1)",cohort,enterprise,school,term,"T309-"+id());
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'G1',1)",group,cohort);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,caseId,group);
        jdbc.update("INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,'Synthetic T309','Synthetic description',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'NORMAL',true,1)",task,group);
        jdbc.update("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',2)",assignment,task,caseId,student.id);
        mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
    }
    private String grant(String kind,String subject,String grantor,String parent,String delegation,String assignedGroup) {
        String grant=id();
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_T309_PREREQUISITE',true,DATE_SUB(UTC_DATE(),INTERVAL 1 HOUR),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY))",
                grant,kind,subject,enterprise,school,term,assignedGroup,parent,delegation,grantor);return grant;
    }
    private String org(Organization.Type type) {var o=new Organization();o.name="Synthetic "+id();o.type=type;o.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(o).id;}
    private UserAccount user(Role role,String org) {
        var u=new UserAccount();u.email=id()+"@example.test";u.displayName="Synthetic";u.passwordHash=hash;u.status=AccountStatus.ACTIVE;users.saveAndFlush(u);
        var m=new Membership();m.userId=u.id;m.organizationId=org;m.role=role;memberships.saveAndFlush(m);return u;
    }
    private long count(String table,String field,String value){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+field+"=?",Long.class,value);}
    private static String id(){return UUID.randomUUID().toString();}


    @Test void browserUsesRealSubmissionCommentReportAndFileApis() throws Exception {
        var config=new LinkedHashMap<String,String>();
        config.put("password",password);config.put("studentEmail",student.email);config.put("mentorEmail",mentor.email);
        config.put("hrEmail",hr.email);config.put("outsiderEmail",outsider.email);
        config.put("mentorContext",mentorGrant);config.put("hrContext",hrGrant);
        config.put("cohortId",cohort);config.put("groupId",group);config.put("caseId",caseId);
        config.put("assignmentId",assignment);config.put("taskId",task);
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- internship-week3-runtime.spec.ts"):
            new ProcessBuilder("npm","run","test:e2e","--","internship-week3-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));var env=builder.environment();
        env.put("API_PROXY_TARGET","http://localhost:"+port);
        env.put("E2E_REPORT_FILE","../target/t3-09-runtime-browser.xml");
        env.put("E2E_T309_CONFIG",json.writeValueAsString(config));
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/t3-09-runtime-browser.log"));
        Process process=builder.start();
        try {
            if(!process.waitFor(240,TimeUnit.SECONDS)) fail("Browser timed out: target/t3-09-runtime-browser.log");
            assertEquals(0,process.exitValue(),"See target/t3-09-runtime-browser.log");
        } finally {if(process.isAlive())process.destroyForcibly();}
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT state FROM internship_task_assignments WHERE id=?",String.class,assignment));
        assertEquals(2,count("internship_submissions","assignment_id",assignment));
        assertEquals("https://example.test/t309/v1",jdbc.queryForObject("SELECT url FROM internship_submissions WHERE assignment_id=? AND version_no=1",String.class,assignment));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM internship_submission_reviews r JOIN internship_submissions s ON s.id=r.submission_id WHERE s.assignment_id=?",Integer.class,assignment));
        assertEquals(2,count("internship_task_comments","assignment_id",assignment));
        assertEquals(2,count("internship_weekly_reports","case_id",caseId));
        assertEquals("Browser report v1",jdbc.queryForObject("SELECT v.completed_work FROM internship_weekly_report_revisions v JOIN internship_weekly_reports r ON r.id=v.report_id WHERE r.case_id=? AND r.week_start='2026-11-09' AND v.revision_no=1",String.class,caseId));
        assertEquals("Mentor report feedback",jdbc.queryForObject("SELECT v.comment FROM internship_weekly_report_reviews v JOIN internship_weekly_reports r ON r.id=v.report_id WHERE r.case_id=? AND v.revision_no=1",String.class,caseId));
    }
}
