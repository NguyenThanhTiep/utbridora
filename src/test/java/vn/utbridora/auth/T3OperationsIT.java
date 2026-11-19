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
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.t5.placement-delivery.enabled=false","app.t3.week3.http.enabled=true","app.t3.case-closure-delivery.enabled=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T3OperationsIT {
    static final org.testcontainers.containers.GenericContainer<?> CLAMD=vn.utbridora.auth.support.ClamdFixture.start();
    static final java.nio.file.Path ROOT=tempRoot();
    static java.nio.file.Path tempRoot() {try {return java.nio.file.Files.createTempDirectory("t3-operations-it-");}catch(java.io.IOException e) {throw new ExceptionInInitializerError(e);}}
    @Autowired vn.utbridora.auth.files.RosterImportQuarantine quarantine;
    @Autowired vn.utbridora.auth.contracts.placement.CaseClosedConsumer closedConsumer;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.CaseClosureOutboxWorker closedWorker;

    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static {MYSQL.start();}
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("app.files.storage-root",ROOT::toString);r.add("app.files.scan.host",CLAMD::getHost);r.add("app.files.scan.port",()->CLAMD.getMappedPort(3310));
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
        String value=id(),demand=id();
        var criteria=new vn.utbridora.auth.modules.cooperation.domain.DemandModels.RequiredCriteria(List.of("IT"),List.of(),new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange(LocalDate.parse("2026-01-01"),LocalDate.parse("2028-01-01")));
        var data=new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput(school,term,"Synthetic T3 prerequisite",1,"Synthetic approved Placement source",null,null,Instant.parse("2027-12-01T00:00:00Z"),criteria,List.of(),"v1","skills-v1");
        jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES(?,?,?,?,'READY','VALID',1,1,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",demand,enterprise,school,term,"0".repeat(64),json.writeValueAsString(data));
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'APPROVED',2,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2026-01-01','2028-01-01',UTC_TIMESTAMP(6),'SYNTHETIC_WEEK1_REVIEWED')",value,studentId,school,enterprise,term,demand,id(),id());
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


    void schoolCapabilities() {for(String permission:List.of("t3:workspace:read","t3:cases:close","t3:coordination:manage","t3:enterprise_quality:school:write","t3:grades:publish")) jdbc.update("INSERT IGNORE INTO school_delegation_permissions(assignment_id,permission_code) VALUES(?,?)",sd,permission);}
    String readyCase() {activate(placement);consume(publish(placement));return caseId(placement);}
    long caseVersion(String id) {return jdbc.queryForObject("SELECT version FROM internship_cases WHERE id=?",Long.class,id);}
    Map<String,Object> closing(String id,String decision) {return Map.of("expectedVersion",caseVersion(id),"decision",decision,"decisionRef","SYNTHETIC_CLOSURE_DECISION","reasonCode",decision);}
    CaseClosedEvent closedEvent(String id) {return json.readValue(jdbc.queryForObject("SELECT payload_json FROM internship_case_closed_outbox WHERE case_id=?",String.class,id),CaseClosedEvent.class);}
    TrustedEventContext closedContext(CaseClosedEvent event) {return new TrustedEventContext("T3",jdbc.queryForObject("SELECT operation_id FROM internship_case_closed_outbox WHERE event_id=?",String.class,event.eventId()),event.traceId());}
    String qualityTemplate() {
        String id=id();var template=Map.of("templateId",id,"version",1,"criteria",List.of(Map.of("code","MENTOR_SUPPORT","label","Synthetic mentoring support")));
        jdbc.update("INSERT INTO internship_enterprise_quality_templates(id,version,school_org_id,term_id,template_json) VALUES(?,1,?,?,?)",id,school,term,json.writeValueAsString(template));return id;
    }
    String foreignPlacement() {
        String foreignSchool=org(Organization.Type.UNIVERSITY),foreignEnterprise=org(Organization.Type.ENTERPRISE),foreignTerm=id();
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES(?,?,'SYNTHETIC_FOREIGN_TERM','2026-01-01','2028-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",foreignTerm,foreignSchool);
        String p=placement(user(Role.STUDENT,foreignSchool).id);jdbc.update("UPDATE demands SET school_org_id=?,enterprise_org_id=?,term_id=? WHERE id=(SELECT demand_id FROM placements WHERE id=?)",foreignSchool,foreignEnterprise,foreignTerm,p);
        jdbc.update("UPDATE placements SET school_org_id=?,enterprise_org_id=?,term_id=? WHERE id=?",foreignSchool,foreignEnterprise,foreignTerm,p);consume(publish(p));return p;
    }
    HttpResponse<String> upload(Browser browser,String cohort,String csv,long version,String key) throws Exception {
        String boundary="synthetic-"+id();String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"expectedCohortVersion\"\r\n\r\n"+version+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"placements.csv\"\r\nContent-Type: text/csv\r\n\r\n"+csv+"\r\n--"+boundary+"--\r\n";
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/t3/cohorts/"+cohort+"/placements/preview")).timeout(Duration.ofSeconds(90)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",browser.csrf).header("X-Context-Assignment-Id",browser.assignment).header("Idempotency-Key",key).POST(HttpRequest.BodyPublishers.ofString(body));
        return browser.client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void scannedPlacementPreviewIsScopeBoundAndFortyCasesBecomeFourGroupsWithoutNewIdentities() throws Exception {
        schoolCapabilities();consume(approved);var b=login(hr,hrGrant);String cohort=cohort(b);List<String> ids=new ArrayList<>(List.of(placement));
        for(int i=1;i<40;i++){var u=user(Role.STUDENT,school);String p=placement(u.id);consume(publish(p));ids.add(p);}
        long usersBefore=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);String csv="placementId\n"+String.join("\n",ids)+"\nnot-a-uuid\n";
        String key=id();var preview=upload(b,cohort,csv,1,key);String response=ok(preview,200);assertEquals(response,ok(upload(b,cohort,csv,1,key),200));
        var root=json.readTree(response);assertEquals(41,root.get("rows").size());assertEquals("INVALID_IDENTIFIER",root.get("rows").get(40).get("errorCode").asString());
        assertEquals(0,count("internship_cohort_cases","cohort_id",cohort));
        var input=new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.BatchCases(1L,ids.stream().map(UUID::fromString).toList(),UUID.fromString(field(response,"previewId")),field(response,"fileHash"));
        String route="/t3/cohorts/"+cohort+"/members:batch",operation=id();var first=b.call("POST",route,input,operation,true);ok(first,200);assertEquals(first.body(),ok(b.call("POST",route,input,operation,true),200));
        assertEquals(409,b.call("POST",route,input,id(),true).statusCode());assertEquals(40,count("internship_cohort_cases","cohort_id",cohort));
        var plan=json.readTree(b.post("/t3/cohorts/"+cohort+"/groups:auto",Map.of("mode","PREVIEW","groupSize",10,"expectedCohortVersion",2)));
        assertEquals(4,plan.get("groups").size());String confirmKey=id();var confirm=Map.of("mode","CONFIRM","planId",plan.get("planId").asString(),"expectedCohortVersion",2);
        var groups=b.call("POST","/t3/cohorts/"+cohort+"/groups:auto",confirm,confirmKey,true);assertEquals(4,json.readTree(ok(groups,200)).size());assertEquals(groups.body(),ok(b.call("POST","/t3/cohorts/"+cohort+"/groups:auto",confirm,confirmKey,true),200));
        assertEquals(4,count("internship_groups","cohort_id",cohort));assertEquals(usersBefore,jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class));
    }
    @Test void placementPreviewRejectsForeignExpiredSelectionsAndUsesRealMalwareScanner() throws Exception {
        schoolCapabilities();consume(approved);var b=login(hr,hrGrant);String cohort=cohort(b);
        String foreign=foreignPlacement();String preview=ok(upload(b,cohort,"placementId\n"+placement+"\n"+foreign+"\n",1,id()),200);
        assertEquals("INVALID",json.readTree(preview).get("rows").get(1).get("status").asString());
        assertTrue(json.readTree(preview).get("rows").get(1).get("placementId").isNull());
        var staffBrowser=login(staff,null);String foreignCase=caseId(foreign);
        assertEquals(404,staffBrowser.call("POST","/t3/cases/"+foreignCase+"/close",closing(foreignCase,"EARLY_TERMINATION"),id(),true).statusCode());
        var input=new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.BatchCases(1L,List.of(UUID.randomUUID()),UUID.fromString(field(preview,"previewId")),field(preview,"fileHash"));
        assertEquals(409,b.call("POST","/t3/cohorts/"+cohort+"/members:batch",input,id(),true).statusCode());
        jdbc.update("UPDATE internship_placement_previews SET expires_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND),rows_json=JSON_SET(rows_json,'$.expiresAt','2000-01-01T00:00:00Z') WHERE id=?",field(preview,"previewId"));
        var expired=new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.BatchCases(1L,List.of(UUID.fromString(placement)),UUID.fromString(field(preview,"previewId")),field(preview,"fileHash"));
        assertEquals(409,b.call("POST","/t3/cohorts/"+cohort+"/members:batch",expired,id(),true).statusCode());
        var tooMany=new StringBuilder("placementId\n");for(int i=0;i<1001;i++)tooMany.append(id()).append('\n');
        var limit=upload(b,cohort,tooMany.toString(),1,id());assertEquals(422,limit.statusCode());assertEquals("PLACEMENT_ROW_LIMIT_EXCEEDED",field(limit.body(),"errorCode"));
        String malware="placementId\n"+new String(vn.utbridora.auth.support.ClamdFixture.eicar(),java.nio.charset.StandardCharsets.US_ASCII)+"\n";
        var blocked=upload(b,cohort,malware,1,id());assertEquals(422,blocked.statusCode());assertEquals("FILE_REJECTED",field(blocked.body(),"errorCode"));assertEquals(0,count("internship_cohort_cases","cohort_id",cohort));
    }
    @Test void groupingKeepsRemainderAndStudentResolutionDoesNotLeakAnotherSchool() throws Exception {
        schoolCapabilities();consume(approved);var b=login(hr,hrGrant);String cohort=cohort(b);List<String> pids=new ArrayList<>(List.of(placement));
        for(int i=0;i<2;i++){String p=placement(user(Role.STUDENT,school).id);consume(publish(p));pids.add(p);}
        var resolved=ok(upload(b,cohort,"studentUserId\n"+student.id+"\n"+id()+"\n",1,id()),200);assertEquals(placement,json.readTree(resolved).get("rows").get(0).get("placementId").asString());assertTrue(json.readTree(resolved).get("rows").get(1).get("placementId").isNull());
        b.post("/t3/cohorts/"+cohort+"/members:batch",new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.BatchCases(1L,pids.stream().map(UUID::fromString).toList(),null,null));
        var plan=json.readTree(b.post("/t3/cohorts/"+cohort+"/groups:auto",Map.of("mode","PREVIEW","groupSize",2,"expectedCohortVersion",2)));
        assertEquals(2,plan.get("groups").size());assertEquals(2,plan.get("groups").get(0).get("caseIds").size());assertEquals(1,plan.get("groups").get(1).get("caseIds").size());
    }
    @Test void dashboardQualityIssuesAndInterestRespectScopeAndPreserveStudentGrades() throws Exception {
        schoolCapabilities();String c=readyCase();var h=login(hr,hrGrant);String cohort=cohort(h),g=group(h,cohort,List.of(placement));String mg=grant("MENTOR",mentor.id,hr.id,hrGrant,null,g);
        var s=login(staff,null);var m=login(mentor,mg);var st=login(student,null);
        var summary=json.readTree(ok(s.call("GET","/t3/school/dashboard?termId="+term,null,null,false),200));assertEquals(1,summary.get("caseCount").asInt());assertEquals(5,summary.get("assignmentCounts").size());assertEquals(1,summary.get("mentorCount").asInt());
        assertEquals(403,st.call("GET","/t3/hr/dashboard?termId="+term,null,null,false).statusCode());
        String template=qualityTemplate();var rating=Map.of("criterionCode","MENTOR_SUPPORT","stars",5,"comment","Synthetic quality note");var feedback=new LinkedHashMap<String,Object>();feedback.put("enterpriseOrgId",enterprise);feedback.put("termId",term);feedback.put("templateId",template);feedback.put("templateVersion",1);feedback.put("ratings",List.of(rating));feedback.put("note",null);
        String key=id();var first=s.call("POST","/t3/school/enterprise-feedback",feedback,key,true);ok(first,201);assertEquals(first.body(),ok(s.call("POST","/t3/school/enterprise-feedback",feedback,key,true),201));
        feedback.put("ratings",List.of(rating,rating));assertEquals(422,s.call("POST","/t3/school/enterprise-feedback",feedback,id(),true).statusCode());assertEquals(0,count("internship_evaluations","case_id",c));
        var issue=Map.of("caseId",c,"category","DELAY","description","Synthetic coordination issue","ownerUserId",hr.id);
        assertEquals(422,s.call("POST","/t3/school/issues",Map.of("caseId",c,"category","DELAY","description","Synthetic issue","ownerUserId",student.id),id(),true).statusCode());
        var created=s.call("POST","/t3/school/issues",issue,id(),true);String iid=field(ok(created,201),"issueId");
        assertEquals(409,h.call("PATCH","/t3/school/issues/"+iid,Map.of("expectedVersion",1,"state","RESOLVED"),id(),true).statusCode());
        ok(h.call("PATCH","/t3/school/issues/"+iid,Map.of("expectedVersion",1,"state","IN_PROGRESS"),id(),true),200);assertEquals(409,s.call("PATCH","/t3/school/issues/"+iid,Map.of("expectedVersion",1,"state","IN_PROGRESS"),id(),true).statusCode());
        ok(s.call("PATCH","/t3/school/issues/"+iid,Map.of("expectedVersion",2,"state","RESOLVED"),id(),true),200);assertEquals(3,count("internship_coordination_issue_history","issue_id",iid));
        assertEquals(404,m.call("PATCH","/t3/school/issues/"+iid,Map.of("expectedVersion",3,"state","IN_PROGRESS"),id(),true).statusCode());
        var interest=new LinkedHashMap<String,Object>();interest.put("expectedVersion",0);interest.put("expectedCaseVersion",caseVersion(c));interest.put("interested",true);interest.put("note","Synthetic hiring note");
        var saved=h.call("PUT","/t3/cases/"+c+"/hiring-interest",interest,key=id(),true);ok(saved,200);assertEquals(saved.body(),ok(h.call("PUT","/t3/cases/"+c+"/hiring-interest",interest,key,true),200));
        assertEquals(404,m.call("GET","/t3/cases/"+c+"/hiring-interest",null,null,false).statusCode());assertEquals(200,s.call("GET","/t3/cases/"+c+"/hiring-interest",null,null,false).statusCode());
        assertEquals(0,count("internship_evaluations","case_id",c));assertEquals("ACTIVE",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));
        jdbc.update("UPDATE school_delegations SET active=false,version=version+1 WHERE id=?",sd);
        assertEquals(404,s.call("GET","/t3/school/dashboard?termId="+term,null,null,false).statusCode());assertNotEquals(200,h.call("GET","/t3/hr/dashboard?termId="+term,null,null,false).statusCode());
    }
    @Test void earlyClosureIsAtomicAndT5ConsumesProofOnceWhileTerminalWritesStayLocked() throws Exception {
        schoolCapabilities();consume(approved);String c=caseId(placement);var s=login(staff,null);var input=closing(c,"EARLY_TERMINATION");String key=id();
        var result=s.call("POST","/t3/cases/"+c+"/close",input,key,true);assertEquals("TERMINATED",field(ok(result,200),"state"));assertEquals(result.body(),ok(s.call("POST","/t3/cases/"+c+"/close",input,key,true),200));
        assertEquals("APPROVED",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement),"T3 must not mutate the T5 source");
        var event=closedEvent(c);assertEquals("REJECT",closedConsumer.consume(new TrustedEventContext("T3",id(),id()),event).outcome());
        var applied=closedConsumer.consume(closedContext(event),event);assertEquals("ACK_APPLIED",applied.outcome());assertEquals("ACK_DUPLICATE",closedConsumer.consume(closedContext(event),event).outcome());
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));assertEquals(1,count("placement_history","placement_id",placement));assertEquals(1,count("case_closed_inbox","event_id",event.eventId()));
        assertEquals(409,s.call("POST","/t3/cases/"+c+"/close",closing(c,"EARLY_TERMINATION"),id(),true).statusCode());
        var cancel=json.readValue(jdbc.queryForObject("SELECT payload_json FROM placement_outbox WHERE placement_id=? AND event_type='PlacementCancelled'",String.class,placement),PlacementLifecycleEvent.class);consume(cancel);assertEquals(1,count("internship_case_closed_outbox","case_id",c));
    }
    @Test void normalClosureRequiresEveryWeekCurrentEvaluationAndAllTasksThenSynchronizesCompletion() throws Exception {
        schoolCapabilities();String c=readyCase();var end=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).with(java.time.temporal.TemporalAdjusters.previous(java.time.DayOfWeek.MONDAY)).minusDays(1);var week=end.minusDays(6);
        jdbc.update("UPDATE placements SET start_date=?,end_date=? WHERE id=?",week,end,placement);
        var h=login(hr,hrGrant);String cohort=cohort(h),g=group(h,cohort,List.of(placement));String mg=grant("MENTOR",mentor.id,hr.id,hrGrant,null,g);var m=login(mentor,mg);var s=login(staff,null);var st=login(student,null);
        assertEquals(409,s.call("POST","/t3/cases/"+c+"/close",closing(c,"NORMAL_COMPLETION"),id(),true).statusCode());assertEquals(0,count("internship_case_closures","case_id",c));
        var reportInput=Map.of("caseId",c,"weekStart",week.toString(),"completedWork","Synthetic completed work","difficulties","","nextPlan","Synthetic next plan","evidenceFileIds",List.of());
        var report=st.call("POST","/t3/reports",reportInput,id(),true);String rid=field(ok(report,201),"reportId");
        var submitted=st.call("POST","/t3/reports/"+rid+"/submit",Map.of("expectedVersion",json.readTree(report.body()).get("version").asLong()),id(),true);ok(submitted,200);
        ok(m.call("POST","/t3/reports/"+rid+"/reviews",Map.of("expectedVersion",json.readTree(submitted.body()).get("version").asLong(),"comment","Synthetic reviewed weekly work"),id(),true),200);
        String template=id();var rubric=Map.of("templateId",template,"templateVersion",1,"criteria",List.of(Map.of("criterionCode","QUALITY","label","Synthetic quality","weight",100)));
        jdbc.update("INSERT INTO internship_rubric_templates(id,template_version,school_org_id,term_id,template_json) VALUES(?,1,?,?,?)",template,school,term,json.writeValueAsString(rubric));
        var evaluation=m.call("POST","/t3/evaluations",Map.of("caseId",c,"templateId",template,"templateVersion",1,"scores",List.of(new LinkedHashMap<String,Object>(){{put("criterionCode","QUALITY");put("score",5);put("comment",null);put("evidenceFileIds",List.of());}})),id(),true);String eid=field(ok(evaluation,201),"evaluationId");
        var submittedEval=m.call("POST","/t3/evaluations/"+eid+"/submit",Map.of("expectedVersion",json.readTree(evaluation.body()).get("version").asLong()),id(),true);ok(submittedEval,200);
        ok(s.call("POST","/t3/evaluations/"+eid+"/publish",Map.of("expectedVersion",json.readTree(submittedEval.body()).get("version").asLong(),"decisionRef","SYNTHETIC_PUBLICATION"),id(),true),200);
        String task=field(ok(m.call("POST","/t3/tasks",task(g,List.of(c)),id(),true),201),"taskId");
        assertEquals(409,s.call("POST","/t3/cases/"+c+"/close",closing(c,"NORMAL_COMPLETION"),id(),true).statusCode());
        // Explicit completed-task prerequisite. Task submission/review itself has existing real runtime suites.
        jdbc.update("UPDATE internship_task_assignments SET state='COMPLETED',version=version+1 WHERE task_id=?",task);
        var closed=s.call("POST","/t3/cases/"+c+"/close",closing(c,"NORMAL_COMPLETION"),id(),true);assertEquals("COMPLETED",field(ok(closed,200),"state"));
        var event=closedEvent(c);assertEquals("ACK_APPLIED",closedConsumer.consume(closedContext(event),event).outcome());assertEquals("COMPLETED",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM placement_outbox WHERE placement_id=? AND event_type='PlacementCompleted'",Integer.class,placement));
    }
    @Test void revokedClosureAuthorityAndInboxFailureCannotPartiallyMutateT5AndRetriesGoToDlq() throws Exception {
        schoolCapabilities();consume(approved);String c=caseId(placement);var s=login(staff,null);ok(s.call("POST","/t3/cases/"+c+"/close",closing(c,"EARLY_TERMINATION"),id(),true),200);var event=closedEvent(c);
        jdbc.execute("ALTER TABLE case_closed_inbox ADD CONSTRAINT synthetic_reject_closure CHECK(event_id<>'"+event.eventId()+"')");
        try {assertEquals("RETRY",closedConsumer.consume(closedContext(event),event).outcome());assertEquals("APPROVED",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));assertEquals(0,count("placement_history","placement_id",placement));}
        finally {jdbc.execute("ALTER TABLE case_closed_inbox DROP CHECK synthetic_reject_closure");}
        jdbc.update("UPDATE school_delegations SET active=false,version=version+1 WHERE id=?",sd);
        assertEquals("RETRY",closedConsumer.consume(closedContext(event),event).outcome());
        for(int i=0;i<6;i++){jdbc.update("UPDATE internship_case_closed_outbox SET next_attempt_at=UTC_TIMESTAMP(6) WHERE event_id=?",event.eventId());closedWorker.deliverPending();}
        assertEquals("DLQ",jdbc.queryForObject("SELECT state FROM internship_case_closed_outbox WHERE event_id=?",String.class,event.eventId()));assertEquals(5,jdbc.queryForObject("SELECT attempts FROM internship_case_closed_outbox WHERE event_id=?",Integer.class,event.eventId()));assertEquals(0,count("case_closed_inbox","event_id",event.eventId()));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="frontend.e2e",matches="true")
    void browserImportsGroupsCoordinatesAndClosesUsingRealOwnerApis() throws Exception {
        schoolCapabilities();String c=readyCase();List<String> placements=new ArrayList<>(List.of(placement));
        for(int i=1;i<40;i++){String p=placement(user(Role.STUDENT,school).id);consume(publish(p));placements.add(p);}
        String template=qualityTemplate();boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npx.cmd playwright test tests/t3-operations-runtime.spec.ts"):new ProcessBuilder("npx","playwright","test","tests/t3-operations-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));var env=builder.environment();env.put("API_PROXY_TARGET","http://localhost:"+port);env.put("E2E_PORT","5178");
        env.put("E2E_REPORT_FILE","../target/t3-operations-runtime-browser.xml");env.put("T3OPS_E2E","true");env.put("T3OPS_HR_EMAIL",hr.email);env.put("T3OPS_STAFF_EMAIL",staff.email);
        env.put("T3OPS_PASSWORD",password);env.put("T3OPS_TERM",term);env.put("T3OPS_ASSIGNMENT",hrGrant);env.put("T3OPS_ENTERPRISE",enterprise);env.put("T3OPS_HR_ID",hr.id);env.put("T3OPS_CASE",c);env.put("T3OPS_TEMPLATE",template);env.put("T3OPS_PLACEMENTS",String.join(",",placements));
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/t3-operations-runtime-e2e.log"));var process=builder.start();long deadline=System.nanoTime()+Duration.ofSeconds(240).toNanos();
        try {
            while(process.isAlive() && System.nanoTime()<deadline){closedWorker.deliverPending();Thread.sleep(100);}
            if(process.isAlive()){process.destroyForcibly();fail("T3 operations browser timed out; see isolated runtime log");}
            assertEquals(0,process.exitValue(),"See target/t3-operations-runtime-e2e.log");closedWorker.deliverPending();
            assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM internship_cases WHERE id=?",String.class,c));
            assertEquals("CANCELLED",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));
            assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM internship_groups g JOIN internship_cohorts c ON c.id=g.cohort_id WHERE c.term_id=?",Integer.class,term));
            assertEquals(1,count("internship_hiring_interest_history","case_id",c));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_coordination_issues WHERE case_id=? AND state='RESOLVED'",Integer.class,c));
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
    @Test void concurrentInterestAndCloseCommandsHaveOneWinnerAndNoDuplicateHistory() throws Exception {
        schoolCapabilities();String c=readyCase();var a=login(hr,hrGrant);var b=login(hr,hrGrant);
        var input=new vn.utbridora.auth.modules.internship.api.InternshipOperationsDtos.HiringInterestInput(0L,caseVersion(c),true,"Synthetic concurrent intent");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var jobs=pool.invokeAll(List.<Callable<Integer>>of(()->a.call("PUT","/t3/cases/"+c+"/hiring-interest",input,id(),true).statusCode(),()->b.call("PUT","/t3/cases/"+c+"/hiring-interest",input,id(),true).statusCode()));
            var statuses=new ArrayList<Integer>();for(var job:jobs)statuses.add(job.get(30,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);assertEquals(1,count("internship_hiring_interest_history","case_id",c));
            var s1=login(staff,null);var s2=login(staff,null);var close=closing(c,"EARLY_TERMINATION");
            var closes=pool.invokeAll(List.<Callable<Integer>>of(()->s1.call("POST","/t3/cases/"+c+"/close",close,id(),true).statusCode(),()->s2.call("POST","/t3/cases/"+c+"/close",close,id(),true).statusCode()));
            statuses.clear();for(var job:closes)statuses.add(job.get(30,TimeUnit.SECONDS));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);assertEquals(1,count("internship_case_closed_outbox","case_id",c));
            var fresh=new vn.utbridora.auth.modules.internship.api.InternshipOperationsDtos.HiringInterestInput(1L,caseVersion(c),false,null);
            assertEquals(409,a.call("PUT","/t3/cases/"+c+"/hiring-interest",fresh,id(),true).statusCode());assertEquals(1,count("internship_hiring_interest_history","case_id",c));
        } finally {pool.shutdownNow();}
    }
}
