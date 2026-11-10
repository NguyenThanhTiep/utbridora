package vn.utbridora.auth.modules.student;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.SchoolDelegationService;
import vn.utbridora.auth.modules.cooperation.application.CooperationService;
import vn.utbridora.auth.modules.cooperation.application.DemandService;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService;
import vn.utbridora.auth.modules.student.application.StudentPreferenceService.*;
import vn.utbridora.auth.modules.student.application.UnavailableStudentOpportunity;
import vn.utbridora.auth.modules.student.port.StudentOpportunityPort;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/**
 * T2-07 Preference (BC22, DEV02 implementation) on a dedicated database with the synthetic local seed. The T5
 * Opportunity adapter does not exist yet: an isolated fixture stands in for it in this test only. It is never part
 * of the application and passing here is not evidence of T5 integration.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.local.enabled=true","DEMO_PASSWORD="+StudentPreferenceIT.PASSWORD,
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-week3-admin-password-456","app.t5.placement-delivery.enabled=false","logging.level.org.springframework.jdbc=WARN"})
@ActiveProfiles("local")
class StudentPreferenceIT {
    static final String PASSWORD="Synthetic-week3-test-password-123";
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}

    /** Isolated test fixture for the missing T5 adapter; ids are synthetic opportunities in DEMO School A, term DEMO-WEEK1. */
    @TestConfiguration static class Opportunities {
        static final Map<String,StudentOpportunityPort.OpportunityRef> PUBLISHED=new ConcurrentHashMap<>();
        @Bean @Primary StudentOpportunityPort fixtureOpportunities() {return (student,id)->Optional.ofNullable(PUBLISHED.get(id));}
    }

    @Autowired JdbcTemplate jdbc;@Autowired AuthorizationPolicy core;@Autowired SchoolDelegationService delegations;@Autowired StudentPreferenceService preferences;
    @Autowired DemandService demands;@Autowired CooperationService cooperations;
    @LocalServerPort int port;
    static final AtomicBoolean DELEGATED=new AtomicBoolean();
    static String id(int i) {return "00000000-0000-4000-8000-"+String.format("%012d",i);}
    static String key() {return UUID.randomUUID().toString();}
    Authentication as(String user) {var a=core.currentSubject(user).orElseThrow();SecurityContextHolder.getContext().setAuthentication(a);return a;}
    Authentication as(int user) {return as(id(user));}
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    /** BC22 opportunityId=cooperationId and V28 references cooperations(id): create a real cooperation through the T5 services. */
    String cooperation() {
        var hr=as(4);var d=demands.list(hr,id(12),null,null,false,0,25).content().getFirst().data();
        var demand=demands.create(hr,id(12),key(),new DemandModels.DemandInput(d.schoolOrgId(),d.termId(),"T2 preference fixture",d.quantity(),d.description(),
            d.location(),d.benefits(),d.deadline(),d.required(),d.preferred(),d.academicCatalogVersion(),d.taxonomyVersion()));
        return cooperations.submitWithCooperation(hr,id(12),key(),new CooperationService.Submit(demand.demandId(),demand.version())).cooperation().cooperationId();
    }
    String publish(Instant closedAt) {
        String id=cooperation();Opportunities.PUBLISHED.put(id,new StudentOpportunityPort.OpportunityRef(id,id(11),id(12),id(21),Instant.now().minusSeconds(86400),closedAt,1));
        return id;
    }
    static Instant open() {return Instant.now().plusSeconds(7*86400);}
    static void code(int status,String code,Executable call) {
        var failure=assertThrows(OperationFailure.class,call);assertEquals(code,failure.code);assertEquals(status,failure.status,failure.code);
    }
    long count(String sql,Object... args) {return jdbc.queryForObject(sql,Long.class,args);}
    static SubmitPreference submit(String opportunity,String message,int expectedIntentVersion) {return new SubmitPreference("SUBMIT",opportunity,message,expectedIntentVersion);}
    static WithdrawPreference withdraw(String preference,long expectedVersion) {return new WithdrawPreference("WITHDRAW",preference,expectedVersion,"STUDENT_WITHDREW");}
    /** School staff 2 gets a current review delegation for school 11 / term 21, as an admin would grant it. */
    void delegate() {
        if(DELEGATED.getAndSet(true)) return;
        var admin=as(jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'",String.class));
        delegations.bootstrap(admin,key(),new SchoolDelegationService.Bootstrap(id(2),id(11),id(21),List.of("student:preference:school:review"),List.of(),
            Instant.now().minusSeconds(60),Instant.now().plusSeconds(86400),"DEMO-T2-W3-TEST"));
    }

    @Test void submitIsIdempotentVersionedAndDoesNotTouchInvitationConsentOrPlacement() {
        String opportunity=publish(open());
        long invitations=count("SELECT COUNT(*) FROM internship_invitations"),consents=count("SELECT COUNT(*) FROM student_consents"),placements=count("SELECT COUNT(*) FROM placements");
        String key=key();var request=submit(opportunity,"  Em mong muốn vị trí này  ",0);
        var first=preferences.submit(as(6),key,request);
        assertEquals(1,first.intentVersion());assertEquals(1,first.version());assertEquals("SUBMITTED",first.status());
        assertEquals(id(11),first.schoolOrgId());assertEquals(id(21),first.termId());assertEquals("Em mong muốn vị trí này",first.message());
        assertEquals(first,preferences.submit(as(6),key,request),"Same key and body replays the first result");
        assertEquals(1,count("SELECT COUNT(*) FROM student_preference_history WHERE preference_id=?",first.preferenceId()));
        assertEquals(1,count("SELECT COUNT(*) FROM audit_logs WHERE action='PREFERENCE_SUBMITTED' AND resource_id=?",first.preferenceId()));
        code(409,"IDEMPOTENCY_KEY_REUSED",()->preferences.submit(as(6),key,submit(opportunity,"Nội dung khác",0)));
        code(409,"VERSION_CONFLICT",()->preferences.submit(as(6),key(),request));
        code(409,"STATE_CONFLICT",()->preferences.submit(as(6),key(),submit(opportunity,null,1)));
        assertEquals(invitations,count("SELECT COUNT(*) FROM internship_invitations"));
        assertEquals(consents,count("SELECT COUNT(*) FROM student_consents"));
        assertEquals(placements,count("SELECT COUNT(*) FROM placements"));
    }

    @Test void withdrawThenResubmitKeepsHistoryAndRejectsWrongOwnerVersionOrState() {
        String opportunity=publish(open());
        var created=preferences.submit(as(6),key(),submit(opportunity,null,0));
        code(404,"RESOURCE_NOT_FOUND",()->preferences.withdraw(as(7),key(),withdraw(created.preferenceId(),1)));
        code(409,"VERSION_CONFLICT",()->preferences.withdraw(as(6),key(),withdraw(created.preferenceId(),2)));
        var withdrawn=preferences.withdraw(as(6),key(),withdraw(created.preferenceId(),1));
        assertEquals("WITHDRAWN",withdrawn.status());assertEquals(2,withdrawn.version());assertNotNull(withdrawn.withdrawnAt());
        code(409,"STATE_CONFLICT",()->preferences.withdraw(as(6),key(),withdraw(created.preferenceId(),2)));
        var again=preferences.submit(as(6),key(),submit(opportunity,"Lần hai",1));
        assertEquals(created.preferenceId(),again.preferenceId());assertEquals(2,again.intentVersion());assertEquals(3,again.version());assertNull(again.withdrawnAt());
        assertEquals(List.of("SUBMIT","WITHDRAW","SUBMIT"),jdbc.queryForList("SELECT operation FROM student_preference_history WHERE preference_id=? ORDER BY version",String.class,created.preferenceId()));
        assertTrue(preferences.mine(as(6),null,0,100).content().stream().anyMatch(p->p.preferenceId().equals(created.preferenceId()) && p.intentVersion()==2));
        assertTrue(preferences.mine(as(7),null,0,100).content().stream().noneMatch(p->p.preferenceId().equals(created.preferenceId())));
    }

    @Test void reviewNeedsTheSchoolDelegationAndIsTerminal() {
        String opportunity=publish(open());
        var created=preferences.submit(as(6),key(),submit(opportunity,null,0));
        var review=new ReviewPreference(1L,"SCHOOL_REVIEWED");
        code(404,"RESOURCE_NOT_FOUND",()->preferences.review(as(4),key(),created.preferenceId(),review));
        code(404,"RESOURCE_NOT_FOUND",()->preferences.review(as(6),key(),created.preferenceId(),review));
        delegate();
        var reviewed=preferences.review(as(2),key(),created.preferenceId(),review);
        assertEquals("REVIEWED",reviewed.status());assertEquals(2,reviewed.version());assertEquals(id(2),reviewed.reviewedByUserId());
        var metadata=JSON.readTree(jdbc.queryForObject("SELECT metadata_json FROM audit_logs WHERE action='PREFERENCE_REVIEWED' AND resource_id=?",String.class,created.preferenceId()));
        assertEquals("REVIEW",metadata.get("decision").asString());assertEquals(2,metadata.get("version").asLong());
        code(409,"PREFERENCE_REVIEWED",()->preferences.withdraw(as(6),key(),withdraw(created.preferenceId(),1)));
        code(409,"STATE_CONFLICT",()->preferences.review(as(2),key(),created.preferenceId(),new ReviewPreference(2L,"SCHOOL_REVIEWED")));
        var next=preferences.submit(as(6),key(),submit(opportunity,null,1));
        assertEquals(2,next.intentVersion());assertEquals("SUBMITTED",next.status());
        assertEquals("REVIEW",jdbc.queryForObject("SELECT operation FROM student_preference_history WHERE preference_id=? AND version=2",String.class,created.preferenceId()));
    }

    @Test void closedUnpublishedForeignOrUnlinkedSubmitsHaveNoEffect() {
        String closed=publish(Instant.now().minusSeconds(60)),open=publish(open());
        code(409,"OPPORTUNITY_CLOSED",()->preferences.submit(as(6),key(),submit(closed,null,0)));
        code(404,"RESOURCE_NOT_FOUND",()->preferences.submit(as(6),key(),submit(id(86),null,0)));
        code(404,"RESOURCE_NOT_FOUND",()->preferences.submit(as(4),key(),submit(open,null,0)));
        code(404,"RESOURCE_NOT_FOUND",()->preferences.submit(as(7),key(),submit(open,null,0)));
        code(400,"SCHEMA_VALIDATION",()->preferences.submit(as(6),key(),submit(open,"x".repeat(1001),0)));
        assertEquals(0,count("SELECT COUNT(*) FROM student_preferences WHERE opportunity_id IN (?,?,?)",closed,id(86),open));
    }

    @Test void auditFailureRollsBackThePreference() {
        String opportunity=publish(open());
        String prior=jdbc.queryForList("SELECT DISTINCT resource_id FROM audit_logs WHERE action='PREFERENCE_SUBMITTED'",String.class).stream()
            .map(value->"'"+UUID.fromString(value)+"'").reduce((a,b)->a+","+b).orElse("''");
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT t2_preference_audit_failure CHECK(action<>'PREFERENCE_SUBMITTED' OR resource_id IN ("+prior+"))");
        try {code(500,"AUDIT_WRITE_FAILED",()->preferences.submit(as(6),key(),submit(opportunity,null,0)));}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK t2_preference_audit_failure");}
        assertEquals(0,count("SELECT COUNT(*) FROM student_preferences WHERE opportunity_id=?",opportunity));
    }

    @Test void withoutTheT5AdapterTheApplicationFallbackAnswers503() {
        code(503,"DEPENDENCY_UNAVAILABLE",()->new UnavailableStudentOpportunity().publishedForStudent(null,id(81)));
    }

    @Test void httpRoutesRequireSessionAndCsrfAndAnswerContractStatuses() throws Exception {
        String opportunity=publish(open());
        assertEquals(401,new Browser().send("GET","/api/v1/students/me/preferences",null,null).statusCode());
        var student=new Browser();student.csrf();
        var login=student.send("POST","/api/v1/auth/login",JSON.writeValueAsString(Map.of("email","student@example.test","password",PASSWORD)),null);
        assertEquals(200,login.statusCode(),login.body());
        String submit="{\"action\":\"SUBMIT\",\"opportunityId\":\""+opportunity+"\",\"message\":null,\"expectedIntentVersion\":0}";
        student.csrf=null;
        assertEquals(403,student.send("POST","/api/v1/students/me/preferences",submit,key()).statusCode());
        student.csrf();
        var created=student.send("POST","/api/v1/students/me/preferences",submit,key());
        assertEquals(201,created.statusCode(),created.body());
        String id=JSON.readTree(created.body()).get("preferenceId").asString();
        for(String invalid:List.of("{\"action\":\"SUBMIT\",\"opportunityId\":\""+opportunity+"\",\"expectedIntentVersion\":0}",
                "{\"action\":\"SUBMIT\",\"opportunityId\":\""+opportunity+"\",\"message\":5,\"expectedIntentVersion\":0}",
                "{\"action\":\"WITHDRAW\",\"preferenceId\":\""+id+"\",\"expectedVersion\":1,\"reasonCode\":\"STUDENT_WITHDREW\",\"message\":null}",
                "{\"action\":\"submit\",\"opportunityId\":\""+opportunity+"\",\"message\":null,\"expectedIntentVersion\":0}")) {
            var response=student.send("POST","/api/v1/students/me/preferences",invalid,key());
            assertEquals(400,response.statusCode(),invalid);assertEquals("SCHEMA_VALIDATION",JSON.readTree(response.body()).get("errorCode").asString());
        }
        var conflict=student.send("POST","/api/v1/students/me/preferences","{\"action\":\"SUBMIT\",\"opportunityId\":\""+opportunity+"\",\"message\":null,\"expectedIntentVersion\":1}",key());
        assertEquals(409,conflict.statusCode());assertEquals("STATE_CONFLICT",JSON.readTree(conflict.body()).get("errorCode").asString());
        var withdrawn=student.send("POST","/api/v1/students/me/preferences","{\"action\":\"WITHDRAW\",\"preferenceId\":\""+id+"\",\"expectedVersion\":1,\"reasonCode\":\"STUDENT_WITHDREW\"}",key());
        assertEquals(200,withdrawn.statusCode(),withdrawn.body());assertEquals("WITHDRAWN",JSON.readTree(withdrawn.body()).get("status").asString());
        var list=student.send("GET","/api/v1/students/me/preferences?page=0&size=100",null,null);
        assertEquals(200,list.statusCode());assertTrue(list.body().contains(id));
        assertEquals(404,student.send("POST","/api/v1/school/preferences/"+id+"/review","{\"expectedVersion\":2,\"reasonCode\":\"SCHOOL_REVIEWED\"}",key()).statusCode());
    }

    @Test void browserShowsAndWithdrawsARealPreference() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("frontend.e2e"),"Browser flow runs with -Dfrontend.e2e=true");
        String opportunity=publish(open());
        preferences.submit(as(6),key(),submit(opportunity,"W3 browser preference",0));
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- student-preference-runtime.spec.ts")
            :new ProcessBuilder("npm","run","test:e2e","--","student-preference-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);
        builder.environment().put("E2E_T2_PREFERENCE","true");builder.environment().put("E2E_PASSWORD",PASSWORD);
        builder.environment().put("E2E_REPORT_FILE","../target/t2-preference-browser-results.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/t2-preference-browser.log"));var process=builder.start();
        if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)) {process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();fail("Browser timeout; see target/t2-preference-browser.log");}
        assertEquals(0,process.exitValue(),"See target/t2-preference-browser.log");
    }

    final class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String csrf;
        HttpResponse<String> send(String method,String path,String body,String key) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
            if(csrf!=null) request.header("X-CSRF-TOKEN",csrf);
            if(key!=null) request.header("Idempotency-Key",key);
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {
            var response=send("GET","/api/v1/auth/csrf",null,null);assertEquals(200,response.statusCode());
            csrf=JSON.readTree(response.body()).get("token").asString();
        }
    }
}
