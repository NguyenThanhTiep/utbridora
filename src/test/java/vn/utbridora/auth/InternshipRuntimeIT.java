package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.modules.cooperation.application.*;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;

/** Real HTTP/security/MySQL/owner ports. Approved Placement/SD/verification/accepted invitation are
 * explicit synthetic prerequisites, not evidence of the later approval or invitation workflow.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.t5.placement-delivery.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternshipRuntimeIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static {MYSQL.start();}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    @Autowired PlacementLifecyclePublisher publisher;@Autowired PlacementLifecycleConsumer consumer;@Autowired TransactionTemplate tx;
    final JsonMapper json=JsonMapper.builder().build();final String password="Synthetic-week1-"+UUID.randomUUID();String hash;
    String school,enterprise,term,sd,coordinatorGrant,hrGrant,placement;UserAccount admin,staff,coordinator,hr,mentor,student,otherStudent;
    PlacementLifecycleEvent approved;
    LocalDateTime grantFrom,grantUntil;
    @BeforeAll void hash() {hash=encoder.encode(password);}
    @BeforeEach void fixture() {
        grantFrom=LocalDateTime.now(ZoneOffset.UTC).minusHours(1);grantUntil=LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
        school=org(Organization.Type.UNIVERSITY);enterprise=org(Organization.Type.ENTERPRISE);term=id();
        admin=user(Role.ADMIN,null);staff=user(Role.SCHOOL_STAFF,school);coordinator=user(Role.ENTERPRISE,enterprise);
        hr=user(Role.ENTERPRISE,enterprise);mentor=user(Role.ENTERPRISE,enterprise);student=user(Role.STUDENT,school);otherStudent=user(Role.STUDENT,school);
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2026-01-01','2028-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,"T-"+id());
        sd=id();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_WEEK1_REVIEWED',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",sd,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,'t3:coordinator:confirm')",sd);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_WEEK1_REVIEWED',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        coordinatorGrant=grant("COORDINATOR",coordinator.id,staff.id,null,sd,null);
        hrGrant=grant("HR",hr.id,coordinator.id,coordinatorGrant,null,null);
        placement=placement(student.id);
        approved=publish(placement);
    }
    String placement(String studentId) {
        String value=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'APPROVED',2,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2026-01-01','2028-01-01',UTC_TIMESTAMP(6),'SYNTHETIC_WEEK1_REVIEWED')",value,studentId,school,enterprise,term,id(),id(),id());
        return value;
    }
    String grant(String kind,String subject,String grantor,String parent,String delegation,String group) {
        String value=id();
        // Fixed common bounds fit the reviewed root and every parent; all data is synthetic.
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_WEEK1_REVIEWED',true,?,?)",
            value,kind,subject,enterprise,school,term,group,parent,delegation,grantor,grantFrom,grantUntil);
        return value;
    }
    String org(Organization.Type type) {var o=new Organization();o.name="Synthetic "+id();o.type=type;o.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(o).id;}
    UserAccount user(Role role,String org) {
        var u=new UserAccount();u.email=id()+"@example.test";u.displayName="Synthetic";u.passwordHash=hash;u.status=AccountStatus.ACTIVE;users.saveAndFlush(u);
        var m=new Membership();m.userId=u.id;m.organizationId=org;m.role=role;memberships.saveAndFlush(m);return u;
    }
    PlacementLifecycleEvent publish(String placementId) {return tx.execute(status->publisher.publish(staff.id,placementId));}
    TrustedEventContext context(PlacementLifecycleEvent event) {
        return new TrustedEventContext("T5",jdbc.queryForObject("SELECT delivery_operation_id FROM placement_outbox WHERE event_id=?",String.class,event.eventId()),id());
    }
    EventConsumeResult consume(PlacementLifecycleEvent event) {return consumer.consume(context(event),event);}
    String caseId(String p) {return jdbc.queryForObject("SELECT id FROM internship_cases WHERE placement_id=?",String.class,p);}
    long count(String table,String column,String value) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Long.class,value);}
    static String id() {return UUID.randomUUID().toString();}
    class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf,assignment;
        HttpResponse<String> call(String method,String path,Object body,String key,boolean token) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(25)).header("Content-Type","application/json");
            if(key!=null)request.header("Idempotency-Key",key);if(assignment!=null)request.header("X-Context-Assignment-Id",assignment);if(token&&csrf!=null)request.header("X-CSRF-TOKEN",csrf);
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {csrf=json.readTree(ok(call("GET","/auth/csrf",null,null,false),200)).get("token").asString();}
        String post(String path,Object input) throws Exception {return ok(call("POST",path,input,id(),true),path.equals("/t3/cohorts")||path.equals("/t3/tasks")?201:200);}
    }
    Browser login(UserAccount u,String assignment) throws Exception {var b=new Browser();b.csrf();ok(b.call("POST","/auth/login",Map.of("email",u.email,"password",password),null,true),200);b.csrf();b.assignment=assignment;return b;}
    String ok(HttpResponse<String> result,int status) {assertEquals(status,result.statusCode(),result.body());return result.body();}
    String field(String body,String name) {return json.readTree(body).get(name).asString();}
    String cohort(Browser b) throws Exception {return field(b.post("/t3/cohorts",Map.of("termId",term,"code","W1-"+id(),"name","Week 1")),"cohortId");}
    String group(Browser b,String cohort,List<String> placements) throws Exception {
        String intake=b.post("/t3/cohorts/"+cohort+"/members:batch",new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.BatchCases(1L,placements.stream().map(UUID::fromString).toList(),null,null));
        assertTrue(intake.contains("ADDED"),intake);
        String preview=b.post("/t3/cohorts/"+cohort+"/groups:auto",Map.of("mode","PREVIEW","groupSize",10,"expectedCohortVersion",2));
        String confirm=b.post("/t3/cohorts/"+cohort+"/groups:auto",Map.of("mode","CONFIRM","planId",field(preview,"planId"),"expectedCohortVersion",2));
        return json.readTree(confirm).get(0).get("groupId").asString();
    }
    Map<String,Object> task(String group,List<String> cases) {return Map.of("groupId",group,"title","Week 1 task","description","Synthetic task","dueAt",Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString(),"priority","NORMAL","caseIds",cases);}
    void activate(String p) {jdbc.update("UPDATE placements SET state='ACTIVE',version=3,effective_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE) WHERE id=?",p);}

    @Test void activatedBeforeApprovedProjectsLatestSourceAndDeduplicates() {
        activate(placement);var activated=publish(placement);
        var applied=consume(activated);assertEquals("ACK_APPLIED",applied.outcome());assertEquals(3,applied.appliedAggregateVersion());
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT state FROM internship_cases WHERE placement_id=?",String.class,placement));
        assertEquals("ACK_DUPLICATE",consume(activated).outcome());assertEquals("ACK_STALE",consume(approved).outcome());
        assertEquals(1,count("internship_cases","placement_id",placement));assertEquals(2,count("internship_case_projection_history","case_id",caseId(placement)));
        assertEquals("REJECT",consumer.consume(new TrustedEventContext("T5",id(),id()),activated).outcome());
    }
    @Test void consumerProjectionAuditFailureRollsBackInboxAndCase() {
        jdbc.execute("ALTER TABLE internship_case_projection_history ADD CONSTRAINT reject_projection CHECK(event_id<>'"+approved.eventId()+"')");
        try {assertEquals("RETRY",consume(approved).outcome());assertEquals(0,count("internship_cases","placement_id",placement));assertEquals(0,count("placement_inbox","event_id",approved.eventId()));}
        finally {jdbc.execute("ALTER TABLE internship_case_projection_history DROP CHECK reject_projection");}
        assertEquals("ACK_APPLIED",consume(approved).outcome());
    }
    @Test void inboxConcurrentRedeliveryProducesOneCaseAndOneAudit() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var one=executor.submit(()->consume(approved));var two=executor.submit(()->consume(approved));
            assertEquals(Set.of("ACK_APPLIED","ACK_DUPLICATE"),Set.of(one.get(20,TimeUnit.SECONDS).outcome(),two.get(20,TimeUnit.SECONDS).outcome()));
        }
        assertEquals(1,count("internship_cases","placement_id",placement));assertEquals(1,count("placement_inbox","event_id",approved.eventId()));
    }
    @Test void realCohortGroupingAndTaskGuardUseCurrentPlacementAndAssignments() throws Exception {
        assertEquals("ACK_APPLIED",consume(approved).outcome());
        var hrBrowser=login(hr,hrGrant);String cohort=cohort(hrBrowser);String group=group(hrBrowser,cohort,List.of(placement));
        String mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);var mentorBrowser=login(mentor,mentorGrant);String caseId=caseId(placement);
        ok(mentorBrowser.call("POST","/t3/tasks",task(group,List.of(caseId)),id(),true),409);
        activate(placement);consume(publish(placement));
        String key=id();var input=task(group,List.of(caseId));
        String created=ok(mentorBrowser.call("POST","/t3/tasks",input,key,true),201);
        assertEquals(created,ok(mentorBrowser.call("POST","/t3/tasks",input,key,true),201));
        String task=field(created,"taskId");assertEquals(1,count("internship_tasks","id",task));
        var studentBrowser=login(student,null);ok(studentBrowser.call("GET","/t3/tasks/"+task,null,null,false),200);
        var outsider=login(otherStudent,null);
        ok(outsider.call("GET","/t3/tasks/"+task,null,null,false),404);
        ok(outsider.call("GET","/t3/cases/"+caseId,null,null,false),404);
        String due=URLEncoder.encode(field(created,"dueAt"),java.nio.charset.StandardCharsets.UTF_8);
        String list=ok(mentorBrowser.call("GET","/t3/tasks?groupId="+group+"&dueFrom="+due+"&dueUntil="+due,null,null,false),200);
        assertEquals(1,json.readTree(list).get("totalElements").asInt());
        // T5 cancelled after activation; no cancellation event has been consumed locally.
        jdbc.update("UPDATE placements SET state='CANCELLED',version=4,cancelled_at=UTC_TIMESTAMP(6) WHERE id=?",placement);
        ok(mentorBrowser.call("POST","/t3/tasks",input,id(),true),409);
        jdbc.update("UPDATE internship_context_assignments SET active=false WHERE id=?",coordinatorGrant);
        ok(hrBrowser.call("GET","/t3/cohorts?termId="+term,null,null,false),403);
        assertEquals("[]",ok(hrBrowser.call("GET","/t3/context-assignments/me",null,null,false),200));
    }
    @Test void taskAuditFailureRollsBackTaskAssignmentsAndLedger() throws Exception {
        activate(placement);consume(publish(placement));var h=login(hr,hrGrant);String cohort=cohort(h),group=group(h,cohort,List.of(placement));
        String mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);var b=login(mentor,mentorGrant);String key=id();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_task CHECK(action<>'TASK_ASSIGNED' OR actor_id<>'"+mentor.id+"')");
        try {ok(b.call("POST","/t3/tasks",task(group,List.of(caseId(placement))),key,true),500);assertEquals(0,count("internship_tasks","group_id",group));assertEquals(0,count("operation_records","idempotency_key",key));}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK reject_task");}
    }
    @Test void noAdminBusinessBypassNoForgedContextAndCsrfIsRequired() throws Exception {
        var b=login(admin,hrGrant);ok(b.call("GET","/t3/cohorts",null,null,false),403);
        b=login(hr,id());ok(b.call("GET","/t3/cohorts",null,null,false),403);
        b=login(hr,hrGrant);ok(b.call("POST","/t3/cohorts",Map.of("termId",term,"code","W1","name","Synthetic"),id(),false),403);
        ok(new Browser().call("GET","/t3/context-assignments/me",null,null,false),401);
        ok(b.call("POST","/t3/placement-events",approved,id(),true),404);
    }
    @Test void outboxDeliversThroughRealConsumerAndMarksAckAfterCommit() {
        new PlacementOutboxWorker(jdbc,consumer).deliverPending();
        assertEquals("DONE",jdbc.queryForObject("SELECT state FROM placement_outbox WHERE event_id=?",String.class,approved.eventId()));
        assertEquals(1,count("internship_cases","placement_id",placement));
    }
    @Test void currentAuthorityIsCheckedBeforeReplayButTermStateIsBusinessValidation() throws Exception {
        var h=login(hr,hrGrant);String key=id();var request=Map.of("termId",term,"code","REPLAY","name","Synthetic");
        String first=ok(h.call("POST","/t3/cohorts",request,key,true),201);
        jdbc.update("UPDATE terms SET status='CLOSED' WHERE id=?",term);
        assertEquals(first,ok(h.call("POST","/t3/cohorts",request,key,true),201));
        ok(h.call("POST","/t3/cohorts",Map.of("termId",term,"code","NEW","name","Synthetic"),id(),true),409);
        jdbc.update("UPDATE internship_context_assignments SET active=false WHERE id=?",hrGrant);
        ok(h.call("POST","/t3/cohorts",request,key,true),403);
    }
    @Test void sourceAndProducerAuditOutboxRollBackTogether() {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_placement CHECK(action<>'PLACEMENT_ACTIVATED' OR resource_id<>'"+placement+"')");
        try {
            assertThrows(RuntimeException.class,()->tx.executeWithoutResult(status->{activate(placement);publisher.publish(staff.id,placement);}));
            assertEquals("APPROVED",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));
            assertEquals(1,count("placement_outbox","placement_id",placement));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK reject_placement");}
    }
    @Test void committedOutboxDoesNotOverrideSourceScopeAndRetryHasFiveStepsThenDlq() {
        var forged=new PlacementLifecycleEvent(approved.eventId(),approved.eventType(),1,placement,2L,2L,student.id,id(),enterprise,term,
            approved.effectiveAt(),approved.occurredAt(),approved.traceId(),null,approved.decisionRef());
        jdbc.update("UPDATE placement_outbox SET payload_json=? WHERE event_id=?",json.writeValueAsString(forged),forged.eventId());
        assertEquals("SCOPE_MISMATCH",consume(forged).reasonCode());
        jdbc.update("UPDATE placement_outbox SET payload_json=? WHERE event_id=?",json.writeValueAsString(approved),approved.eventId());
        jdbc.update("UPDATE placements SET version=1 WHERE id=?",placement);
        var worker=new PlacementOutboxWorker(jdbc,consumer);
        for(int attempt=0;attempt<6;attempt++) {
            jdbc.update("UPDATE placement_outbox SET next_attempt_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND) WHERE event_id=?",approved.eventId());
            worker.deliverPending();
            assertEquals(attempt<5?"PENDING":"DLQ",jdbc.queryForObject("SELECT state FROM placement_outbox WHERE event_id=?",String.class,approved.eventId()));
        }
        assertEquals(5,jdbc.queryForObject("SELECT attempt_count FROM placement_outbox WHERE event_id=?",Integer.class,approved.eventId()));
        assertEquals(0,count("internship_cases","placement_id",placement));
    }
    @Test void cancellationTerminatesWithoutReopeningFromALaterActiveEnvelope() {
        activate(placement);consume(publish(placement));
        jdbc.update("UPDATE placements SET state='CANCELLED',version=4,cancelled_at=UTC_TIMESTAMP(6) WHERE id=?",placement);
        var cancelled=tx.execute(status->publisher.publish(staff.id,placement,"EARLY_TERMINATION"));
        assertEquals("ACK_APPLIED",consume(cancelled).outcome());
        assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM internship_cases WHERE placement_id=?",String.class,placement));
        jdbc.update("UPDATE placements SET state='ACTIVE',version=5 WHERE id=?",placement);
        assertEquals("STATE_CONFLICT",consume(publish(placement)).reasonCode());
        assertEquals(4,jdbc.queryForObject("SELECT last_applied_version FROM internship_cases WHERE placement_id=?",Integer.class,placement));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="frontend.e2e",matches="true")
    void realHrAndMentorBrowserFlow() throws Exception {
        activate(placement);consume(publish(placement));var h=login(hr,hrGrant);String cohort=cohort(h),group=group(h,cohort,List.of(placement));
        String mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- internship-runtime.spec.ts"):
            new ProcessBuilder("npm","run","test:e2e","--","internship-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));var env=builder.environment();
        env.put("API_PROXY_TARGET","http://localhost:"+port);env.put("E2E_REPORT_FILE","../target/internship-browser-results.xml");
        env.put("E2E_T3_HR_EMAIL",hr.email);env.put("E2E_T3_MENTOR_EMAIL",mentor.email);env.put("E2E_PASSWORD",password);
        env.put("E2E_T3_HR_ASSIGNMENT",hrGrant);env.put("E2E_T3_MENTOR_ASSIGNMENT",mentorGrant);
        env.put("E2E_T3_COHORT",cohort);env.put("E2E_T3_GROUP",group);
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/internship-browser.log"));
        Process process=builder.start();if(!process.waitFor(120,TimeUnit.SECONDS)) {process.destroyForcibly();fail("See target/internship-browser.log");}
        assertEquals(0,process.exitValue(),"See target/internship-browser.log");
    }
}
