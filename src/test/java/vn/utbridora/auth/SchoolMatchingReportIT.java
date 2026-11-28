package vn.utbridora.auth;

import java.time.*;
import java.util.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.modules.matching.application.*;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL, delegation ancestry, SQL pagination and export audit; never the developer database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.admin.email=","app.bootstrap.admin.password=","server.servlet.session.cookie.secure=false"})
class SchoolMatchingReportIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired MembershipRepository memberships;
    @Autowired TermRepository terms;
    @Autowired SchoolMatchingReportService reports;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @LocalServerPort int port;

    @Test void reportsUseLatestSchoolSnapshotAndFilterBeforeTotalsPagingAndExportWithCurrentAuthority() throws Exception {
        String school=organization(Organization.Type.UNIVERSITY), otherSchool=organization(Organization.Type.UNIVERSITY), enterprise=organization(Organization.Type.ENTERPRISE);
        UserAccount staff=user(), admin=user(), student=user();
        membership(staff,school,Role.SCHOOL_STAFF);membership(admin,null,Role.ADMIN);membership(student,enterprise,Role.ENTERPRISE);
        String term=term(school), hiddenTerm=term(school), foreignTerm=term(otherSchool), grant=id();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES(?,?,?,?,?,'TEST-REPORT',JSON_ARRAY(),UTC_TIMESTAMP(6)-INTERVAL 1 DAY,UTC_TIMESTAMP(6)+INTERVAL 1 DAY,true,1)",grant,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions VALUES(?,'matching:school:detail_read')",grant);
        String first=demand(school,enterprise,term,"Java, \"API\""), second=demand(school,enterprise,term,"React");
        run(school,enterprise,term,first,Instant.parse("2026-11-28T00:00:00Z"),"SCHOOL",student.id,"MATCH");
        String latest=run(school,enterprise,term,first,Instant.parse("2026-11-28T00:00:00.100Z"),"SCHOOL",student.id,"MISSING_DATA");
        run(school,enterprise,term,first,Instant.parse("2026-11-29T00:00:00Z"),"ENTERPRISE",student.id,"MATCH");
        run(school,enterprise,term,second,Instant.parse("2026-11-27T00:00:00Z"),"SCHOOL",student.id,"MATCH");
        run(school,enterprise,hiddenTerm,demand(school,enterprise,hiddenTerm,"HIDDEN-TERM"),Instant.now(),"SCHOOL",student.id,"MATCH");
        run(otherSchool,enterprise,foreignTerm,demand(otherSchool,enterprise,foreignTerm,"FOREIGN-SCHOOL"),Instant.now(),"SCHOOL",student.id,"MATCH");
        Authentication auth=auth(staff);
        var page=reports.report(auth,school,null,null,0,1);
        assertEquals(2,page.totalElements());assertEquals(2,page.totalPages());assertEquals(2,page.totals().compared());
        assertEquals(1,page.totals().matched());assertEquals(1,page.totals().missingData());assertEquals(0,page.totals().notMatched());
        assertEquals(latest,page.content().getFirst().matchRunId());assertEquals("Java, \"API\"",page.content().getFirst().title());
        assertEquals(second,reports.report(auth,school,term,null,1,1).content().getFirst().demandId());
        assertEquals(1,reports.report(auth,school,term,"React",0,10).totalElements());
        assertEquals(0,reports.report(auth,school,term,"%",0,10).totalElements());
        assertEquals(400,assertThrows(OperationFailure.class,()->reports.report(auth,school,term,null,-1,10)).status);
        assertEquals(404,assertThrows(OperationFailure.class,()->reports.report(auth,school,hiddenTerm,null,0,10)).status);
        assertEquals(404,assertThrows(OperationFailure.class,()->reports.report(auth,otherSchool,null,null,0,10)).status);
        assertEquals(404,assertThrows(OperationFailure.class,()->reports.report(auth(student),school,null,null,0,10)).status);
        // A new draft's title is never shown to school through this report.
        jdbc.update("INSERT INTO demand_revisions(demand_id,version,criteria_version,criteria_hash,data_json,snapshot_json,actor_id,operation,created_at) VALUES(?,1,1,?,?,JSON_OBJECT(),?,'CREATE',UTC_TIMESTAMP(6))",first,"a".repeat(64),JSON.writeValueAsString(Map.of("title","Java, \"API\"")),staff.id);
        jdbc.update("UPDATE demands SET data_json=JSON_OBJECT('title','PRIVATE-NEW-DRAFT'),version=2,criteria_version=2,criteria_hash=? WHERE id=?","b".repeat(64),first);
        var stale=reports.report(auth,school,term,"Java",0,10).content().getFirst();
        assertFalse(stale.criteriaCurrent());assertEquals("Java, \"API\"",stale.title());
        String csv=reports.export(auth,school,term,"Java");
        assertTrue(csv.startsWith("\uFEFF"));assertTrue(csv.contains("Java, \"\"API\"\""));
        for(String privateText:List.of(student.id,student.email,"PRIVATE-NEW-DRAFT","HIDDEN-TERM","FOREIGN-SCHOOL")) assertFalse(csv.contains(privateText));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='MATCH_EXPORT_ATTEMPTED' AND actor_id=? AND organization_id=?",Integer.class,staff.id,school));
        assertFalse(JSON.writeValueAsString(page).contains(student.id));
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String base="http://localhost:"+port;
        String csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asString();
        var login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/auth/login")).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf)
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",staff.email,"password","Report-test-password-123")))).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,login.statusCode(),login.body());
        String path=base+"/api/v1/school/matching-reports";
        var httpReport=client.send(HttpRequest.newBuilder(URI.create(path+"?termId="+term)).header("X-Organization-Id",school).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,httpReport.statusCode(),httpReport.body());assertEquals(2,JSON.readTree(httpReport.body()).get("totalElements").asInt());
        var file=client.send(HttpRequest.newBuilder(URI.create(path+"/export.csv?termId="+term+"&q=React")).header("X-Organization-Id",school).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,file.statusCode(),file.body());assertTrue(file.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        assertTrue(file.body().contains("React"));assertFalse(file.body().contains("Java"));
        assertEquals(401,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(path)).header("X-Organization-Id",school).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        String existingExports=jdbc.queryForList("SELECT id FROM audit_logs WHERE action='MATCH_EXPORT_ATTEMPTED'",String.class).stream()
            .map(value->"'"+vn.utbridora.auth.operations.WorkflowValues.uuid(value)+"'").collect(java.util.stream.Collectors.joining(","));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT school_report_export_failure CHECK(action<>'MATCH_EXPORT_ATTEMPTED' OR id IN ("+existingExports+"))");
        try {assertThrows(RuntimeException.class,()->reports.export(auth,school,term,null));}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK school_report_export_failure");}
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='MATCH_EXPORT_ATTEMPTED' AND actor_id=?",Integer.class,staff.id));
        jdbc.update("UPDATE school_delegations SET active=false WHERE id=?",grant);
        assertEquals(404,assertThrows(OperationFailure.class,()->reports.export(auth,school,term,null)).status);
        assertEquals(404,client.send(HttpRequest.newBuilder(URI.create(path+"/export.csv")).header("X-Organization-Id",school).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    private UserAccount user() {
        var u=new UserAccount();u.email=id()+"@example.test";u.displayName="Report test";u.passwordHash=encoder.encode("Report-test-password-123");u.status=AccountStatus.ACTIVE;
        return users.saveAndFlush(u);
    }
    private void membership(UserAccount u,String org,Role role) {var m=new Membership();m.userId=u.id;m.organizationId=org;m.role=role;memberships.saveAndFlush(m);}
    private Authentication auth(UserAccount u) {return UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(u.id,u.email,null,u.securityVersion,List.of()),null,List.of());}
    private String organization(Organization.Type type) {var o=new Organization();o.name="Report organization";o.type=type;o.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(o).id;}
    private String term(String school) {var t=new Term();t.schoolOrgId=school;t.termCode=id();t.startDate=LocalDate.parse("2026-01-01");t.endDate=LocalDate.parse("2030-01-01");t.status=TermStatus.ACTIVE;return terms.saveAndFlush(t).id;}
    private String demand(String school,String enterprise,String term,String title) {
        String id=id();jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES(?,?,?,?,'READY','VALID',1,1,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id,enterprise,school,term,"a".repeat(64),JSON.writeValueAsString(Map.of("title",title)));return id;
    }
    private String run(String school,String enterprise,String term,String demand,Instant at,String origin,String student,String status) {
        String id=id(), dataset=id();jdbc.update("INSERT INTO student_snapshot_sets VALUES(?,?,?,JSON_OBJECT())",dataset,school,term);
        var ref=new MatchingService.MatchRunRef(id,demand,1,1,"a".repeat(64),dataset,"v1","r1","skills-v1","s1","COMPLETED",at,at,null);
        jdbc.update("INSERT INTO match_runs VALUES(?,?,?,?,?,?,?)",id,demand,school,enterprise,term,dataset,JSON.writeValueAsString(new MatchingService.StoredRun(ref,school,enterprise,term)));
        jdbc.update("INSERT INTO matching_run_origins VALUES(?,?)",id,origin);
        jdbc.update("INSERT INTO match_results VALUES(?,?,?,?,JSON_OBJECT())",id,student,status,status.equals("MATCH")?50:null);return id;
    }
    private static String id() {return UUID.randomUUID().toString();}
}
