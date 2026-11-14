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
class T311GradeRuntimeIT {
    // Only this disposable container permits fault-injection triggers under binary logging.
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
    static final java.nio.file.Path FILE_ROOT=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {
        try { return java.nio.file.Files.createTempDirectory("t311-evidence-"); }
        catch(java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    static {MYSQL.start();} // PER_CLASS Spring injection needs the mapped port before @BeforeAll.
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("app.files.storage-root",()->FILE_ROOT.toString());
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    final JsonMapper json=JsonMapper.builder().build();final String password="Synthetic-T311-"+UUID.randomUUID();String hash;
    String school,enterprise,term,caseId,placement,cohort,group,task,assignment,mentorGrant,hrGrant,template;
    UserAccount admin,staff,coordinator,hr,mentor,student,outsider;
    @BeforeAll void passwordHash(){hash=encoder.encode(password);}
    @BeforeEach void fixture() throws Exception {
        assertTrue(MYSQL.isRunning()); // Every write below targets this private ephemeral container.
        school=org(Organization.Type.UNIVERSITY);enterprise=org(Organization.Type.ENTERPRISE);term=id();
        admin=user(Role.ADMIN,null);staff=user(Role.SCHOOL_STAFF,school);coordinator=user(Role.ENTERPRISE,enterprise);
        hr=user(Role.ENTERPRISE,enterprise);mentor=user(Role.ENTERPRISE,enterprise);student=user(Role.STUDENT,school);
        outsider=user(Role.STUDENT,org(Organization.Type.UNIVERSITY));
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2020-01-01','2099-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,"SYNTHETIC-"+id());
        String delegation=id();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_T311_PREREQUISITE',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,'t3:coordinator:confirm')",delegation);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_T311_PREREQUISITE',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        String parent=grant("COORDINATOR",coordinator.id,staff.id,null,delegation,null);
        hrGrant=grant("HR",hr.id,coordinator.id,parent,null,null);
        placement=id();caseId=id();cohort=id();group=id();task=id();assignment=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2020-01-01','2099-01-01',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'SYNTHETIC_T311_PREREQUISITE')",placement,student.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,placement,student.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,'Synthetic T311',1)",cohort,enterprise,school,term,"T311-"+id());
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'G1',1)",group,cohort);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,caseId,group);
        jdbc.update("INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,'Synthetic T311','Synthetic description',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'NORMAL',true,1)",task,group);
        jdbc.update("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',2)",assignment,task,caseId,student.id);
        mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
        for(String permission:List.of("t3:grades:publish","t3:result_policy:manage","t3:workspace:read"))
            jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,?)",delegation,permission);
        template=id();
        jdbc.update("INSERT INTO internship_rubric_templates(id,template_version,school_org_id,term_id,template_json) VALUES (?,1,?,?,?)",
            template,school,term,json.writeValueAsString(Map.of("templateId",template,"templateVersion",1,"criteria",List.of(
                Map.of("criterionCode","QUALITY","label","Quality","weight",40),Map.of("criterionCode","PROCESS","label","Process","weight",60)))));
    }
    private String grant(String kind,String subject,String grantor,String parent,String delegation,String assignedGroup) {
        String grant=id();
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_T311_PREREQUISITE',true,DATE_SUB(UTC_DATE(),INTERVAL 1 HOUR),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY))",
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


    private static final String EVALUATIONS="/api/v1/t3/evaluations";
    private String path(String evaluation) { return EVALUATIONS+"/"+evaluation; }
    private List<Map<String,Object>> scores(String quality,String process,List<String> files) {
        return List.of(Map.of("criterionCode","QUALITY","score",new java.math.BigDecimal(quality),"comment","Quality feedback","evidenceFileIds",files),
                Map.of("criterionCode","PROCESS","score",new java.math.BigDecimal(process),"comment","Process feedback","evidenceFileIds",List.of()));
    }
    private Map<String,Object> draft() { return Map.of("caseId",caseId,"templateId",template,"templateVersion",1,"scores",scores("5","3",List.of())); }
    private String create(Browser reviewer) throws Exception { return node(reviewer.call("POST",EVALUATIONS,draft(),id(),true),201).get("evaluationId").asString(); }
    private String upload(Browser reviewer,String evaluation) throws Exception {
        String boundary="T311Boundary"+id();StringBuilder body=new StringBuilder();
        for(var entry:Map.of("resourceType","EVALUATION","resourceId",evaluation,"purpose","EVIDENCE").entrySet())
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n\r\n").append(entry.getValue()).append("\r\n");
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.7\nSynthetic T311 test only\n%%EOF\r\n--").append(boundary).append("--\r\n");
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/files"))
                .header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",reviewer.csrf)
                .header("X-Context-Assignment-Id",reviewer.context).header("Idempotency-Key",id())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return node(reviewer.http.send(request,HttpResponse.BodyHandlers.ofString()),202).get("fileId").asString();
    }
    private String schoolPath(){return "/api/v1/t3/schools/"+school+"/terms/"+term+"/result-visibility";}
    private String hrPath(boolean byCase){return "/api/v1/t3/organizations/"+enterprise+"/grade-visibility?termId="+term+(byCase?"&caseId="+caseId:"");}
    private String resultPath(){return "/api/v1/t3/me/results?caseId="+caseId;}
    private Map<String,Object> toggle(long version,boolean enabled){return Map.of("expectedVersion",version,"enabled",enabled);}
    private Map<String,Object> publication(long version){return Map.of("expectedVersion",version,"decisionRef","SCHOOL-DECISION-001");}
    private Map<String,Object> revision(long version){return Map.of("expectedVersion",version,"decisionRef","SCHOOL-REVISION-001","reasonCode","CORRECTION");}
    private String submitted() throws Exception {var m=login(mentor,mentorGrant);String id=create(m);node(m.call("POST",path(id)+"/submit",Map.of("expectedVersion",1),id(),true),200);return id;}
    private void enable(Browser s,Browser h) throws Exception {node(s.call("PUT",schoolPath(),toggle(0,true),id(),true),200);node(h.call("PUT",hrPath(false),toggle(0,true),id(),true),200);}
    private void hidden(HttpResponse<String> response){var value=node(response,200);assertEquals("HIDDEN",value.get("visibility").asString());assertTrue(value.get("evaluation").isNull());assertEquals("NOT_AVAILABLE",value.get("reasonCode").asString());assertFalse(response.body().contains("total100"));assertFalse(response.body().contains("Quality feedback"));}

    @ParameterizedTest @CsvSource({"false,false,false","false,false,true","false,true,false","false,true,true","true,false,false","true,false,true","true,true,false","true,true,true"})
    void allEightVisibilityCombinations(boolean published,boolean schoolEnabled,boolean hrEnabled) throws Exception {
        String evaluation=submitted();var s=login(staff,null);var h=login(hr,hrGrant);var owner=login(student,null);
        node(s.call("PUT",schoolPath(),toggle(0,schoolEnabled),id(),true),200);node(h.call("PUT",hrPath(false),toggle(0,hrEnabled),id(),true),200);
        if(published)node(s.call("POST",path(evaluation)+"/publish",publication(2),id(),true),200);
        var response=owner.call("GET",resultPath(),null,null,true);
        if(published && schoolEnabled && hrEnabled){var value=node(response,200);assertEquals("VISIBLE",value.get("visibility").asString());assertEquals("76.00",value.get("evaluation").get("total100").decimalValue().setScale(2).toString());}
        else hidden(response);
        assertEquals("private, no-store",response.headers().firstValue("Cache-Control").orElseThrow());
    }
    @Test void missingPoliciesAreFalseAndCaseOverrideWins() throws Exception {
        var s=login(staff,null);var h=login(hr,hrGrant);var owner=login(student,null);
        var absent=node(s.call("GET",schoolPath(),null,null,true),200);assertFalse(absent.get("enabled").asBoolean());assertEquals(0,absent.get("version").asInt());
        String evaluation=submitted();node(s.call("POST",path(evaluation)+"/publish",publication(2),id(),true),200);hidden(owner.call("GET",resultPath(),null,null,true));
        enable(s,h);assertEquals("VISIBLE",node(owner.call("GET",resultPath(),null,null,true),200).get("visibility").asString());
        node(h.call("PUT",hrPath(true),toggle(0,false),id(),true),200);hidden(owner.call("GET",resultPath(),null,null,true));
        node(h.call("PUT",hrPath(true),toggle(1,true),id(),true),200);
        node(s.call("PUT",schoolPath(),toggle(1,false),id(),true),200);hidden(owner.call("GET",resultPath(),null,null,true));
    }
    @Test void revisionPreservesPublishedPointerUntilRepublished() throws Exception {
        var s=login(staff,null);var h=login(hr,hrGrant);var m=login(mentor,mentorGrant);var owner=login(student,null);String evaluation=submitted();enable(s,h);
        node(s.call("POST",path(evaluation)+"/publish",publication(2),id(),true),200);
        var old=node(owner.call("GET",resultPath(),null,null,true),200).get("evaluation");
        node(s.call("POST",path(evaluation)+"/revisions",revision(3),id(),true),201);
        node(m.call("PUT",path(evaluation),Map.of("expectedVersion",4,"scores",scores("1","1",List.of())),id(),true),200);
        assertEquals(old,node(owner.call("GET",resultPath(),null,null,true),200).get("evaluation"));
        node(m.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",5),id(),true),200);
        assertEquals(old,node(owner.call("GET",resultPath(),null,null,true),200).get("evaluation"));
        node(s.call("POST",path(evaluation)+"/publish",publication(6),id(),true),200);
        var latest=node(owner.call("GET",resultPath(),null,null,true),200).get("evaluation");assertEquals(2,latest.get("revisionNo").asInt());assertEquals(20,latest.get("total100").asInt());
        assertEquals(old,node(m.call("GET",path(evaluation)+"?version=3",null,null,true),200));
        assertEquals("CORRECTION",jdbc.queryForObject("SELECT reason_code FROM internship_evaluation_revision_decisions WHERE evaluation_id=?",String.class,evaluation));
    }
    @Test void wrongActorsAndForeignStudentsCannotPublishOrRead() throws Exception {
        String evaluation=submitted();
        for(var b:List.of(login(mentor,mentorGrant),login(hr,hrGrant),login(student,null),login(admin,null)))node(b.call("POST",path(evaluation)+"/publish",publication(2),id(),true),404);
        node(login(hr,hrGrant).call("PUT",schoolPath(),toggle(0,true),id(),true),404);
        node(login(outsider,null).call("GET",resultPath(),null,null,true),404);
        var other=user(Role.SCHOOL_STAFF,org(Organization.Type.UNIVERSITY));node(login(other,null).call("POST",path(evaluation)+"/publish",publication(2),id(),true),404);
        var noDelegation=user(Role.SCHOOL_STAFF,school);node(login(noDelegation,null).call("POST",path(evaluation)+"/publish",publication(2),id(),true),404);
    }
    @Test void revokedDelegationBlocksCachedPublicationAndSchoolPolicy() throws Exception {
        String evaluation=submitted();var s=login(staff,null);String key=id();
        node(s.call("POST",path(evaluation)+"/publish",publication(2),key,true),200);
        jdbc.update("UPDATE school_delegations SET active=false,version=version+1 WHERE user_id=?",staff.id);
        node(s.call("POST",path(evaluation)+"/publish",publication(2),key,true),404);
        node(s.call("GET",schoolPath(),null,null,true),404);
    }
    @Test void groupHrCannotChangeOrganizationOrOtherGroupCase() throws Exception {
        String parent=jdbc.queryForObject("SELECT parent_assignment_id FROM internship_context_assignments WHERE id=?",String.class,hrGrant);
        var scope=new LinkedHashMap<String,Object>();scope.put("schoolOrgId",school);scope.put("enterpriseOrgId",enterprise);scope.put("termId",term);scope.put("groupId",group);scope.put("caseId",null);
        String limited=node(login(coordinator,parent).call("POST","/api/v1/t3/hr-assignments",Map.of("userId",hr.id,"scope",scope,"validFrom",Instant.now().minusSeconds(60).toString(),"validUntil",Instant.now().plusSeconds(86400).toString(),"decisionRef","T311-GROUP-HR"),id(),true),201).get("id").asString();
        var h=login(hr,limited);node(h.call("PUT",hrPath(false),toggle(0,true),id(),true),403);
        node(h.call("PUT",hrPath(true),toggle(0,true),id(),true),200);
        assertTrue(List.of(403,404).contains(h.call("GET","/api/v1/t3/tasks/"+task,null,null,true).statusCode()));
        String next=id();jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'OTHER',1)",next,cohort);
        jdbc.update("UPDATE internship_cohort_cases SET group_id=? WHERE case_id=?",next,caseId);
        node(h.call("PUT",hrPath(true),toggle(1,false),id(),true),404);
    }
    @Test void revokedHrCannotReadOrReplayPolicy() throws Exception {
        var h=login(hr,hrGrant);String key=id();node(h.call("PUT",hrPath(false),toggle(0,true),key,true),200);
        jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",hrGrant);
        node(h.call("GET",hrPath(false),null,null,true),403);
        node(h.call("PUT",hrPath(false),toggle(0,true),key,true),403);
    }
    @Test void policyConcurrentCompareAndSetAndAuditRollback() throws Exception {
        var s=login(staff,null);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->s.call("PUT",schoolPath(),toggle(0,true),id(),true));
            var second=pool.submit(()->s.call("PUT",schoolPath(),toggle(0,false),id(),true));
            var statuses=new ArrayList<>(List.of(first.get().statusCode(),second.get().statusCode()));Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
        }
        assertEquals(1,node(s.call("GET",schoolPath(),null,null,true),200).get("version").asInt());
        jdbc.execute("CREATE TRIGGER t311_policy_fault BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='SCHOOL_RESULT_VISIBILITY_CHANGED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic policy audit fault'; END IF; END");
        String key=id();
        try {node(s.call("PUT",schoolPath(),toggle(1,true),key,true),500);assertEquals(1,node(s.call("GET",schoolPath(),null,null,true),200).get("version").asInt());}
        finally {jdbc.execute("DROP TRIGGER t311_policy_fault");}
        assertEquals(2,node(s.call("PUT",schoolPath(),toggle(1,true),key,true),200).get("version").asInt());
    }
    @Test void strictValidationVersionReplayAndCsrf() throws Exception {
        var s=login(staff,null);String evaluation=submitted();
        node(s.call("PUT",schoolPath(),toggle(0,true),id(),false),403);
        var missingKey=node(s.call("PUT",schoolPath(),toggle(0,true),null,true),400);assertTrue(missingKey.hasNonNull("requestId"));
        node(s.call("PUT",schoolPath(),Map.of("expectedVersion",0,"enabled","true"),id(),true),400);
        node(s.call("PUT",schoolPath(),Map.of("expectedVersion",0,"enabled",true,"userId",student.id),id(),true),400);
        String key=id();var a=node(s.call("PUT",schoolPath(),toggle(0,true),key,true),200);assertEquals(a,node(s.call("PUT",schoolPath(),toggle(0,true),key,true),200));
        node(s.call("PUT",schoolPath(),toggle(0,false),key,true),409);node(s.call("PUT",schoolPath(),toggle(0,false),id(),true),409);
        node(s.call("POST",path(evaluation)+"/publish",publication(1),id(),true),409);
        String publishKey=id();var p=node(s.call("POST",path(evaluation)+"/publish",publication(2),publishKey,true),200);assertEquals(p,node(s.call("POST",path(evaluation)+"/publish",publication(2),publishKey,true),200));
        node(s.call("POST",path(evaluation)+"/publish",publication(3),id(),true),409);
    }
    @Test void concurrentPublishHasOneSnapshotAndAudit() throws Exception {
        var s=login(staff,null);String evaluation=submitted();String key=id();
        try(var pool=Executors.newFixedThreadPool(4)){var calls=new ArrayList<Future<HttpResponse<String>>>();for(int i=0;i<12;i++)calls.add(pool.submit(()->s.call("POST",path(evaluation)+"/publish",publication(2),key,true)));for(var call:calls){int status=call.get().statusCode();assertTrue(status==200||status==409,"status="+status);}}
        node(s.call("POST",path(evaluation)+"/publish",publication(2),key,true),200);
        assertEquals(3,count("internship_evaluation_versions","evaluation_id",evaluation));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='EVALUATION_PUBLISHED' AND resource_id=?",Integer.class,evaluation));
    }
    @Test void auditFaultRollsBackSnapshotPointerAndLedger() throws Exception {
        var s=login(staff,null);String evaluation=submitted();String key=id();
        jdbc.execute("CREATE TRIGGER t311_fault BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='EVALUATION_PUBLISHED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic audit fault'; END IF; END");
        try{node(s.call("POST",path(evaluation)+"/publish",publication(2),key,true),500);assertEquals(2,count("internship_evaluation_versions","evaluation_id",evaluation));assertNull(jdbc.queryForObject("SELECT published_version FROM internship_evaluations WHERE id=?",Long.class,evaluation));}
        finally{jdbc.execute("DROP TRIGGER t311_fault");}
        node(s.call("POST",path(evaluation)+"/publish",publication(2),key,true),200);
    }
    @Test void sourceCancellationBlocksPublishAndSchoolListHasScope() throws Exception {
        var s=login(staff,null);String evaluation=submitted();
        var page=node(s.call("GET","/api/v1/t3/school/evaluations?termId="+term,null,null,true),200);assertEquals(1,page.get("totalElements").asInt());
        node(login(student,null).call("GET","/api/v1/t3/school/evaluations?termId="+term,null,null,true),404);
        jdbc.update("UPDATE placements SET state='CANCELLED',version=version+1,cancelled_at=UTC_TIMESTAMP(6) WHERE id=?",placement);
        node(s.call("POST",path(evaluation)+"/publish",publication(2),id(),true),409);
    }
    @Test void studentEvidenceRequiresAllGatesAndOnlyPublishedReferences() throws Exception {
        var m=login(mentor,mentorGrant);var s=login(staff,null);var h=login(hr,hrGrant);var owner=login(student,null);String evaluation=create(m);String file=upload(m,evaluation);
        node(m.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("5","3",List.of(file))),id(),true),200);
        // Scanner-output fixture only, not a real antivirus claim.
        jdbc.update("UPDATE files SET scan_status='CLEAN' WHERE id=?",file);
        node(m.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",2),id(),true),200);enable(s,h);
        node(owner.call("GET","/api/v1/files/"+file+"/metadata",null,null,true),404);
        node(s.call("POST",path(evaluation)+"/publish",publication(3),id(),true),200);
        node(owner.call("GET","/api/v1/files/"+file+"/metadata",null,null,true),200);
        assertEquals(200,owner.call("GET","/api/v1/files/"+file+"/content",null,null,true).statusCode());
        node(s.call("POST",path(evaluation)+"/revisions",revision(4),id(),true),201);String orphan=upload(m,evaluation);
        node(owner.call("GET","/api/v1/files/"+orphan+"/metadata",null,null,true),404);
        node(h.call("PUT",hrPath(false),toggle(1,false),id(),true),200);
        node(owner.call("GET","/api/v1/files/"+file+"/metadata",null,null,true),404);
        node(owner.call("GET","/api/v1/files/"+file+"/content",null,null,true),404);
    }
}
