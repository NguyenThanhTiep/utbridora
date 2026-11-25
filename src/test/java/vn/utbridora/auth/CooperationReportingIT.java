package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Real HTTP/session/MySQL and Chromium. Synthetic prerequisites live exclusively in this ephemeral container. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.local.enabled=false",
    "app.t5.placement-delivery.enabled=false","app.t3.week3.http.enabled=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CooperationReportingIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static final java.nio.file.Path FILES=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {try{return java.nio.file.Files.createTempDirectory("cooperation-reporting-");}catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);p.add("app.files.storage-root",FILES::toString);
    }
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    @LocalServerPort int port;
    final String password="Synthetic-Reporting-"+UUID.randomUUID();String hash;
    String school,enterprise,term,delegation;
    UserAccount admin,staff,hr,outsider;
    record Session(HttpClient client,String csrfHeader,String csrfToken,String context) {
        Session context(String id) {return new Session(client,csrfHeader,csrfToken,id);}
    }
    record Reply(int status,tools.jackson.databind.JsonNode body,HttpHeaders headers) {}
    @BeforeAll void passwordHash() {hash=encoder.encode(password);}
    String demandId,coopId;
    @BeforeEach void fixture() {
        assertTrue(MYSQL.isRunning());school=org(Organization.Type.UNIVERSITY);enterprise=org(Organization.Type.ENTERPRISE);term=id();demandId=id();coopId=id();
        admin=user(Role.ADMIN,null,"Reporting admin");staff=user(Role.SCHOOL_STAFF,school,"Reporting school");hr=user(Role.ENTERPRISE,enterprise,"Reporting HR");outsider=user(Role.SCHOOL_STAFF,org(Organization.Type.UNIVERSITY),"Other school");
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2020-01-01','2099-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,"REPORT-"+id());
        delegation=id();jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_REPORTING',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions VALUES (?,'cooperation:school:review')",delegation);
        var input=new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput(school,term,"Synthetic reporting internship",260,"Synthetic reporting description",null,null,Instant.parse("2099-01-01T00:00:00Z"),new vn.utbridora.auth.modules.cooperation.domain.DemandModels.RequiredCriteria(List.of("IT"),List.of(),new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DateRange(LocalDate.parse("2020-01-01"),LocalDate.parse("2099-01-01"))),List.of(),"SYNTHETIC","SYNTHETIC");
        jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES (?,?,?,?,'READY','VALID',26,26,REPEAT('a',64),?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",demandId,enterprise,school,term,JSON.writeValueAsString(input));
        jdbc.update("INSERT INTO cooperations(id,demand_id,school_org_id,enterprise_org_id,term_id,state,submission_no,version,submitted_at,created_at) VALUES (?,?,?,?,?,'SUBMITTED',26,26,'2026-01-27 00:00:00',UTC_TIMESTAMP(6))",coopId,demandId,school,enterprise,term);
        jdbc.update("UPDATE demands SET cooperation_id=? WHERE id=?",coopId,demandId);
        for(int n=1;n<=26;n++) jdbc.update("INSERT INTO cooperation_history VALUES (?,?,?,?,'SUBMIT',?,?)",id(),coopId,n,hr.id,JSON.writeValueAsString(Map.of("demand",Map.of("version",n,"criteriaVersion",n,"data",Map.of("quantity",n*10)))),java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(n*86400L)));
        jdbc.update("INSERT INTO cooperation_history VALUES (?,?,1,?,'REJECT',?,'2026-01-02 01:00:00')",id(),coopId,staff.id,JSON.writeValueAsString(new vn.utbridora.auth.modules.cooperation.application.CooperationService.Decision(1L,"REJECT","CRITERIA_CHANGED","HISTORIC-1",List.of())));
        audit(staff.id,demandId,"MATCH_RUN_COMPLETED","2026-01-01 00:00:00");audit(staff.id,demandId,"MATCH_RUN_COMPLETED","2026-01-01 01:00:00");audit(hr.id,demandId,"MATCH_RUN_COMPLETED","2026-01-02 00:00:00");audit(hr.id,id(),"MATCH_RUN_COMPLETED","2026-01-01 00:00:00");audit(staff.id,demandId,"MATCH_RUN_REQUESTED","2026-01-01 00:00:00");
    }
    void audit(String actor,String demand,String action,String time) {jdbc.update("INSERT INTO audit_logs(id,actor_id,action,resource_type,resource_id,result,created_at,demand_id) VALUES (?,?,?,'match_run',?,'SUCCESS',?,?)",id(),actor,action,id(),time,demand);}
    private String org(Organization.Type type) {var o=new Organization();o.name="Synthetic "+id();o.type=type;o.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(o).id;}
    private UserAccount user(Role role,String org,String name) {var u=new UserAccount();u.email=id()+"@example.test";u.displayName=name;u.passwordHash=hash;u.status=AccountStatus.ACTIVE;users.saveAndFlush(u);var m=new Membership();m.userId=u.id;m.organizationId=org;m.role=role;memberships.saveAndFlush(m);return u;}
    static String id() {return UUID.randomUUID().toString();}
    String url(String path) {return "http://localhost:"+port+"/api/v1"+path;}
    Session login(UserAccount user) throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(url("/auth/csrf"))).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        var response=client.send(HttpRequest.newBuilder(URI.create(url("/auth/login"))).header(csrf.get("headerName").asString(),csrf.get("token").asString()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",user.email,"password",password)))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,response.statusCode(),response.body());
        csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(url("/auth/csrf"))).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        return new Session(client,csrf.get("headerName").asString(),csrf.get("token").asString(),null);
    }
    Reply call(Session s,String method,String path,Object body,String key,boolean csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url(path))).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(csrf) request.header(s.csrfHeader(),s.csrfToken());if(key!=null) request.header("Idempotency-Key",key);if(s.context()!=null) request.header("X-Context-Assignment-Id",s.context());
        if(path.startsWith("/school/")) request.header("X-Organization-Id",school);
        var r=s.client().send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(),r.body().isBlank()?null:JSON.readTree(r.body()),r.headers());
    }
    Reply get(Session s,String path) throws Exception {return call(s,"GET",path,null,null,false);}
    String history() {return "/school/cooperations/"+coopId+"/submission-history";}
    @Test void historyPreservesSubmittedVersionsDecisionsAndPaginationWithoutWritingCurrentDemand() throws Exception {
        var actor=login(staff);var first=get(actor,history()+"?size=1");assertEquals(200,first.status(),first.body().toString());assertEquals(26,first.body().get("totalElements").asInt());assertEquals(26,first.body().get("content").get(0).get("submissionNo").asInt());
        var old=get(actor,history()+"?page=25&size=1").body().get("content").get(0);assertEquals(1,old.get("demandVersion").asInt());assertEquals(1,old.get("criteriaVersion").asInt());assertEquals(10,old.get("quantitySnapshot").asInt());assertEquals("REJECT",old.get("decision").asString());assertEquals("HISTORIC-1",old.get("decisionRef").asString());
        assertEquals(26,jdbc.queryForObject("SELECT version FROM demands WHERE id=?",Integer.class,demandId));assertEquals(400,get(actor,history()+"?size=101").status());
        assertFalse(first.body().toString().contains("password"));assertFalse(first.body().toString().contains("studentUserId"));
    }
    @Test void schoolHistoryRequiresCurrentDelegationAndNeverGrantsAdminOrEnterpriseBypass() throws Exception {
        assertEquals(404,get(login(admin),history()).status());assertEquals(404,get(login(hr),history()).status());assertEquals(404,get(login(outsider),history()).status());
        var actor=login(staff);assertEquals(200,get(actor,history()).status());jdbc.update("UPDATE school_delegations SET active=false WHERE id=?",delegation);assertEquals(404,get(actor,history()).status());
    }
    @Test void matchingStatisticsRequireGlobalAuditAndUseInclusiveFromExclusiveUntil() throws Exception {
        String path="/admin/matching-statistics?demandId="+demandId;
        assertEquals(403,get(login(staff),path).status());assertEquals(403,get(login(hr),path).status());var actor=login(admin);
        var all=get(actor,path);assertEquals(200,all.status());assertEquals(3,all.body().get("totalExecutions").asInt());assertEquals(2,all.body().get("distinctActors").asInt());
        var filtered=get(actor,path+"&from=2026-01-01T00:00:00Z&until=2026-01-02T00:00:00Z").body();assertEquals(2,filtered.get("totalExecutions").asInt());assertEquals(1,filtered.get("distinctActors").asInt());assertEquals("2026-01-01T00:00:00Z",filtered.get("firstExecutionAt").asString());
        assertEquals(400,get(actor,path+"&from=2026-01-02T00:00:00Z&until=2026-01-01T00:00:00Z").status());assertEquals(400,get(actor,"/admin/matching-statistics?demandId=bad").status());
        var empty=get(actor,path+"&until=2025-01-01T00:00:00Z").body();assertEquals(0,empty.get("totalExecutions").asInt());assertTrue(empty.get("firstExecutionAt")==null || empty.get("firstExecutionAt").isNull());
    }
    @Test void reportingScreensWorkInRealChromiumWithIsolatedMySql() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("frontend.e2e"),"Enable frontend.e2e for real Chromium");
        var config=Map.of("password",password,"staffEmail",staff.email,"adminEmail",admin.email,"demandId",demandId,"cooperationId",coopId);
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- cooperation-reporting-live.spec.ts"):new ProcessBuilder("npm","run","test:e2e","--","cooperation-reporting-live.spec.ts");builder.directory(new java.io.File("web-demo"));builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);builder.environment().put("E2E_COOPERATION_REPORTING",JSON.writeValueAsString(config));builder.environment().put("E2E_REPORT_FILE","../target/cooperation-reporting-browser.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/cooperation-reporting-browser.log"));var process=builder.start();try {if(!process.waitFor(240,TimeUnit.SECONDS))fail("Reporting browser timed out");assertEquals(0,process.exitValue(),"See target/cooperation-reporting-browser.log");}finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        assertEquals(26,jdbc.queryForObject("SELECT version FROM cooperations WHERE id=?",Integer.class,coopId));
    }
}
