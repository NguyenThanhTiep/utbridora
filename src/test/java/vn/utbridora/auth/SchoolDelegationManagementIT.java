package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.application.CooperationService;
import vn.utbridora.auth.user.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Real HTTP/CSRF/current authority and Chromium on an isolated MySQL database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-delegation-user-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-delegation-admin-password-456",
    "app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchoolDelegationManagementIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JdbcTemplate jdbc;@Autowired AuthorizationPolicy core;@Autowired CooperationService cooperations;
    @Autowired UserRepository users;@Autowired MembershipService memberships;
    @Autowired vn.utbridora.auth.organization.OrganizationRepository organizations;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    static String id(int i) {return "00000000-0000-4000-8000-"+String.format("%012d",i);}
    record Session(HttpClient client,String header,String token) {}
    record Reply(int status,tools.jackson.databind.JsonNode body) {}
    Session login(String email) throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(url("/auth/csrf"))).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        String password=email.equals("admin@example.test")?"Synthetic-delegation-admin-password-456":"Synthetic-delegation-user-password-123";
        var request=HttpRequest.newBuilder(URI.create(url("/auth/login"))).header("Content-Type","application/json").header(csrf.get("headerName").asString(),csrf.get("token").asString())
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",email,"password",password)))).build();
        assertEquals(200,client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode());
        csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(url("/auth/csrf"))).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        return new Session(client,csrf.get("headerName").asString(),csrf.get("token").asString());
    }
    String url(String path) {return "http://localhost:"+port+"/api/v1"+path;}
    Reply call(Session session,String method,String path,Object body,String key,boolean csrf) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url(path))).header("Content-Type","application/json");
        if(csrf) builder.header(session.header(),session.token());
        if(key!=null) builder.header("Idempotency-Key",key);
        if(path.startsWith("/school/cooperations")) builder.header("X-Organization-Id",id(11));
        var reply=session.client().send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(reply.statusCode(),reply.body().isBlank()?null:JSON.readTree(reply.body()));
    }
    Reply get(Session s,String path) throws Exception {return call(s,"GET",path,null,null,false);}
    String key() {return UUID.randomUUID().toString();}
    Map<String,Object> grant(String user,List<String> permissions,String ref,boolean root) {
        var body=new LinkedHashMap<String,Object>();body.put("userId",user);body.put("permissionCodes",permissions);body.put("profileReadFields",List.of());
        body.put("validFrom",Instant.now().minusSeconds(120).toString());body.put("validUntil",Instant.now().plusSeconds(3600).toString());body.put("decisionRef",ref);
        if(root) {body.put("schoolOrgId",id(11));body.put("termId",id(21));}return body;
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test @Order(1) void currentScopeReadsAndAuditedCommandsEnforceBoundaries() throws Exception {
        String adminId=jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'",String.class);
        SecurityContextHolder.getContext().setAuthentication(core.currentSubject(adminId).orElseThrow());
        var delegate=new UserAccount();delegate.id=id(201);delegate.email="delegate@example.test";delegate.displayName="Demo delegated reviewer";
        delegate.passwordHash=passwords.encode("Synthetic-delegation-user-password-123");delegate.status=AccountStatus.ACTIVE;users.saveAndFlush(delegate);
        memberships.grant(adminId,delegate.id,Role.SCHOOL_STAFF,id(11));
        var other=new vn.utbridora.auth.organization.Organization();other.id=id(301);other.name="Synthetic other school";other.type=vn.utbridora.auth.organization.Organization.Type.UNIVERSITY;other.status=vn.utbridora.auth.organization.Organization.Status.VERIFIED;organizations.saveAndFlush(other);
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES(?,?,'OTHER-TERM','2026-01-01','2027-12-31','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(302),id(301));
        var hr=core.currentSubject(id(4)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(hr);
        String demand=jdbc.queryForObject("SELECT id FROM demands LIMIT 1",String.class);
        cooperations.submitWithCooperation(hr,id(12),key(),new CooperationService.Submit(demand,1L));
        var admin=login("admin@example.test");var schoolOld=login("school@example.test");var reviewerOld=login(delegate.email);var enterprise=login("hr@example.test");
        assertEquals(404,get(schoolOld,"/school/cooperations").status());
        assertEquals(403,get(enterprise,"/admin/school-delegations/scopes").status());
        assertEquals(0,get(schoolOld,"/school/school-delegations/scopes").body().get("totalElements").asInt());
        assertEquals(404,get(schoolOld,"/school/school-delegations/options?termId="+id(21)).status());
        assertEquals(2,get(admin,"/admin/school-delegations/scopes").body().get("totalElements").asInt());
        assertEquals(200,get(admin,"/admin/school-delegations/options?termId="+id(302)).status());
        assertEquals(404,get(schoolOld,"/school/school-delegations/options?termId="+id(302)).status());
        assertEquals(400,get(admin,"/admin/school-delegations/scopes?page=10001").status());
        assertEquals(404,get(admin,"/admin/school-delegations/options?termId="+id(999)).status());
        var root=grant(id(2),List.of("school:delegation:manage","cooperation:school:review"),"DEMO-HTTP-ROOT",true);
        root.put("validUntil",Instant.now().plusSeconds(7200).toString());
        assertEquals(403,call(admin,"POST","/admin/school-delegations",root,key(),false).status());
        var invalid=new LinkedHashMap<>(root);invalid.put("decisionRef","REAL-DECISION");assertEquals(422,call(admin,"POST","/admin/school-delegations",invalid,key(),true).status());
        invalid=new LinkedHashMap<>(root);invalid.put("schoolOrgId",id(12));assertEquals(403,call(admin,"POST","/admin/school-delegations",invalid,key(),true).status());
        invalid=new LinkedHashMap<>(root);invalid.put("userId",adminId);assertEquals(403,call(admin,"POST","/admin/school-delegations",invalid,key(),true).status());
        invalid=new LinkedHashMap<>(root);invalid.put("schoolOrgId",id(301));invalid.put("termId",id(302));assertEquals(403,call(admin,"POST","/admin/school-delegations",invalid,key(),true).status());
        String operation=key();var created=call(admin,"POST","/admin/school-delegations",root,operation,true);assertEquals(201,created.status(),created.body().toString());String rootId=created.body().get("id").asString();
        assertEquals(rootId,call(admin,"POST","/admin/school-delegations",root,operation,true).body().get("id").asString());
        invalid=new LinkedHashMap<>(root);invalid.put("decisionRef","DEMO-CHANGED");assertEquals(409,call(admin,"POST","/admin/school-delegations",invalid,operation,true).status());
        assertEquals(401,get(schoolOld,"/school/cooperations").status());
        var school=login("school@example.test");assertEquals(200,get(school,"/school/cooperations").status());
        var options=get(school,"/school/school-delegations/options?termId="+id(21));assertEquals(200,options.status());assertEquals(rootId,options.body().get("authorities").get(0).get("id").asString());
        assertEquals(404,get(school,"/school/school-delegations/candidates?termId="+id(302)).status());
        assertEquals(404,get(school,"/school/school-delegations?termId="+id(302)).status());
        assertEquals(1,get(school,"/school/school-delegations/scopes").body().get("totalElements").asInt());
        var candidates=get(school,"/school/school-delegations/candidates?termId="+id(21)+"&search=delegate%40example.test");assertEquals(1,candidates.body().get("totalElements").asInt());assertEquals(id(201),candidates.body().get("content").get(0).get("userId").asString());
        assertFalse(get(school,"/school/school-delegations/candidates?termId="+id(21)).body().toString().contains("password"));
        var child=grant(id(201),List.of("cooperation:school:review"),"SCHOOL-HTTP-CHILD",false);
        invalid=new LinkedHashMap<>(child);invalid.put("userId",id(2));assertEquals(403,call(school,"POST","/school/terms/"+id(21)+"/delegations",invalid,key(),true).status());
        invalid=new LinkedHashMap<>(child);invalid.put("permissionCodes",List.of("placement:school:write"));assertEquals(403,call(school,"POST","/school/terms/"+id(21)+"/delegations",invalid,key(),true).status());
        invalid=new LinkedHashMap<>(child);invalid.put("validUntil",Instant.now().plusSeconds(14400).toString());assertEquals(403,call(school,"POST","/school/terms/"+id(21)+"/delegations",invalid,key(),true).status());
        // An audit failure must roll back the delegation, security invalidation and operation record together.
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations",Long.class);long security=jdbc.queryForObject("SELECT security_version FROM users WHERE id=?",Long.class,id(201));
        String prior=jdbc.queryForList("SELECT resource_id FROM audit_logs WHERE action='CAPABILITY_GRANTED'",String.class).stream().map(v->"'"+UUID.fromString(v)+"'").collect(java.util.stream.Collectors.joining(","));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT delegation_audit_failure CHECK(action<>'CAPABILITY_GRANTED' OR resource_id IN ("+prior+"))");
        try {assertEquals(500,call(school,"POST","/school/terms/"+id(21)+"/delegations",child,key(),true).status());}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK delegation_audit_failure");}
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM school_delegations",Long.class));assertEquals(security,jdbc.queryForObject("SELECT security_version FROM users WHERE id=?",Long.class,id(201)));
        var granted=call(school,"POST","/school/terms/"+id(21)+"/delegations",child,key(),true);assertEquals(201,granted.status(),granted.body().toString());String childId=granted.body().get("id").asString();
        assertEquals(401,get(reviewerOld,"/school/cooperations").status());var reviewer=login(delegate.email);assertEquals(1,get(reviewer,"/school/cooperations").body().get("totalElements").asInt());
        assertEquals(404,get(reviewer,"/school/school-delegations/"+childId).status());
        var detail=get(admin,"/admin/school-delegations/"+childId);assertFalse(detail.body().get("canRevoke").asBoolean());assertEquals(rootId,detail.body().get("parentId").asString());
        assertEquals(409,call(admin,"DELETE","/school/delegations/"+rootId,Map.of("expectedVersion",2,"reasonCode","SECURITY_REVIEW"),key(),true).status());
        String revokeKey=key();var revoke=Map.of("expectedVersion",1,"reasonCode","SECURITY_REVIEW");
        assertEquals(200,call(admin,"DELETE","/school/delegations/"+rootId,revoke,revokeKey,true).status());
        assertEquals(200,call(admin,"DELETE","/school/delegations/"+rootId,revoke,revokeKey,true).status());
        assertEquals(401,get(reviewer,"/school/cooperations").status());assertEquals(401,get(school,"/school/cooperations").status());
        assertEquals(404,get(login(delegate.email),"/school/cooperations").status());
        var inactive=get(admin,"/admin/school-delegations/"+childId).body();assertTrue(inactive.get("delegation").get("active").asBoolean());assertEquals(0,inactive.get("effectivePermissionCodes").size());
        assertEquals(0,get(login("school@example.test"),"/school/school-delegations/scopes").body().get("totalElements").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CAPABILITY_REVOKED' AND resource_id=?",Integer.class,rootId));
    }
    @Test @Order(2) void browserUsesRealGrantReadAndRevokeApis() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("frontend.e2e"),"Enable frontend.e2e for Chromium verification");
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- delegations-live.spec.ts"):new ProcessBuilder("npm","run","test:e2e","--","delegations-live.spec.ts");
        builder.directory(new java.io.File("web-demo"));builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);
        builder.environment().put("E2E_DELEGATIONS","true");builder.environment().put("E2E_PASSWORD","Synthetic-delegation-user-password-123");builder.environment().put("E2E_ADMIN_PASSWORD","Synthetic-delegation-admin-password-456");builder.environment().put("E2E_REPORT_FILE","../target/delegations-browser-results.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/delegations-browser.log"));var process=builder.start();
        if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)) {process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();fail("Browser timeout");}
        assertEquals(0,process.exitValue(),"See target/delegations-browser.log");
    }
}
