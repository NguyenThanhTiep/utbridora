package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;

/** Real sessions, production filter chain, named TAX grants, Flyway and MySQL transactions. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.bootstrap.admin.email=","app.bootstrap.admin.password="})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TaxonomyIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString());
    static { MYSQL.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired OrganizationRepository organizations;
    @Autowired PasswordEncoder encoder;
    String password="Test-"+UUID.randomUUID(),hash;
    UserAccount editor,admin,student;
    @BeforeAll void hash() {hash=encoder.encode(password);}
    @BeforeEach void seed() {
        jdbc.update("UPDATE skills SET parent_skill_id=NULL");jdbc.update("UPDATE skill_categories SET parent_category_id=NULL");
        for(String table:List.of("operation_records","skill_implies","skill_aliases","skills","skill_categories","taxonomy_editor_assignments","SPRING_SESSION_ATTRIBUTES","SPRING_SESSION","audit_logs","memberships","users","organizations")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE taxonomy_catalog_state SET taxonomy_version=1");
        editor=user(Role.ADMIN);admin=user(Role.ADMIN);student=user(Role.STUDENT);
        grant(editor);
    }
    UserAccount user(Role role) {
        UserAccount user=new UserAccount();user.email=UUID.randomUUID()+"@example.test";user.displayName="Taxonomy Test";user.passwordHash=hash;user.status=AccountStatus.ACTIVE;user=users.saveAndFlush(user);
        Membership m=new Membership();m.userId=user.id;m.role=role;
        if(role!=Role.ADMIN) {
            Organization school=new Organization();school.name="Test School";school.type=Organization.Type.UNIVERSITY;school.status=Organization.Status.VERIFIED;
            m.organizationId=organizations.saveAndFlush(school).id;
        }
        memberships.saveAndFlush(m);return user;
    }
    void grant(UserAccount user) {
        jdbc.update("INSERT INTO taxonomy_editor_assignments(id,user_id,decision_ref,approved_by_t1,approved_by_t4,valid_from,valid_until) VALUES (?,?,?,?,?,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY))",UUID.randomUUID().toString(),user.id,"ISOLATED_TEST_MANIFEST",user.id,user.id);
    }
    class Browser {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf;
        HttpResponse<String> send(String method,String path,String body,String key,boolean csrfEnabled) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
            if(key!=null)r.header("Idempotency-Key",key);if(csrfEnabled && csrf!=null)r.header("X-CSRF-TOKEN",csrf);
            return client.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {csrf=JsonPath.read(send("GET","/api/v1/auth/csrf",null,null,false).body(),"$.token");}
        HttpResponse<String> write(String method,String path,String body,String key) throws Exception {return send(method,"/api/v1/taxonomy"+path,body,key,true);}
    }
    Browser login(UserAccount user) throws Exception {
        Browser b=new Browser();b.csrf();assertEquals(200,b.send("POST","/api/v1/auth/login","{\"email\":\""+user.email+"\",\"password\":\""+password+"\"}",null,true).statusCode());b.csrf();return b;
    }
    static final String CATEGORY="{\"categoryCode\":\"PROGRAMMING\",\"name\":\"Programming\",\"parentCategoryCode\":null}";
    static void code(HttpResponse<String> r,int status,String code) {assertEquals(status,r.statusCode(),r.body());assertEquals(code,JsonPath.read(r.body(),"$.errorCode"));}
    @Test void authenticatedStudentCanReadButCannotBecomeATaxEditor() throws Exception {
        Browser b=login(student);
        assertEquals(200,b.send("GET","/api/v1/taxonomy/skills",null,null,false).statusCode());
        grant(student);
        code(b.write("POST","/categories",CATEGORY,UUID.randomUUID().toString()),403,"ACCESS_DENIED");
        jdbc.update("UPDATE taxonomy_editor_assignments SET valid_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 HOUR) WHERE user_id=?",editor.id);
        code(login(editor).write("POST","/categories",CATEGORY,UUID.randomUUID().toString()),403,"ACCESS_DENIED");
    }
    @Test void adminWithoutNamedTaxCannotWriteAndRevocationPreventsReplay() throws Exception {
        code(login(admin).write("POST","/categories",CATEGORY,UUID.randomUUID().toString()),403,"ACCESS_DENIED");
        Browser b=login(editor);String key=UUID.randomUUID().toString();assertEquals(201,b.write("POST","/categories",CATEGORY,key).statusCode());
        jdbc.update("UPDATE taxonomy_editor_assignments SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=?",editor.id);
        code(b.write("POST","/categories",CATEGORY,key),403,"ACCESS_DENIED");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM skill_categories",Integer.class));
    }
    @Test void replayPrecedesVersionCheckAndChangedPayloadIsRejected() throws Exception {
        Browser b=login(editor);String key=UUID.randomUUID().toString();var first=b.write("POST","/categories",CATEGORY,key);
        assertEquals(201,first.statusCode(),first.body());assertEquals(first.body(),b.write("POST","/categories",CATEGORY,key).body());
        code(b.write("POST","/categories",CATEGORY.replace("Programming","Programming changed"),key),409,"IDEMPOTENCY_KEY_REUSED");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='TAXONOMY_CHANGED'",Integer.class));
        var metadata=jdbc.queryForMap("SELECT metadata_json FROM audit_logs WHERE action='TAXONOMY_CHANGED'");assertEquals("CATEGORY",JsonPath.read(metadata.get("metadata_json").toString(),"$.entityType"));
        String update="{\"expectedVersion\":1,\"name\":\"Programming II\",\"status\":\"ACTIVE\"}",updateKey=UUID.randomUUID().toString();
        var saved=b.write("PATCH","/categories/PROGRAMMING",update,updateKey);assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(saved.body(),b.write("PATCH","/categories/PROGRAMMING",update,updateKey).body());
        code(b.write("PATCH","/categories/PROGRAMMING",update,UUID.randomUUID().toString()),409,"TAXONOMY_CONFLICT");
        var page=b.send("GET","/api/v1/taxonomy/categories?page=0&size=25",null,null,false);assertEquals(200,page.statusCode());
        assertEquals(Set.of("categoryCode","name","parentCategoryCode","status","version"),((Map<?,?>)JsonPath.read(page.body(),"$.content[0]")).keySet());
    }
    @Test void auditFailureRollsBackDomainVersionAndOperationRecord() throws Exception {
        Browser b=login(editor);String key=UUID.randomUUID().toString();jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_tax_audit_failure CHECK (action <> 'TAXONOMY_CHANGED')");
        try {code(b.write("POST","/categories",CATEGORY,key),500,"AUDIT_WRITE_FAILED");}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK test_tax_audit_failure");}
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM skill_categories",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records",Integer.class));
        assertEquals(1,jdbc.queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state",Integer.class));
        assertEquals(201,b.write("POST","/categories",CATEGORY,key).statusCode());
    }
    @Test void aliasResolutionIsExactAndProductionRoutesEnforceCsrf() throws Exception {
        Browser b=login(editor);assertEquals(403,b.send("POST","/api/v1/taxonomy/categories",CATEGORY,UUID.randomUUID().toString(),false).statusCode());
        assertEquals(201,b.write("POST","/categories",CATEGORY,UUID.randomUUID().toString()).statusCode());
        String skill="{\"skillCode\":\"JAVA\",\"name\":\"Java\",\"categoryCode\":\"PROGRAMMING\",\"parentSkillCode\":null}";
        assertEquals(201,b.write("POST","/skills",skill,UUID.randomUUID().toString()).statusCode());
        assertEquals(201,b.write("POST","/skills/JAVA/aliases","{\"alias\":\"Java Core\",\"expectedVersion\":1}",UUID.randomUUID().toString()).statusCode());
        var result=b.write("POST","/skills/resolve","{\"values\":[\" JAVA   Core \",\"Spring Boot\"]}",null);
        assertEquals(200,result.statusCode(),result.body());assertEquals(List.of("RESOLVED","UNKNOWN"),JsonPath.read(result.body(),"$.items[*].status"));
        assertFalse(result.body().contains("skillId"));
    }
}
