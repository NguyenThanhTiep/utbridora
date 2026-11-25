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
class AssignmentManagementIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static final java.nio.file.Path FILES=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {try{return java.nio.file.Files.createTempDirectory("assignment-management-");}catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);p.add("app.files.storage-root",FILES::toString);
    }
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    @LocalServerPort int port;
    final String password="Synthetic-Assignment-"+UUID.randomUUID();String hash;
    String school,enterprise,term,delegation,coordinatorGrant,hrGrant,mentorGrant,caseId,placement,cohort,group,oldGroup,task;
    UserAccount admin,staff,coordinator,hr,mentor,student,newCoordinator,newHr,outsider,peerHr;
    record Session(HttpClient client,String csrfHeader,String csrfToken,String context) {
        Session context(String id) {return new Session(client,csrfHeader,csrfToken,id);}
    }
    record Reply(int status,tools.jackson.databind.JsonNode body,HttpHeaders headers) {}
    @BeforeAll void passwordHash() {hash=encoder.encode(password);}
    @BeforeEach void fixture() {
        assertTrue(MYSQL.isRunning());school=org(Organization.Type.UNIVERSITY);enterprise=org(Organization.Type.ENTERPRISE);term=id();
        admin=user(Role.ADMIN,null,"Admin");staff=user(Role.SCHOOL_STAFF,school,"School confirmer");coordinator=user(Role.ENTERPRISE,enterprise,"Coordinator");hr=user(Role.ENTERPRISE,enterprise,"HR issuer");mentor=user(Role.ENTERPRISE,enterprise,"Accepted mentor");student=user(Role.STUDENT,school,"Student");
        newCoordinator=user(Role.ENTERPRISE,enterprise,"New coordinator");newHr=user(Role.ENTERPRISE,enterprise,"New HR");peerHr=user(Role.ENTERPRISE,enterprise,"Peer HR");outsider=user(Role.ENTERPRISE,org(Organization.Type.ENTERPRISE),"Foreign member");
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2020-01-01','2099-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,"ASSIGN-"+id());
        delegation=id();jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_PREREQUISITE',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        for(String permission:List.of("t3:coordinator:confirm","student:preference:school:review")) jdbc.update("INSERT INTO school_delegation_permissions VALUES (?,?)",delegation,permission);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_PREREQUISITE',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        coordinatorGrant=grant("COORDINATOR",coordinator.id,staff.id,null,delegation,null);
        hrGrant=grant("HR",hr.id,coordinator.id,coordinatorGrant,null,null);
        grant("HR",peerHr.id,coordinator.id,coordinatorGrant,null,null);
        placement=id();caseId=id();cohort=id();group=id();oldGroup=id();task=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,UTC_TIMESTAMP(6),'2020-01-01','2099-01-01',UTC_TIMESTAMP(6),'SYNTHETIC_PREREQUISITE')",placement,student.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,placement,student.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,'Synthetic',1)",cohort,enterprise,school,term,"COHORT-"+id());
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'G-current',1),(?,?,'G-old',1)",group,cohort,oldGroup,cohort);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,caseId,group);
        jdbc.update("INSERT INTO internship_tasks(id,group_id,title,description,due_at,priority,late_allowed,version) VALUES (?,?,'Old task','Immutable task description',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'NORMAL',true,1)",task,oldGroup);
        jdbc.update("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES (?,?,?,?,'IN_PROGRESS',2)",id(),task,caseId,student.id);
        mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
    }
    private String grant(String kind,String subject,String grantor,String parent,String source,String groupId) {
        String grant=id();jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_PREREQUISITE',true,DATE_SUB(UTC_DATE(),INTERVAL 1 HOUR),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY))",grant,kind,subject,enterprise,school,term,groupId,parent,source,grantor);return grant;
    }
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
        if(path.startsWith("/school/preferences?")) request.header("X-Organization-Id",school);
        var r=s.client().send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(),r.body().isBlank()?null:JSON.readTree(r.body()),r.headers());
    }
    Reply get(Session s,String path) throws Exception {return call(s,"GET",path,null,null,false);}
    String query() {return "schoolOrgId="+school+"&enterpriseOrgId="+enterprise+"&termId="+term;}
    String managed(String suffix) {return "/t3/assignment-management/"+suffix;}
    Map<String,Object> grantBody(String subject,boolean confirm) {
        var body=new LinkedHashMap<String,Object>();body.put("userId",subject);body.put("validFrom",Instant.now().minusSeconds(30).toString());body.put("validUntil",Instant.now().plusSeconds((confirm?10:5)*86400).toString());body.put("decisionRef","SYNTHETIC_COMMAND");
        if(confirm) {body.put("enterpriseOrgId",enterprise);body.put("termId",term);} else body.put("scope",scope(null,null));
        return body;
    }
    Map<String,Object> scope(String groupId,String caseId) {var result=new LinkedHashMap<String,Object>();result.put("schoolOrgId",school);result.put("enterpriseOrgId",enterprise);result.put("termId",term);result.put("groupId",groupId);result.put("caseId",caseId);return result;}

    @Test void managementReadsAreScopedPaginatedMinimalAndDenyGlobalAdminOrUnassignedEnterprise() throws Exception {
        var schoolSession=login(staff);var ec=login(coordinator).context(coordinatorGrant);
        var scopes=get(schoolSession,managed("scopes?size=1"));assertEquals(200,scopes.status());assertEquals(1,scopes.body().get("totalElements").asInt());assertEquals("SCHOOL",scopes.body().get("content").get(0).get("mode").asString());
        var companies=get(schoolSession,managed("enterprises?schoolOrgId="+school+"&termId="+term));assertEquals(1,companies.body().get("totalElements").asInt());assertEquals(enterprise,companies.body().get("content").get(0).get("enterpriseOrgId").asString());
        var candidates=get(ec,managed("candidates?"+query()+"&search=New&size=1"));assertEquals(200,candidates.status());assertEquals(2,candidates.body().get("totalElements").asInt());assertEquals(1,candidates.body().get("content").size());
        assertTrue(candidates.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        var json=candidates.body().toString();assertFalse(json.contains("email"));assertFalse(json.contains("password"));assertFalse(json.contains(outsider.id));
        assertEquals(2,get(ec,managed("assignments?"+query())).body().get("totalElements").asInt());
        assertEquals(400,get(ec,managed("assignments?"+query()+"&size=101")).status());assertEquals(400,get(ec,managed("candidates?"+query()+"&page=-1")).status());
        assertEquals(404,get(login(admin),managed("assignments?"+query())).status());
        assertEquals(403,get(login(newHr).context(hrGrant),managed("assignments?"+query())).status());
        assertEquals(404,get(login(mentor).context(mentorGrant),managed("assignments?"+query())).status());
        assertEquals(404,get(ec,managed("assignments?"+query().replace(school,id()))).status());
        assertEquals(404,get(ec,managed("assignments/"+mentorGrant+"?"+query())).status());
    }
    @Test void confirmGrantScopedHrRevokeVersionAndParentInvalidationUseExistingCommands() throws Exception {
        var staffSession=login(staff);var coordinatorOld=login(newCoordinator);
        var confirmed=call(staffSession,"POST","/t3/coordinators/confirm",grantBody(newCoordinator.id,true),id(),true);assertEquals(201,confirmed.status(),confirmed.body().toString());String parent=confirmed.body().get("id").asString();
        assertEquals(401,get(coordinatorOld,managed("scopes")).status());var ec=login(newCoordinator).context(parent);var hrOld=login(newHr);
        var body=grantBody(newHr.id,false);body.put("scope",scope(group,caseId));
        var granted=call(ec,"POST","/t3/hr-assignments",body,id(),true);assertEquals(201,granted.status(),granted.body().toString());String child=granted.body().get("id").asString();
        assertEquals(401,get(hrOld,"/t3/context-assignments/me").status());
        var current=login(newHr);assertEquals(1,get(current,"/t3/context-assignments/me").body().size());
        assertEquals(0,get(current,managed("scopes")).body().get("totalElements").asInt());
        assertEquals(group,get(ec,managed("assignments/"+child+"?"+query())).body().get("assignment").get("scope").get("groupId").asString());
        assertEquals(409,call(staffSession,"DELETE","/t3/context-assignments/"+parent,Map.of("expectedVersion",2,"reasonCode","SECURITY_REVIEW"),id(),true).status());
        String key=id();var revoke=Map.of("expectedVersion",1,"reasonCode","SECURITY_REVIEW");
        assertEquals(200,call(staffSession,"DELETE","/t3/context-assignments/"+parent,revoke,key,true).status());assertEquals(200,call(staffSession,"DELETE","/t3/context-assignments/"+parent,revoke,key,true).status());
        assertEquals(401,get(ec,managed("scopes")).status());assertEquals(401,get(current,"/t3/context-assignments/me").status());
        assertEquals(0,get(login(newHr),"/t3/context-assignments/me").body().size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='CAPABILITY_REVOKED' AND resource_id=?",Integer.class,parent));
        assertFalse(get(staffSession,managed("assignments/"+parent+"?"+query())).body().get("canRevoke").asBoolean());
    }
    @Test void handoverUsesCurrentCaseVersionAndOnlyCurrentlyEligibleMentors() throws Exception {
        var actor=login(hr).context(hrGrant);var mentorOld=login(mentor);
        var options=get(actor,managed("cases/"+caseId+"/mentors?"+query()));assertEquals(200,options.status());assertEquals(mentor.id,options.body().get("content").get(0).get("userId").asString());
        assertEquals(404,get(actor,managed("cases/"+id()+"/mentors?"+query())).status());
        var body=new LinkedHashMap<String,Object>();body.put("caseId",caseId);body.put("newMentorUserId",mentor.id);body.put("expectedCaseVersion",1);body.put("reasonCode","TEAM_CHANGED");
        assertEquals(409,call(actor,"POST","/t3/cases/"+caseId+"/mentor-handover",body,id(),true).status());
        body.put("expectedCaseVersion",2);body.put("newMentorUserId",newHr.id);assertEquals(422,call(actor,"POST","/t3/cases/"+caseId+"/mentor-handover",body,id(),true).status());
        body.put("newMentorUserId",mentor.id);assertEquals(403,call(actor,"POST","/t3/cases/"+caseId+"/mentor-handover",body,id(),false).status());
        assertEquals(200,call(actor,"POST","/t3/cases/"+caseId+"/mentor-handover",body,id(),true).status());
        assertEquals(3,jdbc.queryForObject("SELECT version FROM internship_cases WHERE id=?",Integer.class,caseId));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_mentor_handovers WHERE case_id=?",Integer.class,caseId));
        assertEquals("Immutable task description",jdbc.queryForObject("SELECT description FROM internship_tasks WHERE id=?",String.class,task));assertEquals(401,get(mentorOld,"/t3/context-assignments/me").status());
    }
    @Test void mentorRevocationIsSeparateFromInvitationAndOnlyTheGrantorCanReadTheRecord() throws Exception {
        var actor=login(hr).context(hrGrant);String peerGrant=jdbc.queryForObject("SELECT id FROM internship_context_assignments WHERE user_id=?",String.class,peerHr.id);
        assertEquals(404,get(login(peerHr).context(peerGrant),managed("assignments/"+mentorGrant+"?"+query())).status());
        var detail=get(actor,managed("assignments/"+mentorGrant+"?"+query()));assertTrue(detail.body().get("canRevoke").asBoolean());
        assertEquals(200,call(actor,"DELETE","/t3/context-assignments/"+mentorGrant,Map.of("expectedVersion",1,"reasonCode","MENTOR_CHANGED"),id(),true).status());
        assertEquals(0,get(actor,managed("assignments?"+query())).body().get("totalElements").asInt());assertEquals(1,get(actor,managed("assignments?"+query()+"&includeInactive=true")).body().get("totalElements").asInt());
    }
    @Test void preferenceReadApisKeepSchoolTermAndDelegationBoundaries() throws Exception {
        String demand=id(),cooperation=id(),preference=id();
        jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES (?,?,?,?,'READY','VALID',1,1,REPEAT('a',64),JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",demand,enterprise,school,term);
        jdbc.update("INSERT INTO cooperations(id,demand_id,school_org_id,enterprise_org_id,term_id,state,submission_no,version,created_at) VALUES (?,?,?,?,?,'APPROVED',1,1,UTC_TIMESTAMP(6))",cooperation,demand,school,enterprise,term);
        jdbc.update("INSERT INTO student_preferences(preference_id,student_user_id,school_org_id,term_id,opportunity_id,intent_version,version,status,message,intent_created_at,created_at,updated_at) VALUES (?,?,?,?,?,1,1,'SUBMITTED','Synthetic intent',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",preference,student.id,school,term,cooperation);
        var actor=login(staff);assertEquals(1,get(actor,"/school/preferences/scopes").body().get("totalElements").asInt());
        assertEquals(1,get(actor,"/school/preferences?termId="+term+"&status=SUBMITTED").body().get("totalElements").asInt());assertEquals(preference,get(actor,"/school/preferences/"+preference).body().get("preferenceId").asString());
        assertEquals(404,get(login(admin),"/school/preferences/"+preference).status());assertEquals(404,get(login(student),"/school/preferences/"+preference).status());
        assertEquals(404,get(actor,"/school/preferences?termId="+id()).status());assertEquals(400,get(actor,"/school/preferences?termId="+term+"&status=APPROVED").status());
        assertEquals(200,call(actor,"POST","/school/preferences/"+preference+"/review",Map.of("expectedVersion",1,"reasonCode","SCHOOL_REVIEWED"),id(),true).status());assertEquals(0,get(actor,"/school/preferences?termId="+term+"&status=SUBMITTED").body().get("totalElements").asInt());
        jdbc.update("UPDATE school_delegations SET active=false WHERE id=?",delegation);assertEquals(404,get(actor,"/school/preferences/"+preference).status());
    }
    @Test void sentInvitationReadsReturnOnlyTheCurrentHrSchoolScopeAndNoCredentialMaterial() throws Exception {
        var actor=login(hr).context(hrGrant);
        var issue=call(actor,"POST","/t3/mentor-invitations",Map.of("email",mentor.email,"enterpriseOrgId",enterprise,"termId",term,"groupIds",List.of(group),"decisionRef","SYNTHETIC_INVITATION"),id(),true);
        assertEquals(202,issue.status(),issue.body().toString());String invite=issue.body().get("invitationId").asString();
        var path="/t3/mentor-invitations/sent?enterpriseOrgId="+enterprise+"&termId="+term;
        var sent=get(actor,path);assertEquals(1,sent.body().get("totalElements").asInt());assertEquals(mentor.email,sent.body().get("content").get(0).get("email").asString());assertFalse(sent.body().toString().contains("token"));assertFalse(sent.body().toString().contains("password"));
        assertEquals(200,get(actor,"/t3/mentor-invitations/sent/"+invite+"?enterpriseOrgId="+enterprise+"&termId="+term).status());
        String peerGrant=jdbc.queryForObject("SELECT id FROM internship_context_assignments WHERE user_id=?",String.class,peerHr.id);var peer=login(peerHr).context(peerGrant);
        assertEquals(0,get(peer,path).body().get("totalElements").asInt());assertEquals(404,get(peer,"/t3/mentor-invitations/sent/"+invite+"?enterpriseOrgId="+enterprise+"&termId="+term).status());
        assertEquals(403,get(login(mentor).context(hrGrant),path).status());
        assertEquals(404,get(actor,path.replace(term,id())).status());
        assertEquals(200,call(actor,"POST","/t3/mentor-invitations/"+invite+"/revoke",Map.of("expectedVersion",1,"reasonCode","INVITE_CHANGED"),id(),true).status());
        assertEquals("REVOKED",get(actor,path).body().get("content").get(0).get("invitation").get("state").asString());
    }
    @Test void expiredRootDisappearsFromManagementReads() throws Exception {
        var actor=login(coordinator).context(coordinatorGrant);assertEquals(1,get(actor,managed("scopes")).body().get("totalElements").asInt());
        jdbc.update("UPDATE school_delegations SET valid_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 HOUR) WHERE id=?",delegation);
        assertEquals(0,get(actor,managed("scopes")).body().get("totalElements").asInt());assertEquals(403,get(actor,managed("assignments?"+query())).status());
        var staffSession=login(staff);
        assertEquals(0,get(staffSession,managed("scopes")).body().get("totalElements").asInt());
    }
    @Test void candidateReferencesRecheckAccountAndMembershipStatus() throws Exception {
        var actor=login(coordinator).context(coordinatorGrant);String path=managed("candidates?"+query()+"&search=New HR").replace(" ","%20");
        assertEquals(1,get(actor,path).body().get("totalElements").asInt());
        jdbc.update("UPDATE memberships SET active=false WHERE user_id=? AND organization_id=?",newHr.id,enterprise);
        assertEquals(0,get(actor,path).body().get("totalElements").asInt());
        jdbc.update("UPDATE memberships SET active=true WHERE user_id=? AND organization_id=?",newHr.id,enterprise);jdbc.update("UPDATE users SET status='DEACTIVATED' WHERE id=?",newHr.id);
        assertEquals(0,get(actor,path).body().get("totalElements").asInt());
        jdbc.update("UPDATE organizations SET status='SUSPENDED' WHERE id=?",enterprise);
        assertEquals(403,get(actor,managed("assignments?"+query())).status());
    }
    @Test void lapsedVerificationBlocksConfirmationButRetainsSchoolRevocationAccess() throws Exception {
        var actor=login(staff);jdbc.update("UPDATE enterprise_verifications SET state='REJECTED' WHERE enterprise_org_id=? AND school_org_id=?",enterprise,school);
        var options=get(actor,managed("enterprises?schoolOrgId="+school+"&termId="+term));assertEquals(1,options.body().get("totalElements").asInt());assertFalse(options.body().get("content").get(0).get("canConfirm").asBoolean());
        assertEquals(404,get(actor,managed("candidates?"+query())).status());
        var detail=get(actor,managed("assignments/"+coordinatorGrant+"?"+query()));assertEquals(200,detail.status());assertTrue(detail.body().get("canRevoke").asBoolean());assertFalse(detail.body().get("effective").asBoolean());
        assertEquals(200,call(actor,"DELETE","/t3/context-assignments/"+coordinatorGrant,Map.of("expectedVersion",1,"reasonCode","VERIFICATION_LAPSED"),id(),true).status());
    }
    @Test void browserCompletesCoordinatorHrHandoverAndRootRevocationOnRealApis() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("frontend.e2e"),"Enable frontend.e2e for real Chromium");
        var config=new LinkedHashMap<String,String>();config.put("password",password);config.put("staffEmail",staff.email);config.put("coordinatorEmail",newCoordinator.email);config.put("hrEmail",newHr.email);config.put("coordinatorId",newCoordinator.id);config.put("hrId",newHr.id);config.put("enterpriseId",enterprise);config.put("caseId",caseId);config.put("mentorId",mentor.id);
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- assignment-management-live.spec.ts"):new ProcessBuilder("npm","run","test:e2e","--","assignment-management-live.spec.ts");builder.directory(new java.io.File("web-demo"));
        builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);builder.environment().put("E2E_ASSIGNMENT_MANAGEMENT",JSON.writeValueAsString(config));builder.environment().put("E2E_REPORT_FILE","../target/assignment-management-browser.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/assignment-management-browser.log"));var process=builder.start();
        try {if(!process.waitFor(240,TimeUnit.SECONDS))fail("Browser timed out; see target/assignment-management-browser.log");assertEquals(0,process.exitValue(),"See target/assignment-management-browser.log");}
        finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_context_assignments WHERE kind='COORDINATOR' AND user_id=? AND active=false",Integer.class,newCoordinator.id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_mentor_handovers WHERE case_id=?",Integer.class,caseId));
    }
}
