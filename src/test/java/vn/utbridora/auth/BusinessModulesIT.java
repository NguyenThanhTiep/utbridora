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
class BusinessModulesIT {
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
    @Autowired vn.utbridora.auth.modules.cooperation.persistence.TermRepository terms;
    @Autowired PasswordEncoder encoder;
    @Autowired vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort verificationPort;
    String password="Test-"+UUID.randomUUID(),hash;
    UserAccount editor,admin,student;
    @BeforeAll void hash() {hash=encoder.encode(password);}
    @BeforeEach void seed() {
        jdbc.update("UPDATE enterprise_verifications SET previous_verification_id=NULL");jdbc.update("UPDATE school_delegations SET parent_id=NULL");
        jdbc.update("UPDATE skills SET parent_skill_id=NULL");jdbc.update("UPDATE skill_categories SET parent_category_id=NULL");
        for(String table:List.of("operation_records","enterprise_verification_history","enterprise_verifications","file_bindings","files","school_delegation_permissions","school_delegations","demand_revisions","demands","skill_implies","skill_aliases","skills","skill_categories","taxonomy_editor_assignments","SPRING_SESSION_ATTRIBUTES","SPRING_SESSION","audit_logs","memberships","terms","users","organizations")) jdbc.update("DELETE FROM "+table);
        jdbc.update("UPDATE taxonomy_catalog_state SET taxonomy_version=1");
        editor=user(Role.ADMIN);admin=user(Role.ADMIN);student=user(Role.STUDENT);
        grant(editor);
    }
    UserAccount user(Role role) {
        UserAccount user=new UserAccount();user.email=UUID.randomUUID()+"@example.test";user.displayName="Taxonomy Test";user.passwordHash=hash;user.status=AccountStatus.ACTIVE;user=users.saveAndFlush(user);
        Membership m=new Membership();m.userId=user.id;m.role=role;
        if(role!=Role.ADMIN) {
            Organization school=new Organization();school.name="Test School";school.type=role==Role.ENTERPRISE?Organization.Type.ENTERPRISE:Organization.Type.UNIVERSITY;school.status=Organization.Status.VERIFIED;
            m.organizationId=organizations.saveAndFlush(school).id;
        }
        memberships.saveAndFlush(m);return user;
    }
    void grant(UserAccount user) {
        jdbc.update("INSERT INTO taxonomy_editor_assignments(id,user_id,decision_ref,approved_by_t1,approved_by_t4,valid_from,valid_until) VALUES (?,?,?,?,?,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY))",UUID.randomUUID().toString(),user.id,"ISOLATED_TEST_MANIFEST",user.id,user.id);
    }
    class Browser {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf,organization;
        HttpResponse<String> send(String method,String path,String body,String key,boolean csrfEnabled) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json");
            if(organization!=null)r.header("X-Organization-Id",organization);
            if(key!=null)r.header("Idempotency-Key",key);if(csrfEnabled && csrf!=null)r.header("X-CSRF-TOKEN",csrf);
            return client.send(r.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> verify(String method,String path,Object data,String key) throws Exception {
            return send(method,"/api/v1/"+path,data==null?null:new tools.jackson.databind.ObjectMapper().writeValueAsString(data),key,true);
        }
        void csrf() throws Exception {csrf=JsonPath.read(send("GET","/api/v1/auth/csrf",null,null,false).body(),"$.token");}
        HttpResponse<String> write(String method,String path,String body,String key) throws Exception {return send(method,"/api/v1/taxonomy"+path,body,key,true);}
    }
    Browser login(UserAccount user) throws Exception {
        Browser b=new Browser();b.csrf();assertEquals(200,b.send("POST","/api/v1/auth/login","{\"email\":\""+user.email+"\",\"password\":\""+password+"\"}",null,true).statusCode());b.csrf();return b;
    }
    static final String CATEGORY="{\"categoryCode\":\"PROGRAMMING\",\"name\":\"Programming\",\"parentCategoryCode\":null}";
    static void code(HttpResponse<String> r,int status,String code) {assertEquals(status,r.statusCode(),r.body());assertEquals(code,JsonPath.read(r.body(),"$.errorCode"));}
    @Test void productionDemandReferencesFailClosedWhenAcademicCatalogIsAbsent() throws Exception {
        UserAccount enterprise=user(Role.ENTERPRISE);Browser b=login(enterprise);
        String enterpriseId=jdbc.queryForObject("SELECT organization_id FROM memberships WHERE user_id=?",String.class,enterprise.id);
        String schoolId=jdbc.queryForObject("SELECT organization_id FROM memberships WHERE user_id=?",String.class,student.id);
        var term=new vn.utbridora.auth.modules.cooperation.domain.Term();term.schoolOrgId=schoolId;term.termCode="TERM-1";
        term.startDate=java.time.LocalDate.parse("2027-01-01");term.endDate=java.time.LocalDate.parse("2027-06-01");term.status=vn.utbridora.auth.modules.cooperation.domain.TermStatus.ACTIVE;terms.saveAndFlush(term);
        String data="{\"schoolOrgId\":\""+schoolId+"\",\"termId\":\""+term.id+"\",\"title\":\"Internship\",\"quantity\":5,\"description\":\"Development\",\"location\":null,\"benefits\":null,\"deadline\":\"2027-01-01T00:00:00Z\",\"required\":{\"majorCodes\":[],\"skills\":[],\"dateRange\":{\"startDate\":\"2027-02-01\",\"endDate\":\"2027-05-01\"}},\"preferred\":[],\"academicCatalogVersion\":\"v1\",\"taxonomyVersion\":\"skills-v1\"}";
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/enterprise/demands")).header("Content-Type","application/json")
            .header("X-Organization-Id",enterpriseId).header("Idempotency-Key",UUID.randomUUID().toString()).header("X-CSRF-TOKEN",b.csrf)
            .POST(HttpRequest.BodyPublishers.ofString(data)).build();
        code(b.client.send(request,HttpResponse.BodyHandlers.ofString()),503,"DEPENDENCY_UNAVAILABLE");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE actor_id=?",Integer.class,enterprise.id));
    }
    @Test void authenticatedStudentCanReadButCannotBecomeATaxEditor() throws Exception {
        Browser b=login(student);
        assertEquals(200,b.send("GET","/api/v1/taxonomy/skills",null,null,false).statusCode());
        grant(student);
        code(b.write("POST","/categories",CATEGORY,UUID.randomUUID().toString()),403,"ACCESS_DENIED");
        jdbc.update("UPDATE taxonomy_editor_assignments SET valid_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 HOUR) WHERE user_id=?",editor.id);
        code(login(editor).write("POST","/categories",CATEGORY,UUID.randomUUID().toString()),403,"ACCESS_DENIED");
    }
    record VerificationSetup(UserAccount enterprise,UserAccount staff,String enterpriseId,String schoolId,String termId,Browser owner,Browser reviewer) {}
    VerificationSetup verificationSetup(boolean delegated) throws Exception {
        UserAccount enterprise=user(Role.ENTERPRISE),staff=user(Role.SCHOOL_STAFF);
        String org=jdbc.queryForObject("SELECT organization_id FROM memberships WHERE user_id=?",String.class,enterprise.id);
        String school=jdbc.queryForObject("SELECT organization_id FROM memberships WHERE user_id=?",String.class,staff.id);
        var term=new vn.utbridora.auth.modules.cooperation.domain.Term();term.schoolOrgId=school;term.termCode="TERM-1";
        term.startDate=java.time.LocalDate.parse("2027-01-01");term.endDate=java.time.LocalDate.parse("2027-06-01");term.status=vn.utbridora.auth.modules.cooperation.domain.TermStatus.ACTIVE;terms.saveAndFlush(term);
        Browser owner=login(enterprise),reviewer=login(staff);owner.organization=org;reviewer.organization=school;
        var setup=new VerificationSetup(enterprise,staff,org,school,term.id,owner,reviewer);
        if(delegated) delegate(setup);
        return setup;
    }
    void delegate(VerificationSetup s) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'ISOLATED_TEST_MANIFEST','[]',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),true,1)",id,s.staff.id,s.schoolId,s.termId,admin.id);
        for(String permission:List.of("enterprise_verification:school:review","t3:coordinator:confirm","placement:school:write")) jdbc.update("INSERT INTO school_delegation_permissions VALUES (?,?)",id,permission);
    }
    Map<String,Object> verificationInput(String school,List<String> files) {
        var data=new LinkedHashMap<String,Object>();data.put("schoolOrgId",school);data.put("previousVerificationId",null);data.put("registrationCode","DEMO-123");
        data.put("registeredName","Synthetic enterprise");data.put("address","Synthetic address");data.put("contactName","Synthetic contact");data.put("contactEmail","contact@example.test");data.put("contactPhone","0901234567");data.put("evidenceFileIds",files);return data;
    }
    String createVerification(VerificationSetup s) throws Exception {
        var r=s.owner.verify("POST","enterprise/verifications",verificationInput(s.schoolId,List.of()),UUID.randomUUID().toString());assertEquals(201,r.statusCode(),r.body());return JsonPath.read(r.body(),"$.verificationId");
    }
    String evidence(VerificationSetup s,String record) {
        return evidence(s,record,"VERIFICATION");
    }
    String evidence(VerificationSetup s,String record,String purpose) {
        // Owner metadata and binding fixture; does not claim an uploader/scanner was exercised.
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO files(id,owner_user_id,organization_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES (?,?,?,?,'evidence.pdf','application/pdf',10,REPEAT('a',64),'PRIVATE','CLEAN',UTC_TIMESTAMP(6))",id,s.enterprise.id,s.enterpriseId,"isolated/"+id);
        jdbc.update("INSERT INTO file_bindings(id,file_id,binding_kind,purpose,resource_id,created_at) VALUES (?,?,'ENTERPRISE_VERIFICATION',?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),id,purpose,record);return id;
    }
    String submitted(VerificationSetup s) throws Exception {
        String id=createVerification(s),file=evidence(s,id);
        var update=s.owner.verify("PATCH","enterprise/verifications/"+id,Map.of("expectedVersion",1,"data",verificationInput(s.schoolId,List.of(file))),UUID.randomUUID().toString());assertEquals(200,update.statusCode(),update.body());
        var submit=s.owner.verify("PATCH","enterprise/verifications/"+id+"/submit",Map.of("expectedVersion",2),UUID.randomUUID().toString());assertEquals(200,submit.statusCode(),submit.body());return id;
    }
    Map<String,Object> decision(long version,String action,List<String> missing) {
        var data=new LinkedHashMap<String,Object>();data.put("expectedVersion",version);data.put("decision",action);data.put("reasonCode",action.equals("APPROVE")?null:"MISSING_DATA");data.put("decisionRef","QĐ/DEMO-01");data.put("missingFields",missing);return data;
    }
    @Test void verificationUsesCanonicalStatesReplayImmutableHistoryAndScopedReadPort() throws Exception {
        var s=verificationSetup(true);String id=submitted(s),path="school/verifications/"+id+"/decision",key=UUID.randomUUID().toString();
        var request=decision(3,"REQUEST_INFO",List.of("contactPhone"));var changes=s.reviewer.verify("PATCH",path,request,key);
        assertEquals(200,changes.statusCode(),changes.body());assertEquals("NEEDS_CHANGES",JsonPath.read(changes.body(),"$.state"));
        assertEquals(changes.body(),s.reviewer.verify("PATCH",path,request,key).body());
        code(s.reviewer.verify("PATCH",path,decision(3,"REJECT",List.of()),key),409,"IDEMPOTENCY_KEY_REUSED");
        String file=jdbc.queryForObject("SELECT file_id FROM file_bindings WHERE resource_id=?",String.class,id);
        var data=verificationInput(s.schoolId,List.of(file));data.put("contactPhone","0907654321");
        assertEquals(200,s.owner.verify("PATCH","enterprise/verifications/"+id,Map.of("expectedVersion",4,"data",data),UUID.randomUUID().toString()).statusCode());
        assertEquals(200,s.owner.verify("PATCH","enterprise/verifications/"+id+"/submit",Map.of("expectedVersion",5),UUID.randomUUID().toString()).statusCode());
        var approved=s.reviewer.verify("PATCH",path,decision(6,"APPROVE",List.of()),UUID.randomUUID().toString());assertEquals(200,approved.statusCode(),approved.body());
        assertEquals("APPROVED",JsonPath.read(approved.body(),"$.state"));assertEquals(2,(int)JsonPath.read(approved.body(),"$.submissionNo"));assertEquals(7,(int)JsonPath.read(approved.body(),"$.version"));
        assertEquals("0901234567",jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(snapshot_json,'$.data.contactPhone')) FROM enterprise_verification_history WHERE verification_id=? AND to_state='SUBMITTED' AND submission_no=1",String.class,id));
        assertEquals(Set.of("verificationId","enterpriseOrgId","schoolOrgId","state","submissionNo","version","previousVerificationId","data","submittedAt","decidedAt","decisionRef","missingFields"),new tools.jackson.databind.ObjectMapper().readValue(approved.body(),Map.class).keySet());
        var principal=new vn.utbridora.auth.security.AppPrincipal(s.staff.id,s.staff.email,null,s.staff.securityVersion,List.of());
        var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of());
        var context=vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.ReadContext.fromAuthenticatedWorkflow(auth,s.termId,vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.Purpose.CONFIRM_COORDINATOR);
        var proof=(vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.Success)verificationPort.get(context,s.enterpriseId,s.schoolId);
        assertTrue(proof.value().schoolApproved());assertNotNull(proof.value().approvedAt());
        // An authorized history/reference read does not invent an ACTIVE-term write prerequisite.
        jdbc.update("UPDATE terms SET status='CLOSED' WHERE id=?",s.termId);
        assertEquals(200,s.reviewer.verify("GET","school/verifications/"+id,null,null).statusCode());
        assertInstanceOf(vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.Success.class,verificationPort.get(context,s.enterpriseId,s.schoolId));
        assertEquals("VERIFIED",jdbc.queryForObject("SELECT status FROM organizations WHERE id=?",String.class,s.enterpriseId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='ENTERPRISE_VERIFIED'",Integer.class));
        assertInstanceOf(vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort.DomainError.class,verificationPort.get(null,s.enterpriseId,s.schoolId));
    }
    @Test void verificationContactScopeNeedsRealDelegationAndRechecksItBeforeReplay() throws Exception {
        var s=verificationSetup(false);String id=submitted(s);
        code(s.reviewer.verify("GET","school/verifications/"+id,null,null),404,"RESOURCE_NOT_FOUND");
        Browser global=login(admin);global.organization=s.schoolId;
        code(global.verify("GET","school/verifications/"+id,null,null),404,"RESOURCE_NOT_FOUND");
        delegate(s);assertEquals(200,s.reviewer.verify("GET","school/verifications/"+id,null,null).statusCode());
        String key=UUID.randomUUID().toString();var request=decision(3,"APPROVE",List.of());
        assertEquals(200,s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",request,key).statusCode());
        jdbc.update("UPDATE school_delegations SET active=false WHERE user_id=?",s.staff.id);
        code(s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",request,key),404,"RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE users SET security_version=security_version+1 WHERE id=?",s.staff.id);
        code(s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",request,key),401,"SESSION_EXPIRED");
        var foreign=verificationSetup(false);foreign.owner.organization=foreign.enterpriseId;
        code(foreign.owner.verify("GET","enterprise/verifications/"+id,null,null),404,"RESOURCE_NOT_FOUND");
        assertNotEquals(200,s.owner.send("GET","/api/v1/school-verification/"+s.enterpriseId+"/"+s.schoolId,null,null,false).statusCode());
    }
    @Test void verificationRequiresCleanBoundEvidenceAndDoesNotPersistFailedCommands() throws Exception {
        var s=verificationSetup(true);String id=createVerification(s),file=evidence(s,UUID.randomUUID().toString());
        var input=Map.of("expectedVersion",1,"data",verificationInput(s.schoolId,List.of(file)));
        code(s.owner.verify("PATCH","enterprise/verifications/"+id,input,UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        code(s.owner.verify("PATCH","enterprise/verifications/"+id+"/submit",Map.of("expectedVersion",1),UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("INSERT INTO file_bindings(id,file_id,binding_kind,purpose,resource_id,created_at) VALUES (?,?,'ENTERPRISE_VERIFICATION','VERIFICATION',?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),file,id));
        String valid=evidence(s,id);var validInput=Map.of("expectedVersion",1,"data",verificationInput(s.schoolId,List.of(valid)));
        assertEquals(200,s.owner.verify("PATCH","enterprise/verifications/"+id,validInput,UUID.randomUUID().toString()).statusCode());
        jdbc.update("UPDATE files SET scan_status='PENDING' WHERE id=?",valid);
        code(s.owner.verify("PATCH","enterprise/verifications/"+id+"/submit",Map.of("expectedVersion",2),UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        assertEquals("DRAFT",jdbc.queryForObject("SELECT state FROM enterprise_verifications WHERE id=?",String.class,id));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE actor_id=?",Integer.class,s.enterprise.id));
    }
    @Test void verificationChecksPurposeMimeSizeAndCurrentEvidenceAgainAtApproval() throws Exception {
        var s=verificationSetup(true);String id=createVerification(s),wrong=evidence(s,id,"EVIDENCE"),valid=evidence(s,id);
        code(s.owner.verify("PATCH","enterprise/verifications/"+id,Map.of("expectedVersion",1,"data",verificationInput(s.schoolId,List.of(wrong))),UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        var update=Map.of("expectedVersion",1,"data",verificationInput(s.schoolId,List.of(valid)));
        jdbc.update("UPDATE files SET content_type='application/zip' WHERE id=?",valid);
        code(s.owner.verify("PATCH","enterprise/verifications/"+id,update,UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        jdbc.update("UPDATE files SET content_type='application/pdf',size_bytes=10485761 WHERE id=?",valid);
        code(s.owner.verify("PATCH","enterprise/verifications/"+id,update,UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        jdbc.update("UPDATE files SET size_bytes=10 WHERE id=?",valid);
        assertEquals(200,s.owner.verify("PATCH","enterprise/verifications/"+id,update,UUID.randomUUID().toString()).statusCode());
        assertEquals(200,s.owner.verify("PATCH","enterprise/verifications/"+id+"/submit",Map.of("expectedVersion",2),UUID.randomUUID().toString()).statusCode());
        jdbc.update("UPDATE files SET deleted_at=UTC_TIMESTAMP(6) WHERE id=?",valid);
        code(s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",decision(3,"APPROVE",List.of()),UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        assertEquals("SUBMITTED",jdbc.queryForObject("SELECT state FROM enterprise_verifications WHERE id=?",String.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='ENTERPRISE_VERIFIED'",Integer.class));
    }
    @Test void verificationAuditFailureRollsBackDecisionHistoryAndLedger() throws Exception {
        var s=verificationSetup(true);String id=submitted(s),key=UUID.randomUUID().toString();var request=decision(3,"APPROVE",List.of());
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT test_verification_audit CHECK(action <> 'ENTERPRISE_VERIFIED')");
        try {code(s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",request,key),500,"AUDIT_WRITE_FAILED");}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK test_verification_audit");}
        assertEquals("SUBMITTED",jdbc.queryForObject("SELECT state FROM enterprise_verifications WHERE id=?",String.class,id));
        assertEquals(3,jdbc.queryForObject("SELECT version FROM enterprise_verifications WHERE id=?",Integer.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE actor_id=?",Integer.class,s.staff.id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM enterprise_verification_history WHERE verification_id=? AND to_state='APPROVED'",Integer.class,id));
        assertEquals(200,s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",request,key).statusCode());
    }
    @Test void rejectedVerificationCreatesANewLinkedAttemptAndDuplicateDraftsConflict() throws Exception {
        var s=verificationSetup(true);String id=submitted(s);
        assertEquals(200,s.reviewer.verify("PATCH","school/verifications/"+id+"/decision",decision(3,"REJECT",List.of()),UUID.randomUUID().toString()).statusCode());
        var input=verificationInput(s.schoolId,List.of());code(s.owner.verify("POST","enterprise/verifications",input,UUID.randomUUID().toString()),422,"VERIFICATION_DATA_INVALID");
        input.put("previousVerificationId",id);String key=UUID.randomUUID().toString();var created=s.owner.verify("POST","enterprise/verifications",input,key);
        assertEquals(201,created.statusCode(),created.body());assertEquals(created.body(),s.owner.verify("POST","enterprise/verifications",input,key).body());
        assertEquals(id,JsonPath.read(created.body(),"$.previousVerificationId"));
        code(s.owner.verify("POST","enterprise/verifications",input,UUID.randomUUID().toString()),409,"VERIFICATION_ALREADY_OPEN");
        assertEquals("REJECTED",jdbc.queryForObject("SELECT state FROM enterprise_verifications WHERE id=?",String.class,id));
        assertEquals(4,jdbc.queryForObject("SELECT version FROM enterprise_verifications WHERE id=?",Integer.class,id));
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
