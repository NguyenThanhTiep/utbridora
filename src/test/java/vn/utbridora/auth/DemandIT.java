package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.modules.cooperation.domain.*;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated MySQL and real HTTP/session/CSRF. Never uses the developer database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "app.bootstrap.admin.email=", "app.bootstrap.admin.password=", "server.servlet.session.cookie.secure=false",
    "app.t5.demand.enabled=true"})
class DemandIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static { MYSQL.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);
        r.add("spring.datasource.username",MYSQL::getUsername);
        r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired vn.utbridora.auth.modules.cooperation.persistence.TermRepository terms;
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired MembershipRepository memberships;
    @Autowired PasswordEncoder encoder;
    @Autowired vn.utbridora.auth.modules.cooperation.integration.DemandCriteriaPort criteria;
    final String password="Demand-test-password-123";
    String org, other, school; UserAccount user; DemandInput input; Browser browser;
    @BeforeEach void seed() throws Exception {
        org=organization(Organization.Type.ENTERPRISE); other=organization(Organization.Type.ENTERPRISE);
        school=organization(Organization.Type.UNIVERSITY);
        user=new UserAccount(); user.email=UUID.randomUUID()+"@example.test"; user.displayName="Demand tester";
        user.passwordHash=encoder.encode(password); user.status=AccountStatus.ACTIVE; users.saveAndFlush(user);
        Membership m=new Membership(); m.userId=user.id; m.organizationId=org; m.role=Role.ENTERPRISE; memberships.saveAndFlush(m);
        var term=new vn.utbridora.auth.modules.cooperation.domain.Term();term.schoolOrgId=school;term.termCode="TEST-"+UUID.randomUUID();
        term.startDate=LocalDate.parse("2027-01-01");term.endDate=LocalDate.parse("2027-06-01");term.status=vn.utbridora.auth.modules.cooperation.domain.TermStatus.ACTIVE;
        terms.saveAndFlush(term);
        jdbc.update("INSERT INTO academic_catalogs(term_id,school_org_id,catalog_version) VALUES (?,?,'v1')",term.id,school);
        jdbc.update("INSERT INTO academic_catalog_entries(term_id,kind,code,name,major_codes,status) VALUES (?,'MAJOR','IT','Information technology',JSON_ARRAY(),'ACTIVE')",term.id);
        jdbc.update("INSERT IGNORE INTO skill_categories(id,category_code,name,normalized_name,status,version,created_at,updated_at) VALUES ('11111111-1111-4111-8111-111111111111','PROGRAMMING','Programming','programming','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        jdbc.update("INSERT IGNORE INTO skills(id,skill_code,name,normalized_name,category_id,status,version,created_at,updated_at) VALUES ('22222222-2222-4222-8222-222222222222','JAVA','Java','java','11111111-1111-4111-8111-111111111111','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        input=new DemandInput(school,term.id,"Java internship",5,"Build APIs",null,null,
            Instant.parse("2027-01-01T00:00:00Z"),new RequiredCriteria(List.of("IT"),List.of(new SkillCriterion("JAVA",2,null)),
            new DateRange(LocalDate.parse("2027-02-01"),LocalDate.parse("2027-05-01"))),List.of(),"v1","skills-v1");
        browser=new Browser(); browser.csrf();
        assertEquals(200,browser.send("POST","/api/v1/auth/login",Map.of("email",user.email,"password",password),org,true).statusCode());
        browser.csrf();
    }
    String organization(Organization.Type type) {
        var o=new Organization(); o.name="Demand test "+UUID.randomUUID(); o.type=type; o.status=Organization.Status.VERIFIED;
        return organizations.saveAndFlush(o).id;
    }
    class Browser {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String csrf, key=UUID.randomUUID().toString();
        HttpResponse<String> send(String method,String path,Object body,String scope,boolean token) throws Exception {
            var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json").header("X-Organization-Id",scope).header("Idempotency-Key",key);
            if(token && csrf!=null)b.header("X-CSRF-TOKEN",csrf);
            return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(DemandJson.write(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var r=send("GET","/api/v1/auth/csrf",null,org,false); assertEquals(200,r.statusCode());
            csrf=JsonMapper.builder().build().readTree(r.body()).get("token").asString();
        }
    }
    final String path="/api/v1/enterprise/demands";
    DemandDto create() throws Exception {
        var r=browser.send("POST",path,input,org,true); assertEquals(201,r.statusCode(),r.body());
        return DemandJson.read(r.body(),DemandDto.class);
    }
    @Test void createListEditAndImmutableRevision() throws Exception {
        var d=create();
        var list=browser.send("GET",path,null,org,false); assertEquals(200,list.statusCode()); assertTrue(list.body().contains(d.demandId()));
        String snapshot=jdbc.queryForObject("SELECT snapshot_json FROM demand_revisions WHERE demand_id=? AND version=1",String.class,d.demandId());
        var changed=new DemandInput(school,input.termId(),input.title(),6,input.description(),null,null,input.deadline(),input.required(),input.preferred(),input.academicCatalogVersion(),input.taxonomyVersion());
        var r=browser.send("PATCH",path+"/"+d.demandId(),new UpdateDemand(1L,changed),org,true);
        assertEquals(200,r.statusCode(),r.body()); var updated=DemandJson.read(r.body(),DemandDto.class);
        assertEquals(2,updated.version()); assertEquals(2,updated.criteriaVersion());
        assertEquals(snapshot,jdbc.queryForObject("SELECT snapshot_json FROM demand_revisions WHERE demand_id=? AND version=1",String.class,d.demandId()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM demand_revisions WHERE demand_id=?",Integer.class,d.demandId()));
    }
    @Test void replayDoesNotDuplicateAndChangedPayloadConflicts() throws Exception {
        var d=create(); assertEquals(d,create());
        var changed=new DemandInput(school,input.termId(),"Different",5,input.description(),null,null,input.deadline(),input.required(),List.of(),input.academicCatalogVersion(),input.taxonomyVersion());
        var r=browser.send("POST",path,changed,org,true); assertEquals(409,r.statusCode()); assertTrue(r.body().contains("IDEMPOTENCY_KEY_REUSED"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM demands WHERE enterprise_org_id=?",Integer.class,org));
    }
    @Test void updateReplayPrecedesVersionAndRevokedMembershipPreventsCachedDisclosure() throws Exception {
        var d=create();browser.key=UUID.randomUUID().toString();
        var request=new UpdateDemand(1L,input);
        var first=browser.send("PATCH",path+"/"+d.demandId(),request,org,true);
        assertEquals(200,first.statusCode(),first.body());
        assertEquals(first.body(),browser.send("PATCH",path+"/"+d.demandId(),request,org,true).body());
        jdbc.update("UPDATE memberships SET active=false WHERE user_id=?",user.id);
        assertEquals(403,browser.send("PATCH",path+"/"+d.demandId(),request,org,true).statusCode());
    }
    @Test void realTermAndTaxonomyProvidersRejectInventedOrStaleReferences() throws Exception {
        var invented=new DemandInput(school,UUID.randomUUID().toString(),input.title(),5,input.description(),null,null,input.deadline(),input.required(),List.of(),"v1","skills-v1");
        assertEquals(422,browser.send("POST",path,invented,org,true).statusCode());
        var stale=new DemandInput(school,input.termId(),input.title(),5,input.description(),null,null,input.deadline(),input.required(),List.of(),"v1","skills-v999");
        var result=browser.send("POST",path,stale,org,true);assertEquals(409,result.statusCode(),result.body());assertTrue(result.body().contains("CATALOG_CHANGED"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE actor_id=?",Integer.class,user.id));
    }
    @Test void rejectsCrossOrganizationAndMissingCsrf() throws Exception {
        var d=create();
        assertEquals(403,browser.send("GET",path,null,other,false).statusCode());
        assertEquals(403,browser.send("PATCH",path+"/"+d.demandId(),new UpdateDemand(1L,input),other,true).statusCode());
        var csrf=browser.send("POST",path,input,org,false);
        assertEquals(403,csrf.statusCode()); assertTrue(csrf.body().contains("CSRF_INVALID"));
        var anonymous=new Browser().send("GET",path,null,org,false);
        assertEquals(401,anonymous.statusCode()); assertTrue(anonymous.body().contains("SESSION_EXPIRED"));
        assertTrue(anonymous.body().contains("fieldErrors")); assertTrue(anonymous.body().contains("requestId"));
    }
    @Test void staleVersionAndLockedDraftCannotBeEdited() throws Exception {
        var d=create();
        assertEquals(409,browser.send("PATCH",path+"/"+d.demandId(),new UpdateDemand(0L,input),org,true).statusCode());
        jdbc.update("UPDATE demands SET state='READY' WHERE id=?",d.demandId());
        var r=browser.send("PATCH",path+"/"+d.demandId(),new UpdateDemand(1L,input),org,true);
        assertEquals(409,r.statusCode()); assertTrue(r.body().contains("DEMAND_LOCKED"));
    }
    @Test void snapshotPortReadsPersistedRevisionAndRejectsStaleOrUnsupportedPurpose() throws Exception {
        var d=create();
        var principal=new vn.utbridora.auth.security.AppPrincipal(user.id,user.email,null,user.securityVersion,List.of());
        var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
        var purpose=vn.utbridora.auth.modules.cooperation.integration.DemandCriteriaPort.Purpose.EVALUATE;
        var snapshot=criteria.read(auth,org,d.demandId(),1L,purpose);
        assertEquals(d.demandId(),snapshot.demandId()); assertEquals(input.required(),snapshot.required());
        assertEquals(DemandJson.criteriaHash(input),snapshot.criteriaHash());
        assertEquals("CRITERIA_STALE",assertThrows(DemandFailure.class,()->criteria.read(auth,org,d.demandId(),0L,purpose)).code());
        assertEquals(403,assertThrows(DemandFailure.class,()->criteria.read(auth,org,d.demandId(),1L,
            vn.utbridora.auth.modules.cooperation.integration.DemandCriteriaPort.Purpose.PLACEMENT)).status());
    }
    @Test void invalidCatalogDoesNotPersistCommandOrDemand() throws Exception {
        var bad=new DemandInput(school,input.termId(),input.title(),5,input.description(),null,null,input.deadline(),
            new RequiredCriteria(List.of("UNKNOWN"),List.of(),input.required().dateRange()),List.of(),input.academicCatalogVersion(),input.taxonomyVersion());
        assertEquals(422,browser.send("POST",path,bad,org,true).statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM demands WHERE enterprise_org_id=?",Integer.class,org));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM operation_records WHERE actor_id=?",Integer.class,user.id));
    }
    @Test void unverifiedOrganizationLosesEffectiveGrantAndDemandAccess() throws Exception {
        jdbc.update("UPDATE organizations SET status='PENDING' WHERE id=?",org);
        assertEquals(403,browser.send("GET",path,null,org,false).statusCode());
        var permissions=browser.send("GET","/api/v1/auth/me/permissions",null,org,false);
        assertEquals(200,permissions.statusCode()); assertEquals("[]",permissions.body());
    }
    @Test void concurrentUpdatesAllowOnlyOneWinner() throws Exception {
        var d=create(); var second=new Browser(); second.csrf();
        assertEquals(200,second.send("POST","/api/v1/auth/login",Map.of("email",user.email,"password",password),org,true).statusCode()); second.csrf();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=executor.submit(()->{ start.await(); return browser.send("PATCH",path+"/"+d.demandId(),new UpdateDemand(1L,input),org,true).statusCode(); });
            var next=executor.submit(()->{ start.await(); return second.send("PATCH",path+"/"+d.demandId(),new UpdateDemand(1L,input),org,true).statusCode(); });
            start.countDown();
            var statuses=new ArrayList<>(List.of(first.get(30,java.util.concurrent.TimeUnit.SECONDS),next.get(30,java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(statuses); assertEquals(List.of(200,409),statuses);
        }
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM demand_revisions WHERE demand_id=?",Integer.class,d.demandId()));
    }
    @Test void auditFailureRollsBackDraftAndCommand() throws Exception {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT demand_test_audit CHECK (actor_id <> '"+user.id+"' OR reason_code IS NULL OR reason_code <> 'DEMAND_CREATE')");
        try {
            var r=browser.send("POST",path,input,org,true); assertEquals(500,r.statusCode(),r.body());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM demands WHERE enterprise_org_id=?",Integer.class,org));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM operation_records WHERE actor_id=?",Integer.class,user.id));
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CHECK demand_test_audit"); }
    }
    @Test void expiredReplayDoesNotCreateAnotherDemand() throws Exception {
        create();
        jdbc.update("UPDATE operation_records SET response_expires_at=DATE_SUB(UTC_TIMESTAMP(), INTERVAL 1 HOUR) WHERE actor_id=?",user.id);
        var r=browser.send("POST",path,input,org,true);
        assertEquals(409,r.statusCode()); assertTrue(r.body().contains("IDEMPOTENCY_RESPONSE_EXPIRED"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM demands WHERE enterprise_org_id=?",Integer.class,org));
    }
    @Test void paginationFiltersAndSoftDeleteAreApplied() throws Exception {
        var d=create();
        var missing=browser.send("GET",path+"?termId="+UUID.randomUUID(),null,org,false);
        assertEquals(200,missing.statusCode()); assertFalse(missing.body().contains(d.demandId()));
        assertFalse(browser.send("GET",path+"?page=1&size=1",null,org,false).body().contains(d.demandId()));
        assertFalse(browser.send("GET",path+"?state=READY",null,org,false).body().contains(d.demandId()));
        assertEquals(400,browser.send("GET",path+"?size=101",null,org,false).statusCode());
        jdbc.update("UPDATE demands SET soft_deleted_at=UTC_TIMESTAMP() WHERE id=?",d.demandId());
        assertFalse(browser.send("GET",path,null,org,false).body().contains(d.demandId()));
        assertTrue(browser.send("GET",path+"?includeDeleted=true",null,org,false).body().contains(d.demandId()));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="frontend.e2e",matches="true")
    void browserUsesRealDemandApiAndDatabase() throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npx.cmd playwright test tests/demand-live.spec.ts"):
            new ProcessBuilder("npx","playwright","test","tests/demand-live.spec.ts");
        builder.directory(new java.io.File("web-demo"));
        var env=builder.environment(); env.put("API_PROXY_TARGET","http://localhost:"+port);
        env.put("E2E_REPORT_FILE","../target/demand-browser-results.xml");
        env.put("DEMAND_E2E_EMAIL",user.email); env.put("DEMAND_E2E_PASSWORD",password);
        env.put("DEMAND_E2E_SCHOOL",school); env.put("DEMAND_E2E_TERM",input.termId());
        env.put("DEMAND_E2E_TAXONOMY",input.taxonomyVersion());
        env.put("DEMAND_E2E_ACADEMIC",input.academicCatalogVersion());
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/demand-live-e2e.log"));
        var process=builder.start();
        if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Browser timed out"); }
        assertEquals(0,process.exitValue(),"See target/demand-live-e2e.log");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM demands WHERE enterprise_org_id=?",Integer.class,org));
        assertEquals(2,jdbc.queryForObject("SELECT version FROM demands WHERE enterprise_org_id=?",Integer.class,org));
    }
}
