package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import static org.junit.jupiter.api.Assertions.*;

/** DEV06 Comment HTTP/Core/T5/JPA/JDBC/MySQL evidence using integrated V28/V29.
 * Never replaces a provider or connects to a developer/shared database. ACTIVE fixtures are
 * explicit synthetic prerequisites, not evidence of T5 approval/activation workflows.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.local.enabled=false","app.t5.placement-delivery.enabled=false",
    "logging.level.org.springframework.jdbc.core=INFO","app.t3.week3.http.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T308ReportRuntimeIT {
    // Only this disposable container permits fault-injection triggers under binary logging.
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
    static final java.nio.file.Path FILE_ROOT=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {try{return java.nio.file.Files.createTempDirectory("t308-report-files-");}catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
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
    final JsonMapper json=JsonMapper.builder().build();final String password="Synthetic-T307-"+UUID.randomUUID();String hash;
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
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_T307_PREREQUISITE',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,'t3:coordinator:confirm')",delegation);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_T307_PREREQUISITE',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        String parent=grant("COORDINATOR",coordinator.id,staff.id,null,delegation,null);
        hrGrant=grant("HR",hr.id,coordinator.id,parent,null,null);
        placement=id();caseId=id();cohort=id();group=id();task=id();assignment=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2020-01-01','2099-01-01',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'SYNTHETIC_T307_PREREQUISITE')",placement,student.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,placement,student.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,'Synthetic T307',1)",cohort,enterprise,school,term,"T307-"+id());
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'G1',1)",group,cohort);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,caseId,group);
        jdbc.update("INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,'Synthetic T307','Synthetic description',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'NORMAL',true,1)",task,group);
        jdbc.update("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',2)",assignment,task,caseId,student.id);
        mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
    }
    private String grant(String kind,String subject,String grantor,String parent,String delegation,String assignedGroup) {
        String grant=id();
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_T307_PREREQUISITE',true,DATE_SUB(UTC_DATE(),INTERVAL 1 HOUR),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY))",
                grant,kind,subject,enterprise,school,term,assignedGroup,parent,delegation,grantor);return grant;
    }
    private String org(Organization.Type type) {var o=new Organization();o.name="Synthetic "+id();o.type=type;o.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(o).id;}
    private UserAccount user(Role role,String org) {
        var u=new UserAccount();u.email=id()+"@example.test";u.displayName="Synthetic";u.passwordHash=hash;u.status=AccountStatus.ACTIVE;users.saveAndFlush(u);
        var m=new Membership();m.userId=u.id;m.organizationId=org;m.role=role;memberships.saveAndFlush(m);return u;
    }
    private class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf,context;
        HttpResponse<String> call(String method,String path,Object body,String key,boolean csrfHeader) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
            if(key!=null)request.header("Idempotency-Key",key);if(context!=null)request.header("X-Context-Assignment-Id",context);
            if(csrfHeader&&csrf!=null)request.header("X-CSRF-TOKEN",csrf);
            return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {csrf=node(call("GET","/api/v1/auth/csrf",null,null,false),200).get("token").asString();}
    }
    private Browser login(UserAccount u,String context) throws Exception {
        var b=new Browser();b.csrf();node(b.call("POST","/api/v1/auth/login",Map.of("email",u.email,"password",password),null,true),200);b.csrf();b.context=context;return b;
    }
    private JsonNode node(HttpResponse<String> response,int status) {assertEquals(status,response.statusCode(),response.body());return json.readTree(response.body());}
    private String submissions(){return "/api/v1/t3/assignments/"+assignment+"/submissions";}
    private String reviews(String submission){return "/api/v1/t3/submissions/"+submission+"/reviews";}
    private Map<String,Object> input(long version,String url){var value=new LinkedHashMap<String,Object>();value.put("expectedAssignmentVersion",version);value.put("fileIds",List.of());value.put("url",url);value.put("note",null);return value;}
    private long count(String table,String field,String value){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+field+"=?",Long.class,value);}
    private static String id(){return UUID.randomUUID().toString();}


    private String path(String report) {return "/api/v1/t3/reports/"+report;}
    private String upload(Browser owner,String report) throws Exception {
        String boundary="T308Boundary"+id();StringBuilder body=new StringBuilder();
        for(var entry:Map.of("resourceType","WEEKLY_REPORT","resourceId",report,"purpose","WEEKLY_REPORT").entrySet())
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n\r\n").append(entry.getValue()).append("\r\n");
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.7\nSynthetic T308 test only\n%%EOF\r\n--").append(boundary).append("--\r\n");
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/files"))
            .header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",owner.csrf).header("Idempotency-Key",id())
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return node(owner.http.send(request,HttpResponse.BodyHandlers.ofString()),202).get("fileId").asString();
    }
    @Test void evidenceUsesRealFileServiceAndRequiresCleanScanState() throws Exception {
        var owner=login(student,null);var reviewer=login(mentor,mentorGrant);String report=create(owner);String file=upload(owner,report);
        node(reviewer.call("GET","/api/v1/files/"+file+"/metadata",null,null,true),404);
        node(owner.call("PATCH",path(report),edit(1,"Evidence",List.of(file)),id(),true),200);
        node(owner.call("POST",path(report)+"/submit",Map.of("expectedVersion",2),id(),true),409);
        assertEquals("DRAFT",node(owner.call("GET",path(report),null,null,true),200).get("state").asString());
        node(reviewer.call("GET","/api/v1/files/"+file+"/metadata",null,null,true),200);
        node(login(outsider,null).call("GET","/api/v1/files/"+file+"/metadata",null,null,true),404);
        // Explicit scanner-output fixture: tests consumer enforcement, not antivirus execution.
        jdbc.update("UPDATE files SET scan_status='CLEAN' WHERE id=?",file);
        node(owner.call("POST",path(report)+"/submit",Map.of("expectedVersion",2),id(),true),200);
        var download=reviewer.call("GET","/api/v1/files/"+file+"/content",null,null,true);
        assertEquals(200,download.statusCode(),download.body());assertTrue(download.body().startsWith("%PDF-1.7"));
        assertEquals(1,count("internship_weekly_report_files","report_id",report));
        jdbc.update("UPDATE files SET scan_status='REJECTED' WHERE id=?",file);
        node(reviewer.call("GET","/api/v1/files/"+file+"/content",null,null,true),409);
    }
    private Map<String,Object> draft() {return Map.of("caseId",caseId,"weekStart",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).with(java.time.DayOfWeek.MONDAY).toString(),"completedWork","First version","difficulties","","nextPlan","Next","evidenceFileIds",List.of());}
    private String create(Browser owner) throws Exception {return node(owner.call("POST","/api/v1/t3/reports",draft(),id(),true),201).get("reportId").asString();}
    private Map<String,Object> edit(long version,String work,List<String> files) {return Map.of("expectedVersion",version,"completedWork",work,"difficulties","","nextPlan","Next","evidenceFileIds",files);}
    @Test void reportLifecyclePreservesReviewedHistoryAndReplay() throws Exception {
        var owner=login(student,null);var reviewer=login(mentor,mentorGrant);String report=create(owner);
        node(owner.call("PATCH",path(report),edit(1,"Updated draft",List.of()),id(),true),200);
        String key=id();var submission=Map.of("expectedVersion",2);
        var submitted=node(owner.call("POST",path(report)+"/submit",submission,key,true),200);
        assertEquals("SUBMITTED",submitted.get("state").asString());
        assertEquals(submitted,node(owner.call("POST",path(report)+"/submit",submission,key,true),200));
        node(reviewer.call("POST",path(report)+"/reviews",Map.of("expectedVersion",3,"comment","Good progress"),id(),true),200);
        assertEquals("Good progress",jdbc.queryForObject("SELECT comment FROM internship_weekly_report_reviews WHERE report_id=?",String.class,report));
        var feedback=node(owner.call("GET",path(report)+"/reviews",null,null,true),200);
        assertEquals("Good progress",feedback.get("comment").asString());assertEquals(mentor.id,feedback.get("reviewerUserId").asString());
        node(login(outsider,null).call("GET",path(report)+"/reviews",null,null,true),404);
        node(owner.call("POST",path(report)+"/revisions",Map.of("expectedVersion",4,"reasonCode","CORRECTION"),id(),true),201);
        node(owner.call("PATCH",path(report),edit(5,"Second revision",List.of()),id(),true),200);
        var old=node(owner.call("GET",path(report)+"?revisionNo=1",null,null,true),200);
        assertEquals("Updated draft",old.get("completedWork").asString());assertEquals("REVIEWED",old.get("state").asString());
        var current=node(owner.call("GET",path(report),null,null,true),200);assertEquals(2,current.get("revisionNo").asInt());
        assertEquals("Second revision",current.get("completedWork").asString());
        node(owner.call("GET",path(report)+"/reviews",null,null,true),404);
        assertEquals(feedback,node(owner.call("GET",path(report)+"/reviews?revisionNo=1",null,null,true),200));
        assertEquals(1,node(owner.call("GET","/api/v1/t3/reports?caseId="+caseId,null,null,true),200).get("totalElements").asInt());
    }
    @Test void scopeSourceAndRevocationAreRechecked() throws Exception {
        var owner=login(student,null);String report=create(owner);var reviewer=login(mentor,mentorGrant);
        node(login(outsider,null).call("GET",path(report),null,null,true),404);
        node(owner.call("POST",path(report)+"/submit",Map.of("expectedVersion",1),id(),true),200);
        node(login(hr,hrGrant).call("POST",path(report)+"/reviews",Map.of("expectedVersion",2,"comment","HR"),id(),true),404);
        String key=id();var body=Map.of("expectedVersion",2,"comment","Mentor");
        node(reviewer.call("POST",path(report)+"/reviews",body,key,true),200);
        jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",hrGrant);
        node(reviewer.call("POST",path(report)+"/reviews",body,key,true),403);
        jdbc.update("UPDATE placements SET state='CANCELLED',version=version+1,cancelled_at=UTC_TIMESTAMP(6) WHERE id=?",placement);
        node(owner.call("POST",path(report)+"/revisions",Map.of("expectedVersion",3,"reasonCode","CORRECTION"),id(),true),409);
        node(owner.call("GET",path(report),null,null,true),200);
    }
    @Test void duplicatesStaleVersionsAndCsrfAreRejected() throws Exception {
        var owner=login(student,null);String report=create(owner);
        node(owner.call("POST","/api/v1/t3/reports",draft(),id(),true),409);
        node(owner.call("PATCH",path(report),edit(1,"Edit",List.of()),id(),false),403);
        node(owner.call("PATCH",path(report),edit(1,"Edit",List.of()),id(),true),200);
        node(owner.call("PATCH",path(report),edit(1,"Stale",List.of()),id(),true),409);
        assertEquals(1,count("internship_weekly_reports","case_id",caseId));
    }
    @Test void deadlineNanosecondRemainderSurvivesDatabaseReload() throws Exception {
        var owner=login(student,null);String report=create(owner);
        var before=node(owner.call("GET",path(report),null,null,true),200);
        Instant submittedAt=Instant.parse(before.get("dueAt").asString()).plusNanos(1);
        ((ControlledClock)week3Clock).now=submittedAt;
        var submitted=node(owner.call("POST",path(report)+"/submit",Map.of("expectedVersion",1),id(),true),200);
        assertTrue(submitted.get("lateFlag").asBoolean());
        var reloaded=node(owner.call("GET",path(report),null,null,true),200);
        assertEquals(submittedAt,Instant.parse(reloaded.get("submittedAt").asString()));assertTrue(reloaded.get("lateFlag").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT submitted_submicro_nanos FROM internship_weekly_report_revisions WHERE report_id=?",Integer.class,report));
    }
    @Test void concurrentDifferentKeysWithSameVersionHaveOneWinner() throws Exception {
        var owner=login(student,null);String report=create(owner);var body=edit(1,"Concurrent edit",List.of());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->owner.call("PATCH",path(report),body,id(),true));
            var b=pool.submit(()->owner.call("PATCH",path(report),body,id(),true));
            var statuses=new ArrayList<>(List.of(a.get().statusCode(),b.get().statusCode()));Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);
        }
        assertEquals(2,node(owner.call("GET",path(report),null,null,true),200).get("version").asInt());
    }
    @Test void auditFailureRollsBackEntireReportAndLedger() throws Exception {
        var owner=login(student,null);String key=id();var body=draft();
        jdbc.execute("CREATE TRIGGER t308_report_fault BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='WEEKLY_REPORT_DRAFT_SAVED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic report audit fault'; END IF; END");
        try {node(owner.call("POST","/api/v1/t3/reports",body,key,true),500);assertEquals(0,count("internship_weekly_reports","case_id",caseId));}
        finally {jdbc.execute("DROP TRIGGER t308_report_fault");}
        node(owner.call("POST","/api/v1/t3/reports",body,key,true),201);
    }
    @Test void concurrentSameKeyCreatesOneReport() throws Exception {
        var owner=login(student,null);String key=id();var body=draft();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var calls=new ArrayList<Future<HttpResponse<String>>>();
            for(int i=0;i<20;i++) calls.add(pool.submit(()->owner.call("POST","/api/v1/t3/reports",body,key,true)));
            for(var call:calls){int status=call.get().statusCode();assertTrue(status==201||status==409,"status="+status);}
        }
        String report=node(owner.call("POST","/api/v1/t3/reports",body,key,true),201).get("reportId").asString();
        assertEquals(1,count("internship_weekly_reports","case_id",caseId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='WEEKLY_REPORT_DRAFT_SAVED' AND resource_id=?",Integer.class,report));
    }
}
