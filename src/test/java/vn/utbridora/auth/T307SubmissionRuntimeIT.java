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

/** Real HTTP/Core/T5/JPA/JDBC/MySQL evidence using the integrated T1 V28 and T3 V29.
 * Never replaces a provider or connects to a developer/shared database. ACTIVE fixtures are
 * explicit synthetic prerequisites, not evidence of T5 approval/activation workflows.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.local.enabled=false","app.t5.placement-delivery.enabled=false",
    "logging.level.org.springframework.jdbc.core=INFO"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T307SubmissionRuntimeIT {
    // Only this disposable container permits fault-injection triggers under binary logging.
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
    static {MYSQL.start();} // PER_CLASS Spring injection needs the mapped port before @BeforeAll.
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    @TestBean(methodName="controlledClock") Clock submissionClock;
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
        ((ControlledClock)submissionClock).now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
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

    @Test void flywayReallyAppliedV28BeforeV29AndUrlPersistsAcrossHttpReads() throws Exception {
        var versions=jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version IN ('28','29') AND success=true ORDER BY installed_rank",String.class);
        assertEquals(List.of("28","29"),versions);var owner=login(student,null);
        var first=node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201);
        assertEquals(1,first.get("versionNo").asInt());assertEquals(1,count("internship_submissions","assignment_id",assignment));
        var history=node(owner.call("GET",submissions(),null,null,false),200);assertEquals(first,history.get("content").get(0));
        assertEquals("SUBMITTED",jdbc.queryForObject("SELECT state FROM internship_task_assignments WHERE id=?",String.class,assignment));
    }

    @Test void realRevisionCycleKeepsV1V2AndRejectsOldReview() throws Exception {
        var owner=login(student,null);var reviewer=login(mentor,mentorGrant);
        var first=node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201);String firstId=first.get("submissionId").asString();
        var pending=owner.call("GET",reviews(firstId),null,null,false);
        assertEquals(204,pending.statusCode());assertEquals("",pending.body());
        var firstReview=node(reviewer.call("POST",reviews(firstId),Map.of("expectedAssignmentVersion",3,"verdict","REQUEST_REVISION","comment","Revise"),id(),true),201);
        assertEquals(firstReview,node(login(student,null).call("GET",reviews(firstId),null,null,false),200));
        node(owner.call("PATCH","/api/v1/t3/assignments/"+assignment+"/status",Map.of("expectedVersion",4,"state","IN_PROGRESS"),id(),true),200);
        var second=node(owner.call("POST",submissions(),input(5,"https://example.test/v2"),id(),true),201);String secondId=second.get("submissionId").asString();
        assertEquals(2,second.get("versionNo").asInt());
        assertEquals("STALE_SUBMISSION",node(reviewer.call("POST",reviews(firstId),Map.of("expectedAssignmentVersion",6,"verdict","ACCEPT","comment","Old"),id(),true),409).get("errorCode").asString());
        assertEquals(204,owner.call("GET",reviews(secondId),null,null,false).statusCode());
        var secondReview=node(reviewer.call("POST",reviews(secondId),Map.of("expectedAssignmentVersion",6,"verdict","ACCEPT","comment","Accepted"),id(),true),201);
        var reloaded=login(student,null);
        long operationsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM operation_records",Long.class);
        long auditsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs",Long.class);
        assertEquals(firstReview,node(reloaded.call("GET",reviews(firstId),null,null,false),200));
        assertEquals(secondReview,node(reloaded.call("GET",reviews(secondId),null,null,false),200));
        assertEquals(secondReview,node(reviewer.call("GET",reviews(secondId),null,null,false),200));
        assertEquals(operationsBefore,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records",Long.class));
        assertEquals(auditsBefore,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs",Long.class));
        var history=node(owner.call("GET",submissions(),null,null,false),200).get("content");assertEquals(second,history.get(0));assertEquals(first,history.get(1));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT state FROM internship_task_assignments WHERE id=?",String.class,assignment));
        assertEquals(2,count("internship_submission_reviews","submission_id",firstId)+count("internship_submission_reviews","submission_id",secondId));
        assertEquals("Revise",jdbc.queryForObject("SELECT comment FROM internship_submission_reviews WHERE submission_id=?",String.class,firstId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id=? AND action='SUBMISSION_REVISED'",Long.class,student.id));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id=? AND action='SUBMISSION_REVIEWED'",Long.class,mentor.id));
        var page=node(owner.call("GET",submissions()+"?page=1&size=1",null,null,false),200);
        assertEquals(2,page.get("totalElements").asInt());assertEquals(first,page.get("content").get(0));
        jdbc.update("UPDATE placements SET state='CANCELLED',cancelled_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=?",placement);
        assertEquals(first,node(owner.call("GET",submissions()+"?page=1&size=1",null,null,false),200).get("content").get(0));
        assertEquals(firstReview,node(reloaded.call("GET",reviews(firstId),null,null,false),200));
        assertEquals(secondReview,node(reloaded.call("GET",reviews(secondId),null,null,false),200));
    }

    @Test void reviewReadProtectsPrivateFeedbackAndHandlesMissingResources() throws Exception {
        var owner=login(student,null);
        String submission=node(owner.call("POST",submissions(),input(2,"https://example.test/read"),id(),true),201).get("submissionId").asString();
        var other=login(outsider,null);
        node(other.call("GET",reviews(submission),null,null,false),404); // Even before a review exists.
        var reviewer=login(mentor,mentorGrant);
        node(reviewer.call("POST",reviews(submission),Map.of("expectedAssignmentVersion",3,"verdict","REQUEST_REVISION","comment","Private feedback"),id(),true),201);
        var anonymous=new Browser();
        node(anonymous.call("GET",reviews(submission),null,null,false),401);
        var denied=node(other.call("GET",reviews(submission),null,null,false),404);
        assertFalse(denied.toString().contains("Private feedback"));
        node(owner.call("GET",reviews(id()),null,null,false),404);
        node(owner.call("GET",reviews("invalid"),null,null,false),400);
        String otherGroup=id();jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'OTHER',1)",otherGroup,cohort);
        var otherMentor=user(Role.ENTERPRISE,enterprise);
        String otherGrant=grant("MENTOR",otherMentor.id,hr.id,hrGrant,null,otherGroup);
        node(login(otherMentor,otherGrant).call("GET",reviews(submission),null,null,false),404);
        jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",hrGrant);
        node(reviewer.call("GET",reviews(submission),null,null,false),404);
    }

    @ParameterizedTest @CsvSource({"0,false,201,false","1,true,201,true","1,false,422,false"})
    void deadlineBoundaryUsesServerClockAndStoredPolicy(int secondsLate,boolean allowed,int status,boolean late) throws Exception {
        var due=submissionClock.instant();
        jdbc.update("UPDATE internship_tasks SET due_at=?,late_allowed=? WHERE id=?",LocalDateTime.ofInstant(due,ZoneOffset.UTC),allowed,task);
        ((ControlledClock)submissionClock).now=due.plusSeconds(secondsLate);
        var response=node(login(student,null).call("POST",submissions(),input(2,"https://example.test/deadline"),id(),true),status);
        if(status==201) assertEquals(late,response.get("lateFlag").asBoolean());
        else {assertEquals("SUBMISSION_LATE",response.get("errorCode").asString());assertRolledBack();}
    }

    @Test void wrongMentorAndRevokedParentCannotReviewOrReplayPrivateResults() throws Exception {
        var owner=login(student,null);var reviewer=login(mentor,mentorGrant);
        String submission=node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201).get("submissionId").asString();
        String otherGroup=id();jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'OTHER',1)",otherGroup,cohort);
        var otherMentor=user(Role.ENTERPRISE,enterprise);String otherGrant=grant("MENTOR",otherMentor.id,hr.id,hrGrant,null,otherGroup);
        var review=Map.of("expectedAssignmentVersion",3,"verdict","ACCEPT","comment","Accepted");
        node(login(otherMentor,otherGrant).call("POST",reviews(submission),review,id(),true),403);
        String key=id();node(reviewer.call("POST",reviews(submission),review,key,true),201);
        jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",hrGrant);
        node(reviewer.call("POST",reviews(submission),review,key,true),403);
        node(reviewer.call("GET",submissions(),null,null,false),404);
        assertEquals(1,count("internship_submission_reviews","submission_id",submission));
    }

    @Test void fileModeStaysClosedAndInvalidUrlsCannotCreateArtifacts() throws Exception {
        var owner=login(student,null);
        var fileRequest=new LinkedHashMap<>(input(2,"https://example.test/unused"));fileRequest.put("url",null);fileRequest.put("fileIds",List.of(id()));
        assertEquals("DEPENDENCY_UNAVAILABLE",node(owner.call("POST",submissions(),fileRequest,id(),true),503).get("errorCode").asString());
        for(String url:List.of("http://example.test/plain","https://user@example.test/private")) {
            assertEquals("SCHEMA_VALIDATION",node(owner.call("POST",submissions(),input(2,url),id(),true),400).get("errorCode").asString());
        }
        assertRolledBack();
    }

    @Test void realSecurityRejectsAnonymousMissingCsrfAndCrossSchoolOwner() throws Exception {
        var anonymous=new Browser();anonymous.csrf();node(anonymous.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),401);
        var owner=login(student,null);node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),false),403);
        var other=login(outsider,null);node(other.call("GET",submissions(),null,null,false),404);
        node(other.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),404);assertEquals(0,count("internship_submissions","assignment_id",assignment));
    }

    @Test void realSourceGuardRejectsPlacementThatIsNoLongerActive() throws Exception {
        var owner=login(student,null);jdbc.update("UPDATE placements SET state='CANCELLED',cancelled_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=?",placement);
        node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),409);assertEquals(0,count("internship_submissions","assignment_id",assignment));
        assertEquals("IN_PROGRESS",jdbc.queryForObject("SELECT state FROM internship_cases WHERE id=?",String.class,caseId));
    }

    @Test void explicitHandoverControlsReviewOfHistoricalTasksAndIsLostOnAnotherMove() throws Exception {
        var owner=login(student,null);var humanResources=login(hr,hrGrant);
        String submission=node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201).get("submissionId").asString();
        String next=id(),third=id();
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'NEXT',1),(?,?,'THIRD',1)",next,cohort,third,cohort);
        String nextGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,next);
        node(humanResources.call("POST","/api/v1/t3/groups/"+group+"/members:move",Map.of("caseId",caseId,"toGroupId",next,
                "expectedCaseVersion",2,"expectedFromGroupVersion",1,"expectedToGroupVersion",1,"reasonCode","SYNTHETIC_MOVE"),id(),true),200);
        var reviewer=login(mentor,nextGrant);var review=Map.of("expectedAssignmentVersion",3,"verdict","ACCEPT","comment","Historical task");
        node(reviewer.call("POST",reviews(submission),review,id(),true),403);
        node(humanResources.call("POST","/api/v1/t3/cases/"+caseId+"/mentor-handover",Map.of("caseId",caseId,"newMentorUserId",mentor.id,
                "expectedCaseVersion",3,"reasonCode","SYNTHETIC_HANDOVER"),id(),true),200);
        node(reviewer.call("POST",reviews(submission),review,id(),true),401); // Owner invalidates the old session.
        reviewer=login(mentor,nextGrant);String key=id();node(reviewer.call("POST",reviews(submission),review,key,true),201);
        assertEquals("Historical task",node(reviewer.call("GET",reviews(submission),null,null,false),200).get("comment").asString());
        node(humanResources.call("POST","/api/v1/t3/groups/"+next+"/members:move",Map.of("caseId",caseId,"toGroupId",third,
                "expectedCaseVersion",4,"expectedFromGroupVersion",2,"expectedToGroupVersion",1,"reasonCode","SYNTHETIC_MOVE_AGAIN"),id(),true),200);
        node(reviewer.call("POST",reviews(submission),review,key,true),403);
        node(reviewer.call("GET",reviews(submission),null,null,false),404);
        assertEquals(1,count("internship_submission_reviews","submission_id",submission));
    }

    @ParameterizedTest @ValueSource(strings={"SUBMISSION_REVIEWED","SUBMISSION_REVISED"})
    void revisionAndReviewAuditFailuresRollBackAllEffectsAndPermitRetry(String action) throws Exception {
        var owner=login(student,null);var reviewer=login(mentor,mentorGrant);
        var first=node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201);
        String firstId=first.get("submissionId").asString();
        boolean revising=action.equals("SUBMISSION_REVISED");
        if(revising) {
            node(reviewer.call("POST",reviews(firstId),Map.of("expectedAssignmentVersion",3,"verdict","REQUEST_REVISION","comment","Revise"),id(),true),201);
            node(owner.call("PATCH","/api/v1/t3/assignments/"+assignment+"/status",Map.of("expectedVersion",4,"state","IN_PROGRESS"),id(),true),200);
        }
        var browser=revising?owner:reviewer;
        String route=revising?submissions():reviews(firstId),key=id(),trigger="t307_audit_"+id().replace("-","");
        Object request=revising?input(5,"https://example.test/v2"):Map.of("expectedAssignmentVersion",3,"verdict","ACCEPT","comment","Accepted");
        String actorId=revising?student.id:mentor.id;
        long ledgerBefore=count("operation_records","actor_id",actorId);
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='"+action+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic T307 audit fault'; END IF; END");
        try {
            assertEquals("AUDIT_WRITE_FAILED",node(browser.call("POST",route,request,key,true),500).get("errorCode").asString());
            assertEquals(1,count("internship_submissions","assignment_id",assignment));
            assertEquals(revising?1:0,count("internship_submission_reviews","submission_id",firstId));
            assertEquals(revising?5:3,jdbc.queryForObject("SELECT version FROM internship_task_assignments WHERE id=?",Long.class,assignment));
            assertEquals(ledgerBefore,count("operation_records","actor_id",actorId));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id=? AND action=?",Long.class,actorId,action));
        } finally {jdbc.execute("DROP TRIGGER "+trigger);}
        node(browser.call("POST",route,request,key,true),201);
        assertEquals(first,node(owner.call("GET",submissions()+"?page="+(revising?1:0)+"&size=1",null,null,false),200).get("content").get(0));
    }

    @Test void effectiveAtCannotBeBypassedByAStaleInProgressCase() throws Exception {
        var owner=login(student,null);jdbc.update("UPDATE placements SET effective_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),version=version+1 WHERE id=?",placement);
        node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),409);assertEquals(0,count("internship_submissions","assignment_id",assignment));
    }

    @Test void studentAndHrCannotUseTheMentorReviewEndpoint() throws Exception {
        var owner=login(student,null);String submission=node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201).get("submissionId").asString();
        var review=Map.of("expectedAssignmentVersion",3,"verdict","ACCEPT","comment","Must not grant authority");
        node(owner.call("POST",reviews(submission),review,id(),true),403);
        node(login(hr,hrGrant).call("POST",reviews(submission),review,id(),true),403);
        assertEquals(0,count("internship_submission_reviews","submission_id",submission));
    }

    @Test void realUniqueAndForeignKeyConstraintsProtectImmutableHistory() throws Exception {
        var owner=login(student,null);node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),201);
        String insert="INSERT INTO internship_submissions(id,assignment_id,version_no,url,note,submitted_at,late_flag) VALUES (?,?,1,'https://example.test/duplicate',NULL,UTC_TIMESTAMP(6),false)";
        assertThrows(org.springframework.dao.DuplicateKeyException.class,()->jdbc.update(insert,id(),assignment));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(insert,id(),id()));
        assertEquals(1,count("internship_submissions","assignment_id",assignment));
    }

    @Test void concurrentReplayCommitsOneArtifactAndDifferentKeysCannotDuplicateVersion() throws Exception {
        var owner=login(student,null);String key=id();
        JsonNode original=null;
        try(var pool=Executors.newFixedThreadPool(20)) {
            var gate=new CountDownLatch(1);var requests=new ArrayList<Future<HttpResponse<String>>>();
            for(int i=0;i<20;i++) requests.add(pool.submit(()->{gate.await();return owner.call("POST",submissions(),input(2,"https://example.test/v1"),key,true);}));
            gate.countDown();
            for(var pending:requests) {
                var response=pending.get(60,TimeUnit.SECONDS);
                if(response.statusCode()==201) {var value=node(response,201);if(original==null)original=value;else assertEquals(original,value);}
                else assertEquals("REQUEST_IN_PROGRESS",node(response,409).get("errorCode").asString());
            }
        }
        assertNotNull(original);assertEquals(original,node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),key,true),201));
        node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true),409);assertEquals(1,count("internship_submissions","assignment_id",assignment));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id=? AND action='SUBMISSION_CREATED'",Long.class,student.id));
    }

    @Test void simultaneousDifferentKeysWithTheSameExpectedVersionHaveExactlyOneWinner() throws Exception {
        var owner=login(student,null);
        try(var pool=Executors.newFixedThreadPool(20)) {
            var gate=new CountDownLatch(1);var requests=new ArrayList<Future<HttpResponse<String>>>();
            for(int i=0;i<20;i++) requests.add(pool.submit(()->{gate.await();return owner.call("POST",submissions(),input(2,"https://example.test/v1"),id(),true);}));
            gate.countDown();int winners=0;
            for(var pending:requests) {
                var response=pending.get(60,TimeUnit.SECONDS);
                if(response.statusCode()==201) winners++;
                else assertEquals("VERSION_CONFLICT",node(response,409).get("errorCode").asString());
            }
            assertEquals(1,winners);
        }
        assertEquals(1,count("internship_submissions","assignment_id",assignment));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id=? AND action='SUBMISSION_CREATED'",Long.class,student.id));
    }

    @Test void mandatoryJpaAuditFailureRollsBackJdbcAndLedgerThenSameKeyCanRetry() throws Exception {
        var owner=login(student,null);String key=id();String trigger="t307_audit_"+id().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='SUBMISSION_CREATED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic T307 audit fault'; END IF; END");
        try {node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),key,true),500);assertRolledBack();}
        finally {jdbc.execute("DROP TRIGGER "+trigger);}
        node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),key,true),201);
    }

    @Test void ledgerDoneFailureRollsBackJdbcAndJpaAuditThenSameKeyCanRetry() throws Exception {
        var owner=login(student,null);String key=id();String trigger="t307_ledger_"+id().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE UPDATE ON operation_records FOR EACH ROW BEGIN IF NEW.operation_code='T3_SUBMISSION_CREATE' AND NEW.state='DONE' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic T307 ledger fault'; END IF; END");
        try {node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),key,true),500);assertRolledBack();}
        finally {jdbc.execute("DROP TRIGGER "+trigger);}
        node(owner.call("POST",submissions(),input(2,"https://example.test/v1"),key,true),201);
    }
    private void assertRolledBack() {
        assertEquals(0,count("internship_submissions","assignment_id",assignment));
        assertEquals(2,jdbc.queryForObject("SELECT version FROM internship_task_assignments WHERE id=?",Long.class,assignment));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE actor_id=? AND operation_code='T3_SUBMISSION_CREATE'",Long.class,student.id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor_id=? AND action='SUBMISSION_CREATED'",Long.class,student.id));
    }
}
