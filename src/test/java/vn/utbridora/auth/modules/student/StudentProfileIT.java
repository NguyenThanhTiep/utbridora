package vn.utbridora.auth.modules.student;

import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.utbridora.auth.modules.student.application.StudentTermScopePort;
import vn.utbridora.auth.modules.student.application.UnavailableStudentTermScope;
import vn.utbridora.auth.modules.student.application.LinkedStudentTermScope;
import vn.utbridora.auth.modules.student.application.ProfileReferenceValidator;
import vn.utbridora.auth.modules.student.application.ProfileReferenceConfiguration;
import vn.utbridora.auth.modules.student.application.StudentProfileException;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;

/** BC11 own profile over real HTTP sessions, CSRF, MySQL 8.4 and Flyway (guide section 11.4 acceptance slice). */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.allow-org-verification=false"})
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentProfileIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("student_test").withUsername("student_test").withPassword(UUID.randomUUID().toString());
    static final java.nio.file.Path FILES;
    static {
        try {FILES=java.nio.file.Files.createTempDirectory("utbridora-profile-files-it-");} catch(java.io.IOException e) {throw new ExceptionInInitializerError(e);}
        MYSQL.start();
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.files.storage-root", FILES::toString);
    }
    @AfterAll static void deleteFiles() throws java.io.IOException {
        // Only direct children of this JVM-created test directory; never a shared storage root.
        try(var paths=java.nio.file.Files.list(FILES)) {for(var path:paths.toList()) java.nio.file.Files.delete(path);}
        java.nio.file.Files.delete(FILES);
    }
    static final String TERM="44444444-4444-4444-8444-444444444444";
    static final String PROFILE="/api/v1/students/me/profile";
    static final List<String> CHECKS=List.of("MAJOR_VALID","STUDENT_TERM_PRESENT","SKILLS_DECLARED","SKILLS_VALID","AVAILABILITY_DECLARED","AVAILABILITY_VALID");
    /** Positive paths use real T5/T2 providers. Only the availability-failure switch is a test wrapper. */
    @TestConfiguration static class LinkedTerms {
        static volatile boolean available=true;
        @Bean @Primary StudentTermScopePort fixtureStudentTerms(LinkedStudentTermScope authoritative) {
            return (student,term)->available ? authoritative.schoolOfLinkedTerm(student,term)
                : new UnavailableStudentTermScope().schoolOfLinkedTerm(student,term);
        }
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired MembershipRepository memberships;
    @Autowired PasswordEncoder encoder;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired vn.utbridora.auth.modules.student.application.AcademicCatalogService catalogs;
    @Autowired vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort taxonomy;
    final String password="Test-"+UUID.randomUUID();
    String passwordHash;
    Organization university;
    UserAccount student;

    @BeforeAll void hash() { passwordHash=encoder.encode(password); }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="frontend.e2e",matches="true")
    void realStudentProfileBrowserFlow() throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- student-runtime.spec.ts")
            :new ProcessBuilder("npm","run","test:e2e","--","student-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));
        builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);
        builder.environment().put("E2E_REPORT_FILE","../target/student-browser-results.xml");
        builder.environment().put("E2E_PROFILE_STUDENT_EMAIL",student.email);
        builder.environment().put("E2E_PASSWORD",password);
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/student-browser.log"));
        Process process=builder.start();
        if(!process.waitFor(120,java.util.concurrent.TimeUnit.SECONDS)) {process.destroyForcibly();fail("See target/student-browser.log");}
        assertEquals(0,process.exitValue(),"See target/student-browser.log");
        assertEquals(2,jdbc.queryForObject("SELECT profile_version FROM student_profiles WHERE user_id=?",Integer.class,student.id));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE state='DONE'",Integer.class));
    }
    @BeforeEach void seed() {
        for(String table:List.of("academic_catalog_entries","academic_catalogs","operation_records","student_term_eligibilities","student_rosters","student_project_files","student_project_skills","student_projects","student_certificates",
                "student_custom_skills","student_profile_skills","student_profile_term_codes","student_profile_term_periods",
                "student_profile_terms","student_profiles","SPRING_SESSION_ATTRIBUTES","SPRING_SESSION","audit_logs","account_status_history",
                "one_time_tokens","file_bindings","files","memberships","terms","users","organizations")) jdbc.update("DELETE FROM "+table);
        LinkedTerms.available=true;
        university=university(Organization.Status.VERIFIED);
        student=student(university.id);
        jdbc.update("INSERT IGNORE INTO skill_categories(id,category_code,name,normalized_name,status,version,created_at,updated_at) VALUES ('11111111-1111-4111-8111-111111111111','DESIGN','Design','design','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
        for(String code:List.of("JAVA","PYTHON")) jdbc.update("INSERT IGNORE INTO skills(id,skill_code,name,normalized_name,category_id,status,version,created_at,updated_at) VALUES (?,?,?,?,'11111111-1111-4111-8111-111111111111','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),code,code,code.toLowerCase(Locale.ROOT));
    }
    Organization university(Organization.Status status) {
        Organization org=new Organization();org.name="Trường thử nghiệm "+UUID.randomUUID();
        org.type=Organization.Type.UNIVERSITY;org.status=status;return organizations.saveAndFlush(org);
    }
    UserAccount student(String schoolId) {
        UserAccount user=new UserAccount();user.email=UUID.randomUUID()+"@example.test";
        user.displayName="Sinh viên kiểm thử";user.passwordHash=passwordHash;user.status=AccountStatus.ACTIVE;
        users.saveAndFlush(user);Membership m=new Membership();m.userId=user.id;m.role=Role.STUDENT;m.organizationId=schoolId;
        memberships.saveAndFlush(m);linkTerm(user.id,TERM,schoolId);return user;
    }
    void linkTerm(String user,String term,String school) {
        var existing=jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=?",String.class,term);
        if(!existing.isEmpty() && !existing.get(0).equals(school)) return;
        String code="TEST-"+term.substring(0,8);
        jdbc.update("INSERT IGNORE INTO terms (id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2026-12-01','2027-08-31','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,code);
        jdbc.update("INSERT IGNORE INTO academic_catalogs(term_id,school_org_id,catalog_version) VALUES (?,?,'school-v1')",term,school);
        for(String entryCode:List.of("SE","AI","BACKEND","OOP")) jdbc.update("INSERT IGNORE INTO academic_catalog_entries(term_id,kind,code,name,major_codes,status) VALUES (?,?,?,?,JSON_ARRAY(),'ACTIVE')",term,entryCode.equals("BACKEND")?"ORIENTATION":entryCode.equals("OOP")?"SUBJECT":"MAJOR",entryCode,entryCode);
        var rosters=jdbc.queryForList("SELECT id FROM student_rosters WHERE user_id=? AND school_org_id=?",String.class,user,school);
        String roster=rosters.isEmpty()?UUID.randomUUID().toString():rosters.get(0);
        if(rosters.isEmpty()) jdbc.update("INSERT INTO student_rosters (id,school_org_id,student_code,user_id,full_name,email,major_code,term_code,created_at) VALUES (?,?,?,?,? ,?,'SE',?,UTC_TIMESTAMP(6))",
                roster,school,"00"+user.replace("-","").substring(0,24),user,"Synthetic student",user+"@example.test",code);
        jdbc.update("INSERT IGNORE INTO student_term_eligibilities (id,student_roster_id,term_code,user_id,school_org_id,term_id,school_approved,created_at) VALUES (?,?,?,?,?,?,b'0',UTC_TIMESTAMP(6))",
                UUID.randomUUID().toString(),roster,code,user,school,term);
    }
    static Map<String,Object> obj(Object... pairs) {
        Map<String,Object> map=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) map.put((String)pairs[i],pairs[i+1]);
        return map;
    }
    static Map<String,Object> input(long version) {
        return obj("expectedProfileVersion",version,"termId",TERM,"majorCode","SE","skillsDeclared",true,
            "skills",List.of(obj("skillCode","JAVA","level",3,"yearsPractice",1.5,"note",null)),"customSkills",List.of(),
            "availabilityDeclared",true,"availablePeriods",List.of(obj("startDate","2026-12-01","endDate","2027-02-28")),
            "orientationCodes",List.of("BACKEND"),"subjectCodes",List.of("OOP"),"projects",List.of(),"certificates",List.of(),
            "cvFileId",null,"phone","0901234567");
    }
    static Map<String,Object> project(String id) {
        return obj("projectId",id,"title","Ứng dụng thử nghiệm","description","Đồ án mẫu","role","Backend",
            "skillCodes",List.of("JAVA"),"url","https://example.test/project","evidenceFileIds",List.of());
    }
    static <T> T read(HttpResponse<String> response,String path) { return JsonPath.read(response.body(),path); }

    class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(10)).build();
        String csrf;
        HttpResponse<String> send(String method,String path,String json,boolean withCsrf,String idempotencyKey) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json");
            if(withCsrf && csrf!=null) request.header("X-CSRF-TOKEN",csrf);
            if(idempotencyKey!=null) request.header("Idempotency-Key",idempotencyKey);
            return client.send(request.method(method,json==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception { var res=send("GET","/api/v1/auth/csrf",null,false,null);assertEquals(200,res.statusCode(),res.body());csrf=read(res,"$.token"); }
        HttpResponse<String> get(String termId) throws Exception { return send("GET",PROFILE+"?termId="+termId,null,false,null); }
        HttpResponse<String> put(Map<String,Object> body) throws Exception {
            return send("PUT",PROFILE,new tools.jackson.databind.ObjectMapper().writeValueAsString(body),true,UUID.randomUUID().toString());
        }
        /** Real T1 multipart upload bound to this student's STUDENT_PROFILE; the server keeps it PENDING. */
        String upload(UserAccount owner,String purpose) throws Exception {
            String boundary="T209-profile-boundary";
            var body=new java.io.ByteArrayOutputStream();
            for(var field:List.of(List.of("resourceType","STUDENT_PROFILE"),List.of("resourceId",owner.id),List.of("purpose",purpose)))
                body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.get(0)+"\"\r\n\r\n"+field.get(1)+"\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"profile.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            body.write(PDF);body.write(("\r\n--"+boundary+"--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/files")).timeout(Duration.ofSeconds(30))
                .header("Content-Type","multipart/form-data; boundary="+boundary).header("Idempotency-Key",UUID.randomUUID().toString()).header("X-CSRF-TOKEN",csrf);
            var res=client.send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(202,res.statusCode(),res.body());assertEquals("PENDING",read(res,"$.scanStatus"));
            return read(res,"$.fileId");
        }
        int download(String fileId) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/files/"+fileId+"/content")).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
    static final byte[] PDF="%PDF-1.7\nSynthetic T2-09 profile fixture, not a scanned document.\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    /** Scanner-output fixture (T1-07 is not on this base): marks the test's own upload CLEAN, never production data. */
    void syntheticClean(String fileId) { jdbc.update("UPDATE files SET scan_status='CLEAN' WHERE id=?",fileId); }
    Browser signedIn(UserAccount user) throws Exception {
        Browser b=new Browser();b.csrf();
        var res=b.send("POST","/api/v1/auth/login",new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("email",user.email,"password",password)),true,null);
        assertEquals(200,res.statusCode(),res.body());b.csrf();return b;
    }
    static void assertEnvelope(HttpResponse<String> res,int status,String code) {
        assertEquals(status,res.statusCode(),res.body());
        assertEquals(code,read(res,"$.errorCode"));assertEquals(code,read(res,"$.error"));
        assertTrue(((String)read(res,"$.requestId")).matches("[0-9a-f-]{36}"),res.body());
        assertNull(read(res,"$.retryAfterSeconds"));
    }
    static List<String> fields(HttpResponse<String> res) { return read(res,"$.fieldErrors[*].field"); }

    @Test void repeatedKeyReplaysOriginalVersionAndChangedPayloadConflicts() throws Exception {
        Browser b=signedIn(student);String key=UUID.randomUUID().toString();
        String body=new tools.jackson.databind.ObjectMapper().writeValueAsString(input(0));
        var first=b.send("PUT",PROFILE,body,true,key);assertEquals(200,first.statusCode(),first.body());
        var replay=b.send("PUT",PROFILE,body,true,key);assertEquals(200,replay.statusCode(),replay.body());
        assertEquals(first.body(),replay.body());
        var changed=input(1);changed.put("phone","0907654321");
        assertEnvelope(b.send("PUT",PROFILE,new tools.jackson.databind.ObjectMapper().writeValueAsString(changed),true,key),409,"IDEMPOTENCY_KEY_REUSED");
        assertEquals(1,jdbc.queryForObject("SELECT profile_version FROM student_profiles WHERE user_id=?",Integer.class,student.id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='STUDENT_PROFILE_UPDATED'",Integer.class));
    }
    @Test void academicCatalogIsVersionedScopedAndUsesTheSameCodesAsProfileWrites() throws Exception {
        Browser b=signedIn(student);
        String path="/api/v1/students/me/catalogs?termId="+TERM+"&kind=MAJOR&size=1";
        var first=b.send("GET",path,null,false,null);
        assertEquals(200,first.statusCode(),first.body());
        assertEquals("school-v1",read(first,"$.catalogVersion"));
        assertEquals(2,(int)read(first,"$.entries.totalElements"));
        assertEquals(List.of("AI"),read(first,"$.entries.content[*].code"));
        assertEnvelope(b.send("GET",path+"&expectedCatalogVersion=old",null,false,null),409,"CATALOG_CHANGED");
        assertEnvelope(b.send("GET",path.replace(TERM,UUID.randomUUID().toString()),null,false,null),404,"RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE academic_catalog_entries SET status='INACTIVE' WHERE term_id=? AND code='SE'",TERM);
        jdbc.update("UPDATE academic_catalogs SET catalog_version='school-v2' WHERE term_id=?",TERM);
        assertEnvelope(b.put(input(0)),422,"CATALOG_CODE_INVALID");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records",Integer.class));
    }
    @Test void actualTaxonomyRejectsUnknownDeprecatedAndWrongCaseReferences() throws Exception {
        Browser b=signedIn(student);
        for(String code:List.of("UNKNOWN","java")) {
            var bad=input(0);bad.put("skills",List.of(obj("skillCode",code,"level",2,"yearsPractice",1,"note",null)));
            assertEnvelope(b.put(bad),422,"CATALOG_CODE_INVALID");
        }
        jdbc.update("UPDATE skills SET status='DEPRECATED' WHERE skill_code='JAVA'");
        try { assertEnvelope(b.put(input(0)),422,"CATALOG_CODE_INVALID"); }
        finally { jdbc.update("UPDATE skills SET status='ACTIVE' WHERE skill_code='JAVA'"); }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Integer.class));
    }

    @Test void referenceGuardsSeeCommittedChangesAfterTheRequestSnapshotStarted() {
        var request=new org.springframework.transaction.support.TransactionTemplate(transactions);
        request.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var publisher=new org.springframework.transaction.support.TransactionTemplate(transactions);
        publisher.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        long previous=jdbc.queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1",Long.class);
        var actor=new vn.utbridora.auth.security.AppPrincipal(student.id,student.email,null,student.securityVersion,List.of());
        try {
            request.executeWithoutResult(status->{
                // Establish an older consistent-read snapshot, as the Core/profile guard does.
                assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM skills WHERE skill_code='JAVA'",String.class));
                publisher.executeWithoutResult(update->{
                    jdbc.update("UPDATE academic_catalogs SET catalog_version='school-v2' WHERE term_id=?",TERM);
                    jdbc.update("UPDATE academic_catalog_entries SET status='INACTIVE' WHERE term_id=? AND code='SE'",TERM);
                    jdbc.update("UPDATE taxonomy_catalog_state SET taxonomy_version=taxonomy_version+1 WHERE id=1");
                    jdbc.update("UPDATE skills SET status='DEPRECATED' WHERE skill_code='JAVA'");
                });
                var academic=catalogs.forProfile(university.id,TERM);
                var skills=taxonomy.read(actor,Set.of("JAVA"),Set.of());
                assertAll(
                    ()->assertEquals("school-v2",academic.version()),
                    ()->assertEquals("INACTIVE",academic.entries().get(vn.utbridora.auth.modules.student.application.AcademicCatalogService.Kind.MAJOR)
                        .stream().filter(item->item.code().equals("SE")).findFirst().orElseThrow().status()),
                    ()->assertEquals("skills-v"+(previous+1),skills.taxonomyVersion()),
                    ()->assertFalse(skills.activeSkillCodes().contains("JAVA"),"A newly deprecated code must not pass through an older request snapshot")
                );
            });
        } finally {
            jdbc.update("UPDATE skills SET status='ACTIVE' WHERE skill_code='JAVA'");
            jdbc.update("UPDATE taxonomy_catalog_state SET taxonomy_version=? WHERE id=1",previous);
        }
    }

    @Test void termListIsScopedAndARealLinkedTermDoesNotApproveTheStudent() throws Exception {
        Browser b=signedIn(student);
        var page=b.send("GET","/api/v1/terms?schoolOrgId="+university.id,null,false,null);
        assertEquals(200,page.statusCode(),page.body());assertEquals(List.of(TERM),read(page,"$.content[*].termId"));
        var foreign=university(Organization.Status.VERIFIED);
        assertEnvelope(b.send("GET","/api/v1/terms?schoolOrgId="+foreign.id,null,false,null),404,"RESOURCE_NOT_FOUND");
        assertEquals(200,b.put(input(0)).statusCode());
        assertFalse(jdbc.queryForObject("SELECT school_approved FROM student_term_eligibilities WHERE user_id=? AND term_id=?",Boolean.class,student.id,TERM));
        jdbc.update("UPDATE terms SET status='CLOSED' WHERE id=?",TERM);
        assertEquals(200,b.get(TERM).statusCode());
    }

    @Test void replayRechecksMembershipAndNeverReturnsCachedDataToRevokedActor() throws Exception {
        Browser b=signedIn(student);String key=UUID.randomUUID().toString();
        String body=new tools.jackson.databind.ObjectMapper().writeValueAsString(input(0));
        assertEquals(200,b.send("PUT",PROFILE,body,true,key).statusCode());
        Membership membership=memberships.findByUserIdAndOrganizationIdAndRole(student.id,university.id,Role.STUDENT).orElseThrow();
        membership.active=false;memberships.saveAndFlush(membership);
        assertEnvelope(b.send("PUT",PROFILE,body,true,key),403,"ACCESS_DENIED");
    }

    @Test void auditFailureRollsBackProfileAndLedgerAndSameKeyCanBeRetried() throws Exception {
        Browser b=signedIn(student);String key=UUID.randomUUID().toString();
        String body=new tools.jackson.databind.ObjectMapper().writeValueAsString(input(0));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_profile_audit CHECK (action <> 'STUDENT_PROFILE_UPDATED')");
        try {
            assertEnvelope(b.send("PUT",PROFILE,body,true,key),500,"AUDIT_WRITE_FAILED");
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records",Integer.class));
        } finally { jdbc.execute("ALTER TABLE audit_logs DROP CHECK reject_profile_audit"); }
        assertEquals(200,b.send("PUT",PROFILE,body,true,key).statusCode());
    }

    @Test void firstSaveCreatesVersionOneAndReloadKeepsDataForOwnerOnly() throws Exception {
        Browser b=signedIn(student);
        assertEnvelope(b.get(TERM),404,"RESOURCE_NOT_FOUND");
        var created=b.put(input(0));
        assertEquals(200,created.statusCode(),created.body());
        assertEquals(1,(int)read(created,"$.profileVersion"));
        assertEquals(student.id,read(created,"$.studentUserId"));assertEquals(university.id,read(created,"$.schoolOrgId"));
        assertEquals("demo-v1",read(created,"$.completeness.policyVersion"));assertEquals(CHECKS,read(created,"$.completeness.checks[*].code"));
        assertTrue((Boolean)read(created,"$.completeness.complete"));
        assertEquals(List.of(true,true,true,true,true,true),read(created,"$.completeness.checks[*].passed"));
        var reloaded=b.get(TERM);
        assertEquals(200,reloaded.statusCode(),reloaded.body());
        assertEquals("0901234567",read(reloaded,"$.profile.phone"));assertEquals("SELF_DECLARED",read(reloaded,"$.profile.skills[0].source"));
        assertEquals("2027-02-28",read(reloaded,"$.profile.availablePeriods[0].endDate"));assertNull(read(reloaded,"$.profile.cvFileId"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='STUDENT_PROFILE_UPDATED' AND actor_id=? AND organization_id=?",
            Integer.class,student.id,university.id));
        assertEnvelope(signedIn(student(university.id)).get(TERM),404,"RESOURCE_NOT_FOUND");
    }
    /** Unknown codes are rejected; valid linkage is no longer replaced with a hard-coded failure. */
    @Test void unverifiedMajorTermAndSkillsNeverReportComplete() throws Exception {
        var unknown=input(0);unknown.put("majorCode","NO_SUCH_MAJOR");
        unknown.put("skills",List.of(obj("skillCode","NO_SUCH_SKILL","level",3,"yearsPractice",1,"note",null)));
        var res=signedIn(student).put(unknown);
        assertEnvelope(res,422,"CATALOG_CODE_INVALID");
        var empty=input(0);empty.put("majorCode",null);empty.put("skills",List.of());
        var none=signedIn(student(university.id)).put(empty);
        assertEquals(200,none.statusCode(),none.body());
        assertFalse((Boolean)read(none,"$.completeness.complete"));
        assertEquals(List.of("MAJOR_MISSING","OK","OK","OK","OK","OK"),read(none,"$.completeness.checks[*].reasonCode"));
    }
    /** Review P1-2: a term without an authoritative student link is rejected, and nothing is created while the source is down. */
    @Test void unverifiedTermIsRejectedAndCreatesNoProfile() throws Exception {
        Browser b=signedIn(student);
        var unlinked=input(0);unlinked.put("termId",UUID.randomUUID().toString());
        assertEnvelope(b.put(unlinked),404,"RESOURCE_NOT_FOUND");
        assertEnvelope(b.get(UUID.randomUUID().toString()),404,"RESOURCE_NOT_FOUND");
        LinkedTerms.available=false;
        assertEnvelope(b.put(input(0)),503,"DEPENDENCY_UNAVAILABLE");
        assertEnvelope(b.get(TERM),503,"DEPENDENCY_UNAVAILABLE");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Integer.class));
    }
    @Test void schoolComesFromTheLinkedTermNotTheFirstMembership() throws Exception {
        Organization second=university(Organization.Status.VERIFIED);
        Membership m=new Membership();m.userId=student.id;m.role=Role.STUDENT;m.organizationId=second.id;memberships.saveAndFlush(m);
        String secondTerm="55555555-5555-4555-8555-555555555555";
        linkTerm(student.id,secondTerm,second.id);
        var body=input(0);body.put("termId",secondTerm);
        var saved=signedIn(student).put(body);
        assertEquals(200,saved.statusCode(),saved.body());
        assertEquals(second.id,read(saved,"$.schoolOrgId"));
    }
    /** K.2 line 1847: one aggregate per student; major, availability and academic selections are kept per term. */
    @Test void sharedDataSpansTermsWhileTermDataStaysPerTerm() throws Exception {
        Browser b=signedIn(student);
        String nextTerm="66666666-6666-4666-8666-666666666666";
        linkTerm(student.id,nextTerm,university.id);
        var first=input(0);first.put("projects",List.of(project(null)));
        var created=b.put(first);
        assertEquals(200,created.statusCode(),created.body());
        String projectId=read(created,"$.profile.projects[0].projectId");
        var fresh=b.get(nextTerm);
        assertEquals(200,fresh.statusCode(),fresh.body());
        assertEquals(1,(int)read(fresh,"$.profileVersion"));
        assertNull(read(fresh,"$.profile.majorCode"));assertFalse((Boolean)read(fresh,"$.profile.availabilityDeclared"));
        assertEquals(List.of(),read(fresh,"$.profile.availablePeriods"));assertEquals(List.of(),read(fresh,"$.profile.orientationCodes"));
        assertEquals("0901234567",read(fresh,"$.profile.phone"));assertEquals(List.of(projectId),read(fresh,"$.profile.projects[*].projectId"));
        assertEquals(List.of("JAVA"),read(fresh,"$.profile.skills[*].skillCode"));
        var second=input(1);second.put("termId",nextTerm);second.put("majorCode","AI");second.put("projects",List.of(project(projectId)));
        second.put("availablePeriods",List.of(obj("startDate","2027-06-01","endDate","2027-08-31")));
        var saved=b.put(second);
        assertEquals(200,saved.statusCode(),saved.body());assertEquals(2,(int)read(saved,"$.profileVersion"));
        var firstTerm=b.get(TERM);
        assertEquals("SE",read(firstTerm,"$.profile.majorCode"));assertEquals(2,(int)read(firstTerm,"$.profileVersion"));
        assertEquals("2027-02-28",read(firstTerm,"$.profile.availablePeriods[0].endDate"));
        assertEquals(List.of("BACKEND"),read(firstTerm,"$.profile.orientationCodes"));
        assertEquals("AI",read(b.get(nextTerm),"$.profile.majorCode"));
        assertEquals(1.5,((Number)read(firstTerm,"$.profile.skills[0].yearsPractice")).doubleValue());
    }
    @Test void staleVersionIs409AndDoesNotOverwrite() throws Exception {
        Browser b=signedIn(student);
        assertEquals(200,b.put(input(0)).statusCode());
        var stale=input(0);stale.put("phone","0911111111");
        var conflict=b.put(stale);
        assertEnvelope(conflict,409,"PROFILE_STALE");assertEquals(List.of(),fields(conflict));
        assertEquals("0901234567",read(b.get(TERM),"$.profile.phone"));
        var fresh=input(1);fresh.put("phone","0911111111");
        var updated=b.put(fresh);
        assertEquals(200,updated.statusCode(),updated.body());assertEquals(2,(int)read(updated,"$.profileVersion"));
    }
    @Test void closedSchemaAndFieldRulesAre400WithFieldErrors() throws Exception {
        Browser b=signedIn(student);
        var missing=input(0);missing.remove("phone");missing.put("bio","không có trong BC11");
        var res=b.put(missing);
        assertEnvelope(res,400,"SCHEMA_VALIDATION");assertTrue(fields(res).containsAll(List.of("phone","bio")),res.body());
        var shortPhone=input(0);shortPhone.put("phone","123");
        assertEquals(List.of("phone"),fields(b.put(shortPhone)));
        var undeclared=input(0);undeclared.put("skillsDeclared",false);
        assertEquals(List.of("skills"),fields(b.put(undeclared)));
        var reversed=input(0);reversed.put("availablePeriods",List.of(obj("startDate","2027-03-01","endDate","2027-01-01")));
        assertEquals(List.of("availablePeriods[0]"),fields(b.put(reversed)));
        var schoolVerified=input(0);schoolVerified.put("skills",List.of(obj("skillCode","JAVA","level",3,"yearsPractice",1,"note",null,"source","SCHOOL_VERIFIED")));
        assertEquals(List.of("skills[0].source"),fields(b.put(schoolVerified)));
        var duplicated=input(0);duplicated.put("skills",List.of(obj("skillCode","JAVA","level",3,"yearsPractice",1,"note",null),
            obj("skillCode","JAVA","level",4,"yearsPractice",2,"note",null)));
        assertEquals(List.of("skills[1].skillCode"),fields(b.put(duplicated)));
        var precise=input(0);precise.put("skills",List.of(obj("skillCode","JAVA","level",3,"yearsPractice",-0.001,"note",null)));
        assertEquals(List.of("skills[0].yearsPractice"),fields(b.put(precise)));
        var noKey=b.send("PUT",PROFILE,new tools.jackson.databind.ObjectMapper().writeValueAsString(input(0)),true,null);
        assertEnvelope(noKey,400,"INVALID_REQUEST");
        assertEnvelope(b.get("not-a-uuid"),400,"INVALID_REQUEST");
        assertEnvelope(b.get(TERM),404,"RESOURCE_NOT_FOUND");
    }
    @Test void majorChangeMustKeepDeclaredRecords() throws Exception {
        Browser b=signedIn(student);
        var first=input(0);first.put("projects",List.of(project(null)));
        var created=b.put(first);
        assertEquals(200,created.statusCode(),created.body());
        String projectId=read(created,"$.profile.projects[0].projectId");
        var drop=input(1);drop.put("majorCode","AI");
        assertEnvelope(b.put(drop),422,"MAJOR_CHANGE_WOULD_DROP_DATA");
        assertEquals(List.of(projectId),read(b.get(TERM),"$.profile.projects[*].projectId"));
        var keep=input(1);keep.put("majorCode","AI");keep.put("projects",List.of(project(projectId)));
        assertEquals(200,b.put(keep).statusCode());
        var remove=input(2);remove.put("majorCode","AI");
        var removed=b.put(remove);
        assertEquals(200,removed.statusCode(),removed.body());assertEquals(List.of(),read(removed,"$.profile.projects"));
    }
    @Test void foreignRecordIdIs404AndUnknownFileReferenceIs422() throws Exception {
        Browser b=signedIn(student);
        assertEquals(200,b.put(input(0)).statusCode());
        var foreign=input(1);foreign.put("customSkills",List.of(obj("customSkillId",UUID.randomUUID().toString(),"name","Thiết kế",
            "categoryCode","DESIGN","level",2,"yearsPractice",1,"note",null)));
        assertEnvelope(b.put(foreign),404,"RESOURCE_NOT_FOUND");
        var file=input(1);file.put("cvFileId",UUID.randomUUID().toString());
        var rejected=b.put(file);
        assertEnvelope(rejected,422,"FILE_BINDING_INVALID");assertEquals(List.of("cvFileId"),fields(rejected));
    }
    @Test void cleanOwnFilesAttachToCvProjectAndCertificateAndSurviveReload() throws Exception {
        Browser b=signedIn(student);
        assertEquals(200,b.put(input(0)).statusCode());
        String cv=b.upload(student,"CV"), evidence=b.upload(student,"EVIDENCE"), certificate=b.upload(student,"EVIDENCE");
        for(String id:List.of(cv,evidence,certificate)) syntheticClean(id);
        var attach=input(1);attach.put("cvFileId",cv);
        var project=project(null);project.put("evidenceFileIds",List.of(evidence));attach.put("projects",List.of(project));
        attach.put("certificates",List.of(obj("certificateId",null,"title","Chứng chỉ thử nghiệm","issuer","Đơn vị thử nghiệm",
            "issuedOn","2026-06-01","expiresOn",null,"fileId",certificate)));
        var saved=b.put(attach);
        assertEquals(200,saved.statusCode(),saved.body());assertEquals(2,(int)read(saved,"$.profileVersion"));
        var reloaded=b.get(TERM);
        assertEquals(cv,read(reloaded,"$.profile.cvFileId"));
        assertEquals(List.of(evidence),read(reloaded,"$.profile.projects[0].evidenceFileIds"));
        assertEquals(certificate,read(reloaded,"$.profile.certificates[0].fileId"));
        assertEquals(200,b.download(cv));
        // Kept references are not re-checked: a later save that keeps them succeeds without new uploads.
        var keep=input(2);keep.put("cvFileId",cv);
        var keptProject=project((String)read(reloaded,"$.profile.projects[0].projectId"));keptProject.put("evidenceFileIds",List.of(evidence));
        keep.put("projects",List.of(keptProject));
        keep.put("certificates",List.of(obj("certificateId",read(reloaded,"$.profile.certificates[0].certificateId"),"title","Chứng chỉ thử nghiệm",
            "issuer","Đơn vị thử nghiệm","issuedOn","2026-06-01","expiresOn",null,"fileId",certificate)));
        assertEquals(200,b.put(keep).statusCode());
    }
    @Test void pendingWrongPurposeForeignAndDeletedFilesAreRejectedWithoutSaving() throws Exception {
        Browser b=signedIn(student);
        assertEquals(200,b.put(input(0)).statusCode());
        String pending=b.upload(student,"CV");
        var waiting=input(1);waiting.put("cvFileId",pending);
        var notClean=b.put(waiting);
        assertEnvelope(notClean,409,"FILE_NOT_CLEAN");assertEquals(List.of("cvFileId"),fields(notClean));
        String evidence=b.upload(student,"EVIDENCE");syntheticClean(evidence);
        var wrongPurpose=input(1);wrongPurpose.put("cvFileId",evidence);
        var wrong=b.put(wrongPurpose);
        assertEnvelope(wrong,422,"FILE_BINDING_INVALID");assertEquals(List.of("cvFileId"),fields(wrong));
        UserAccount other=student(university.id);Browser o=signedIn(other);
        assertEquals(200,o.put(input(0)).statusCode());
        String foreign=o.upload(other,"CV");syntheticClean(foreign);
        var stolen=input(1);stolen.put("cvFileId",foreign);
        var foreignRejected=b.put(stolen);
        assertEnvelope(foreignRejected,422,"FILE_BINDING_INVALID");assertEquals(List.of("cvFileId"),fields(foreignRejected));
        String deleted=b.upload(student,"CV");syntheticClean(deleted);
        jdbc.update("UPDATE files SET deleted_at=UTC_TIMESTAMP(6) WHERE id=?",deleted);
        var gone=input(1);gone.put("cvFileId",deleted);
        assertEnvelope(b.put(gone),422,"FILE_BINDING_INVALID");
        assertEquals(1,jdbc.queryForObject("SELECT profile_version FROM student_profiles WHERE user_id=?",Integer.class,student.id));
        assertNull(jdbc.queryForObject("SELECT cv_file_id FROM student_profiles WHERE user_id=?",String.class,student.id));
    }
    @Test void pendingSchoolRevokedMembershipMissingCsrfAndAnonymousAreDenied() throws Exception {
        assertEnvelope(signedIn(student(university(Organization.Status.PENDING).id)).get(TERM),403,"ACCESS_DENIED");
        Browser b=signedIn(student);
        var noCsrf=b.send("PUT",PROFILE,new tools.jackson.databind.ObjectMapper().writeValueAsString(input(0)),false,UUID.randomUUID().toString());
        assertEquals(403,noCsrf.statusCode(),noCsrf.body());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles",Integer.class));
        Membership membership=memberships.findByUserIdAndOrganizationIdAndRole(student.id,university.id,Role.STUDENT).orElseThrow();
        membership.active=false;memberships.saveAndFlush(membership);
        assertEnvelope(b.get(TERM),403,"ACCESS_DENIED");
        assertEquals(401,new Browser().get(TERM).statusCode());
    }

    @Test void approvedContractDecimalsPersistExactlyForCanonicalAndCustomSkills() throws Exception {
        var b=signedIn(student);var value=input(0);
        var years=new java.math.BigDecimal("1000.12345678901234567890123456789");
        value.put("skills",List.of(obj("skillCode","JAVA","level",3,"yearsPractice",years,"note",null)));
        value.put("customSkills",List.of(obj("customSkillId",null,"name","Precise custom","categoryCode","DESIGN","level",2,"yearsPractice",new java.math.BigDecimal("1.001"),"note",null)));
        String wire=new tools.jackson.databind.ObjectMapper().writeValueAsString(value),key=UUID.randomUUID().toString();
        var saved=b.send("PUT",PROFILE,wire,true,key);assertEquals(200,saved.statusCode(),saved.body());
        var replay=b.send("PUT",PROFILE,wire,true,key);assertEquals(200,replay.statusCode(),replay.body());assertTrue(replay.body().contains(years.toPlainString()),replay.body());
        assertTrue(saved.body().contains(years.toPlainString()),saved.body());
        assertTrue(b.get(TERM).body().contains(years.toPlainString()));
        assertEquals(years.toPlainString(),jdbc.queryForObject("SELECT s.years_practice FROM student_profile_skills s JOIN student_profiles p ON p.id=s.profile_id WHERE p.user_id=?",String.class,student.id));
        assertEquals("1.001",jdbc.queryForObject("SELECT s.years_practice FROM student_custom_skills s JOIN student_profiles p ON p.id=s.profile_id WHERE p.user_id=?",String.class,student.id));
    }

}
