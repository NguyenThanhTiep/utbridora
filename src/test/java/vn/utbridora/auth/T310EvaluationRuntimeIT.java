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
class T310EvaluationRuntimeIT {
    // Only this disposable container permits fault-injection triggers under binary logging.
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withCommand("--log-bin-trust-function-creators=1");
    static final java.nio.file.Path FILE_ROOT=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {
        try { return java.nio.file.Files.createTempDirectory("t310-evidence-"); }
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
    final JsonMapper json=JsonMapper.builder().build();final String password="Synthetic-T310-"+UUID.randomUUID();String hash;
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
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_T310_PREREQUISITE',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,'t3:coordinator:confirm')",delegation);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_T310_PREREQUISITE',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        String parent=grant("COORDINATOR",coordinator.id,staff.id,null,delegation,null);
        hrGrant=grant("HR",hr.id,coordinator.id,parent,null,null);
        placement=id();caseId=id();cohort=id();group=id();task=id();assignment=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2020-01-01','2099-01-01',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'SYNTHETIC_T310_PREREQUISITE')",placement,student.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,placement,student.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,'Synthetic T310',1)",cohort,enterprise,school,term,"T310-"+id());
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'G1',1)",group,cohort);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,caseId,group);
        jdbc.update("INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,'Synthetic T310','Synthetic description',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'NORMAL',true,1)",task,group);
        jdbc.update("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',2)",assignment,task,caseId,student.id);
        mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
        template=id();
        jdbc.update("INSERT INTO internship_rubric_templates(id,template_version,school_org_id,term_id,template_json) VALUES (?,1,?,?,?)",
            template,school,term,json.writeValueAsString(Map.of("templateId",template,"templateVersion",1,"criteria",List.of(
                Map.of("criterionCode","QUALITY","label","Quality","weight",40),Map.of("criterionCode","PROCESS","label","Process","weight",60)))));
    }
    private String grant(String kind,String subject,String grantor,String parent,String delegation,String assignedGroup) {
        String grant=id();
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_T310_PREREQUISITE',true,DATE_SUB(UTC_DATE(),INTERVAL 1 HOUR),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY))",
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
        String boundary="T310Boundary"+id();StringBuilder body=new StringBuilder();
        for(var entry:Map.of("resourceType","EVALUATION","resourceId",evaluation,"purpose","EVIDENCE").entrySet())
            body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n\r\n").append(entry.getValue()).append("\r\n");
        body.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"file\"; filename=\"evidence.pdf\"\r\nContent-Type: application/pdf\r\n\r\n%PDF-1.7\nSynthetic T310 test only\n%%EOF\r\n--").append(boundary).append("--\r\n");
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/files"))
                .header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-TOKEN",reviewer.csrf)
                .header("X-Context-Assignment-Id",reviewer.context).header("Idempotency-Key",id())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return node(reviewer.http.send(request,HttpResponse.BodyHandlers.ofString()),202).get("fileId").asString();
    }
    @Test void templateCreateEditSubmitAndReloadKeepEverySnapshot() throws Exception {
        var reviewer=login(mentor,mentorGrant);
        var templates=node(reviewer.call("GET","/api/v1/t3/rubric-templates?termId="+term,null,null,false),200);
        assertEquals(template,templates.get("content").get(0).get("templateId").asString());
        assertEquals(204,reviewer.call("GET",EVALUATIONS+"?caseId="+caseId,null,null,false).statusCode());
        String key=id(); var request=draft();
        var first=node(reviewer.call("POST",EVALUATIONS,request,key,true),201);
        assertEquals(new java.math.BigDecimal("76.0"),first.get("total100").decimalValue());
        assertEquals(first,node(reviewer.call("POST",EVALUATIONS,request,key,true),201));
        String evaluation=first.get("evaluationId").asString();
        var edit=Map.of("expectedVersion",1,"scores",scores("4.99","2.01",List.of()));
        var second=node(reviewer.call("PUT",path(evaluation),edit,id(),true),200);
        assertEquals(2,second.get("version").asInt());
        String submitKey=id();var submit=Map.of("expectedVersion",2);
        var submitted=node(reviewer.call("POST",path(evaluation)+"/submit",submit,submitKey,true),200);
        assertEquals("SUBMITTED",submitted.get("state").asString());
        assertEquals(submitted,node(reviewer.call("POST",path(evaluation)+"/submit",submit,submitKey,true),200));
        var reloaded=login(mentor,mentorGrant);
        assertEquals(first,node(reloaded.call("GET",path(evaluation)+"?version=1",null,null,false),200));
        assertEquals(second,node(reloaded.call("GET",path(evaluation)+"?version=2",null,null,false),200));
        assertEquals(submitted,node(reloaded.call("GET",EVALUATIONS+"?caseId="+caseId,null,null,false),200));
        assertEquals(3,node(reloaded.call("GET",path(evaluation)+"/history?size=1",null,null,false),200).get("totalElements").asInt());
        assertEquals(first,node(reloaded.call("GET",path(evaluation)+"/history?page=2&size=1",null,null,false),200).get("content").get(0));
        node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",3,"scores",scores("5","5",List.of())),id(),true),409);
        assertEquals(3,count("internship_evaluation_versions","evaluation_id",evaluation));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE resource_id=? AND action='EVALUATION_DRAFT_SAVED'",Integer.class,evaluation));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE resource_id=? AND action='EVALUATION_SUBMITTED'",Integer.class,evaluation));
    }
    @Test void malformedInputsRubricMismatchAndDuplicateRootsFailWithoutEffects() throws Exception {
        var reviewer=login(mentor,mentorGrant);
        var wrong=new LinkedHashMap<>(draft());wrong.put("templateVersion",2);
        assertEquals("VERSION_CONFLICT",node(reviewer.call("POST",EVALUATIONS,wrong,id(),true),409).get("errorCode").asString());
        wrong=new LinkedHashMap<>(draft());wrong.put("scores",List.of(scores("5","3",List.of()).getFirst()));
        assertEquals("RUBRIC_INVALID",node(reviewer.call("POST",EVALUATIONS,wrong,id(),true),422).get("errorCode").asString());
        wrong=new LinkedHashMap<>(draft());wrong.put("scores",scores("5.000000000000000001","3",List.of()));
        node(reviewer.call("POST",EVALUATIONS,wrong,id(),true),400);
        wrong=new LinkedHashMap<>(draft());wrong.put("state","PUBLISHED");node(reviewer.call("POST",EVALUATIONS,wrong,id(),true),400);
        assertEquals(0,count("internship_evaluations","case_id",caseId));
        String evaluation=create(reviewer);
        assertEquals("EVALUATION_ALREADY_EXISTS",node(reviewer.call("POST",EVALUATIONS,draft(),id(),true),409).get("errorCode").asString());
        node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",0,"scores",scores("5","5",List.of())),id(),true),409);
        assertEquals(1,count("internship_evaluation_versions","evaluation_id",evaluation));
    }
    @Test void newerTemplateVersionCannotChangeExistingEvaluationSnapshot() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        jdbc.update("INSERT INTO internship_rubric_templates VALUES (?,2,?,?,?)",template,school,term,
                json.writeValueAsString(Map.of("templateId",template,"templateVersion",2,"criteria",List.of(
                    Map.of("criterionCode","QUALITY","label","Quality v2","weight",25),Map.of("criterionCode","PROCESS","label","Process v2","weight",75)))));
        assertEquals(2,node(reviewer.call("GET","/api/v1/t3/rubric-templates?termId="+term,null,null,false),200).get("content").get(0).get("templateVersion").asInt());
        var updated=node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("5","3",List.of())),id(),true),200);
        assertEquals(1,updated.get("template").get("templateVersion").asInt());
        assertEquals(0,new java.math.BigDecimal("76.00").compareTo(updated.get("total100").decimalValue()));
    }
    @Test void publishedSnapshotAndPointerCannotBeOverwrittenByMentor() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        // Synthetic T3-11 output, not a claim that school publication is implemented by T3-10.
        var snapshot=json.readValue(jdbc.queryForObject("SELECT snapshot_json FROM internship_evaluation_versions WHERE evaluation_id=? AND version=1",String.class,evaluation),Map.class);
        snapshot.put("state","PUBLISHED");snapshot.put("version",2);snapshot.put("publishedAt","2026-11-14T00:00:00Z");snapshot.put("decisionRef","SYNTHETIC_SCHOOL_DECISION");
        String frozen=json.writeValueAsString(snapshot);
        jdbc.update("INSERT INTO internship_evaluation_versions VALUES (?,2,1,'PUBLISHED',?,1,?,76.00,?,UTC_TIMESTAMP(6))",evaluation,template,frozen,mentor.id);
        jdbc.update("UPDATE internship_evaluations SET current_version=2,published_version=2 WHERE id=?",evaluation);
        node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",2,"scores",scores("1","1",List.of())),id(),true),409);
        node(reviewer.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",2),id(),true),409);
        assertEquals(frozen,jdbc.queryForObject("SELECT snapshot_json FROM internship_evaluation_versions WHERE evaluation_id=? AND version=2",String.class,evaluation));
        assertEquals(2,jdbc.queryForObject("SELECT published_version FROM internship_evaluations WHERE id=?",Long.class,evaluation));
    }
    @Test void currentMentorOnlyNoDraftLeakToStudentHrOrWrongGroup() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        node(new Browser().call("GET",path(evaluation),null,null,false),401);
        for(var denied:List.of(login(student,null),login(outsider,null))) {
            node(denied.call("GET",path(evaluation),null,null,false),404);
            node(denied.call("GET",path(evaluation)+"/history",null,null,false),404);
            node(denied.call("GET",EVALUATIONS+"?caseId="+caseId,null,null,false),404);
        }
        node(login(hr,hrGrant).call("GET",path(evaluation),null,null,false),403);
        node(login(staff,null).call("GET",path(evaluation),null,null,false),404);
        String otherGroup=id();jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'OTHER',1)",otherGroup,cohort);
        var otherMentor=user(Role.ENTERPRISE,enterprise);String otherGrant=grant("MENTOR",otherMentor.id,hr.id,hrGrant,null,otherGroup);
        node(login(otherMentor,otherGrant).call("GET",path(evaluation),null,null,false),404);
        node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("5","5",List.of())),id(),false),403);
    }
    @Test void readsUseStrictQueriesAndMutationRequiresOperationKey() throws Exception {
        var reviewer=login(mentor,mentorGrant);
        node(reviewer.call("POST",EVALUATIONS,draft(),null,true),400);
        String evaluation=create(reviewer);
        for(String route:List.of(path(evaluation)+"?version=1.0",path(evaluation)+"?version=1&version=2",
                EVALUATIONS+"?caseId="+caseId+"&studentId="+student.id,"/api/v1/t3/rubric-templates?termId="+term+"&termId="+term))
            node(reviewer.call("GET",route,null,null,false),400);
        node(reviewer.call("GET",path(evaluation)+"/history?size=101",null,null,false),400);
        node(reviewer.call("GET",path(evaluation)+"?version=999",null,null,false),404);
        node(reviewer.call("GET",path(id()),null,null,false),404);
        var reply=node(reviewer.call("GET",path("invalid"),null,null,false),400);
        assertEquals(6,reply.size());assertTrue(reply.has("requestId"));
    }
    @Test void revocationDeniesCachedReplayAndReads() throws Exception {
        var reviewer=login(mentor,mentorGrant);String key=id();var body=draft();
        String evaluation=node(reviewer.call("POST",EVALUATIONS,body,key,true),201).get("evaluationId").asString();
        jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",hrGrant);
        node(reviewer.call("POST",EVALUATIONS,body,key,true),403);
        node(reviewer.call("GET",path(evaluation),null,null,false),403);
    }
    @Test void movingCaseRevokesOldMentorAndAllowsCurrentGroupMentor() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        String next=id();jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'NEXT',1)",next,cohort);
        var replacement=user(Role.ENTERPRISE,enterprise);String nextGrant=grant("MENTOR",replacement.id,hr.id,hrGrant,null,next);
        node(login(hr,hrGrant).call("POST","/api/v1/t3/groups/"+group+"/members:move",Map.of("caseId",caseId,"toGroupId",next,
                "expectedCaseVersion",2,"expectedFromGroupVersion",1,"expectedToGroupVersion",1,"reasonCode","SYNTHETIC_MOVE"),id(),true),200);
        node(reviewer.call("GET",path(evaluation),null,null,false),404);
        var current=login(replacement,nextGrant);
        node(current.call("GET",path(evaluation),null,null,false),200);
        node(current.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("4","4",List.of())),id(),true),200);
    }
    @Test void evidenceCannotBeBorrowedFromAnotherAuthorizedEvaluation() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        String secondCase=id(),secondPlacement=id();var secondStudent=user(Role.STUDENT,school);
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2020-01-01','2099-01-01',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'SYNTHETIC_T310_PREREQUISITE')",secondPlacement,secondStudent.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",secondCase,secondPlacement,secondStudent.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,secondCase,group);
        var body=new LinkedHashMap<>(draft());body.put("caseId",secondCase);
        String second=node(reviewer.call("POST",EVALUATIONS,body,id(),true),201).get("evaluationId").asString();
        String file=upload(reviewer,second);
        assertEquals("FILE_BINDING_INVALID",node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("5","3",List.of(file))),id(),true),422).get("errorCode").asString());
        assertEquals(1,count("internship_evaluation_versions","evaluation_id",evaluation));
    }
    @Test void sourceCancellationAndFutureEffectiveTimeBlockMutationsButPreserveHistory() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        jdbc.update("UPDATE placements SET effective_at=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),version=version+1 WHERE id=?",placement);
        node(reviewer.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",1),id(),true),409);
        jdbc.update("UPDATE placements SET state='CANCELLED',cancelled_at=UTC_TIMESTAMP(6),version=version+1 WHERE id=?",placement);
        node(reviewer.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",1),id(),true),409);
        node(reviewer.call("GET",path(evaluation),null,null,false),200);
        assertEquals(1,count("internship_evaluation_versions","evaluation_id",evaluation));
    }
    @Test void concurrentUpdateSubmitHaveOneWinnerAndRetryKeepsOneEffect() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("4","4",List.of())),id(),true));
            var b=pool.submit(()->reviewer.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",1),id(),true));
            var statuses=new ArrayList<>(List.of(a.get().statusCode(),b.get().statusCode()));Collections.sort(statuses);
            assertEquals(List.of(200,409),statuses);
        }
        assertEquals(2,count("internship_evaluation_versions","evaluation_id",evaluation));
    }
    @Test void concurrentSameKeyAndChangedPayloadUseExistingLedger() throws Exception {
        var reviewer=login(mentor,mentorGrant);String key=id();var body=draft();
        try(var pool=Executors.newFixedThreadPool(6)) {
            var calls=new ArrayList<Future<HttpResponse<String>>>();
            for(int i=0;i<12;i++) calls.add(pool.submit(()->reviewer.call("POST",EVALUATIONS,body,key,true)));
            for(var call:calls) {var r=call.get();assertTrue(r.statusCode()==201||r.statusCode()==409,r.body());}
        }
        String evaluation=node(reviewer.call("POST",EVALUATIONS,body,key,true),201).get("evaluationId").asString();
        var changed=new LinkedHashMap<>(body);changed.put("scores",scores("4","4",List.of()));
        assertEquals("IDEMPOTENCY_KEY_REUSED",node(reviewer.call("POST",EVALUATIONS,changed,key,true),409).get("errorCode").asString());
        assertEquals(1,count("internship_evaluations","case_id",caseId));
        assertEquals(1,count("internship_evaluation_versions","evaluation_id",evaluation));
    }
    @ParameterizedTest @ValueSource(strings={"EVALUATION_DRAFT_SAVED","EVALUATION_SUBMITTED"})
    void auditFailureRollsBackSnapshotsRootPointerAndLedger(String action) throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer),key=id();
        boolean submit=action.equals("EVALUATION_SUBMITTED");
        Object body=submit?Map.of("expectedVersion",1):Map.of("expectedVersion",1,"scores",scores("4","4",List.of()));
        long records=count("operation_records","actor_id",mentor.id);
        String trigger="t310_"+id().replace("-","");
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON audit_logs FOR EACH ROW BEGIN IF NEW.action='"+action+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic evaluation audit fault'; END IF; END");
        try {
            node(reviewer.call(submit?"POST":"PUT",path(evaluation)+(submit?"/submit":""),body,key,true),500);
            assertEquals(1,count("internship_evaluation_versions","evaluation_id",evaluation));
            assertEquals(1,jdbc.queryForObject("SELECT current_version FROM internship_evaluations WHERE id=?",Long.class,evaluation));
            assertEquals(records,count("operation_records","actor_id",mentor.id));
        } finally { jdbc.execute("DROP TRIGGER "+trigger); }
        node(reviewer.call(submit?"POST":"PUT",path(evaluation)+(submit?"/submit":""),body,key,true),200);
    }
    @Test void evidenceUploadBindingAndCleanGuardUseRealT1Service() throws Exception {
        var reviewer=login(mentor,mentorGrant);String evaluation=create(reviewer),file=upload(reviewer,evaluation);
        assertEquals("PENDING",node(reviewer.call("GET","/api/v1/files/"+file+"/metadata",null,null,false),200).get("scanStatus").asString());
        node(reviewer.call("PUT",path(evaluation),Map.of("expectedVersion",1,"scores",scores("5","3",List.of(file))),id(),true),200);
        assertEquals("FILE_NOT_CLEAN",node(reviewer.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",2),id(),true),409).get("errorCode").asString());
        node(login(student,null).call("GET","/api/v1/files/"+file+"/metadata",null,null,false),404);
        // Synthetic scanner output only; does not claim antivirus integration.
        jdbc.update("UPDATE files SET scan_status='CLEAN' WHERE id=?",file);
        node(reviewer.call("POST",path(evaluation)+"/submit",Map.of("expectedVersion",2),id(),true),200);
        assertEquals(2,count("internship_evaluation_files","file_id",file));
        assertEquals(200,reviewer.call("GET","/api/v1/files/"+file+"/content",null,null,false).statusCode());
        jdbc.update("UPDATE internship_context_assignments SET active=false,version=version+1 WHERE id=?",hrGrant);
        int denied=reviewer.call("GET","/api/v1/files/"+file+"/metadata",null,null,false).statusCode();
        assertTrue(denied==403||denied==404,"Revoked file read: "+denied);
    }
}
