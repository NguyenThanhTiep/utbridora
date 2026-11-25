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
class SchoolFollowUpManagementIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static final java.nio.file.Path FILES=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {try{return java.nio.file.Files.createTempDirectory("school-followup-");}catch(java.io.IOException e){throw new ExceptionInInitializerError(e);}}
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);p.add("app.files.storage-root",FILES::toString);
    }
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    @LocalServerPort int port;
    final String password="Synthetic-Assignment-"+UUID.randomUUID();String hash;
    String school,enterprise,term,delegation,coordinatorGrant,hrGrant,mentorGrant,caseId,placement,cohort,group,oldGroup,task;
    String cooperation,preference,template;
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
        for(String permission:List.of("t3:coordinator:confirm","student:preference:school:review","student_profile:school:read","t3:workspace:read","t3:result_policy:manage","t3:grades:publish","t3:coordination:manage","t3:cases:close","t3:enterprise_quality:school:write")) jdbc.update("INSERT INTO school_delegation_permissions VALUES (?,?)",delegation,permission);
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
        jdbc.update("UPDATE school_delegations SET profile_read_fields_json=JSON_ARRAY('studentCode','majorCode') WHERE id=?",delegation);
        String roster=id(),code=jdbc.queryForObject("SELECT term_code FROM terms WHERE id=?",String.class,term);
        jdbc.update("INSERT INTO student_rosters(id,school_org_id,student_code,user_id,full_name,email,major_code,term_code,created_at) VALUES (?,?,?,?,'Private Academic Name',?,'IT','STALE_LEGACY_CODE',UTC_TIMESTAMP(6))",roster,school,"SV-"+id(),student.id,student.email);
        jdbc.update("INSERT INTO student_term_eligibilities(id,student_roster_id,term_code,user_id,school_org_id,term_id,school_approved,created_at) VALUES (?,?,?,?,?,?,false,UTC_TIMESTAMP(6))",id(),roster,code,student.id,school,term);
        String demand=id();cooperation=id();preference=id();
        jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES (?,?,?,?,'READY','VALID',1,1,REPEAT('a',64),JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",demand,enterprise,school,term);
        jdbc.update("INSERT INTO cooperations(id,demand_id,school_org_id,enterprise_org_id,term_id,state,submission_no,version,created_at) VALUES (?,?,?,?,?,'APPROVED',1,1,UTC_TIMESTAMP(6))",cooperation,demand,school,enterprise,term);
        jdbc.update("INSERT INTO student_preferences(preference_id,student_user_id,school_org_id,term_id,opportunity_id,intent_version,version,status,message,intent_created_at,created_at,updated_at) VALUES (?,?,?,?,?,1,1,'SUBMITTED','Synthetic phase two intent',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",preference,student.id,school,term,cooperation);
        template=id();jdbc.update("INSERT INTO internship_enterprise_quality_templates VALUES (?,1,?,?,?)",template,school,term,JSON.writeValueAsString(Map.of("templateId",template,"version",1,"criteria",List.of(Map.of("code","SUPPORT","label","Mentor support")))));
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


    String rosterPath() {return "/school/student-rosters?schoolOrgId="+school;}
    @Test void rosterReadsOnlyLinkedCurrentTermAndExactAllowedFieldsWithNoEnterpriseBypass() throws Exception {
        var actor=login(staff);var result=get(actor,rosterPath());assertEquals(200,result.status(),result.body().toString());
        assertEquals(1,result.body().size());var row=result.body().get(0);
        assertEquals(student.id,row.get("userId").asString());assertEquals("IT",row.get("majorCode").asString());assertTrue(row.get("studentCode").asString().startsWith("SV-"));
        assertTrue(row.get("fullName")==null || row.get("fullName").isNull());assertTrue(row.get("email")==null || row.get("email").isNull());assertFalse(result.body().toString().contains("Private Academic Name"));assertFalse(result.body().toString().contains(student.email));
        assertEquals(jdbc.queryForObject("SELECT term_code FROM terms WHERE id=?",String.class,term),row.get("termCode").asString());
        assertEquals(0,get(actor,rosterPath()+"&termCode=STALE_LEGACY_CODE").body().size());
        assertTrue(result.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals(403,get(login(admin),rosterPath()).status());assertEquals(403,get(login(hr).context(hrGrant),rosterPath()).status());
        assertEquals(403,get(actor,"/school/student-rosters?schoolOrgId="+org(Organization.Type.UNIVERSITY)).status());
    }
    @Test void rosterFailsClosedWithoutTheFieldMaskCurrentMembershipOrLiveDelegation() throws Exception {
        var actor=login(staff);
        jdbc.update("UPDATE school_delegations SET profile_read_fields_json=JSON_ARRAY('skills','cv') WHERE id=?",delegation);
        assertEquals(0,get(actor,rosterPath()).body().size());
        jdbc.update("UPDATE school_delegations SET profile_read_fields_json=JSON_ARRAY('displayName','email') WHERE id=?",delegation);
        var full=get(actor,rosterPath()).body().get(0);assertEquals("Private Academic Name",full.get("fullName").asString());assertEquals(student.email,full.get("email").asString());assertTrue(full.get("studentCode")==null || full.get("studentCode").isNull());
        jdbc.update("UPDATE memberships SET active=false WHERE user_id=? AND organization_id=?",student.id,school);assertEquals(0,get(actor,rosterPath()).body().size());
        jdbc.update("UPDATE memberships SET active=true WHERE user_id=? AND organization_id=?",student.id,school);
        jdbc.update("UPDATE school_delegations SET valid_until=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE) WHERE id=?",delegation);assertEquals(0,get(actor,rosterPath()).body().size());
    }
    @Test void rosterRevalidatesAncestorAndTermWithoutUsingAnotherTermGrant() throws Exception {
        String parent=id();jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,parent_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) SELECT ?,user_id,school_org_id,term_id,NULL,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version FROM school_delegations WHERE id=?",parent,delegation);
        jdbc.update("INSERT INTO school_delegation_permissions VALUES (?,'student_profile:school:read')",parent);
        jdbc.update("UPDATE school_delegations SET parent_id=?,granted_by=? WHERE id=?",parent,staff.id,delegation);
        // Remove the ancestor's own direct access to exercise the descendant as a different principal.
        var child=user(Role.SCHOOL_STAFF,school,"Child reviewer");jdbc.update("UPDATE school_delegations SET user_id=? WHERE id=?",child.id,delegation);
        var actor=login(child);assertEquals(1,get(actor,rosterPath()).body().size());
        jdbc.update("UPDATE school_delegations SET active=false WHERE id=?",parent);assertEquals(0,get(actor,rosterPath()).body().size());
        jdbc.update("UPDATE school_delegations SET active=true WHERE id=?",parent);jdbc.update("UPDATE terms SET status='CLOSED' WHERE id=?",term);assertEquals(0,get(actor,rosterPath()).body().size());
    }
    @Test void preferenceReviewEnforcesVersionCsrfReplayAndNoPlacementSideEffect() throws Exception {
        var actor=login(staff);String path="/school/preferences/"+preference+"/review";
        var body=Map.of("expectedVersion",1,"reasonCode","INTENT_REVIEWED");String key=id();
        assertEquals(403,call(actor,"POST",path,body,key,false).status());
        assertEquals(409,call(actor,"POST",path,Map.of("expectedVersion",2,"reasonCode","INTENT_REVIEWED"),id(),true).status());
        assertEquals(200,call(actor,"POST",path,body,key,true).status());assertEquals(200,call(actor,"POST",path,body,key,true).status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student_preference_history WHERE preference_id=? AND operation='REVIEW'",Integer.class,preference));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM placements WHERE student_user_id=?",Integer.class,student.id));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT state FROM placements WHERE id=?",String.class,placement));
        jdbc.update("UPDATE school_delegations SET active=false WHERE id=?",delegation);assertEquals(404,get(actor,"/school/preferences/"+preference).status());
    }
    @Test void revokedSentInvitationSurvivesReloadAndCurrentHrScopeStillOwnsOnlyItsRecords() throws Exception {
        var actor=login(hr).context(hrGrant);var invite=call(actor,"POST","/t3/mentor-invitations",Map.of("email",newHr.email,"enterpriseOrgId",enterprise,"termId",term,"groupIds",List.of(group),"decisionRef","SYNTHETIC_INVITATION"),id(),true);
        assertEquals(202,invite.status(),invite.body().toString());String inviteId=invite.body().get("invitationId").asString();
        String path="/t3/mentor-invitations/sent/"+inviteId+"?enterpriseOrgId="+enterprise+"&termId="+term;
        assertEquals(409,call(actor,"POST","/t3/mentor-invitations/"+inviteId+"/revoke",Map.of("expectedVersion",9,"reasonCode","INVITE_CHANGED"),id(),true).status());
        String key=id();var body=Map.of("expectedVersion",1,"reasonCode","INVITE_CHANGED");
        assertEquals(200,call(actor,"POST","/t3/mentor-invitations/"+inviteId+"/revoke",body,key,true).status());
        assertEquals(200,call(actor,"POST","/t3/mentor-invitations/"+inviteId+"/revoke",body,key,true).status());
        var fresh=get(login(hr).context(hrGrant),path);assertEquals("REVOKED",fresh.body().get("invitation").get("state").asString());assertFalse(fresh.body().toString().contains("token"));
        String peerGrant=jdbc.queryForObject("SELECT id FROM internship_context_assignments WHERE user_id=?",String.class,peerHr.id);assertEquals(404,get(login(peerHr).context(peerGrant),path).status());
        jdbc.update("UPDATE school_delegations SET active=false WHERE id=?",delegation);assertEquals(403,get(actor,path).status());
    }
    @Test void realBrowserReviewsInvitesAndOperatesSchoolCaseFromFollowUpRoute() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("frontend.e2e"),"Enable frontend.e2e for real Chromium");
        var config=new LinkedHashMap<String,String>();config.put("password",password);config.put("staffEmail",staff.email);config.put("hrEmail",hr.email);config.put("mentorEmail",newHr.email);config.put("hrGrant",hrGrant);config.put("school",school);config.put("term",term);config.put("enterprise",enterprise);config.put("caseId",caseId);config.put("group",group);config.put("preference",preference);config.put("template",template);
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- school-followup-live.spec.ts"):new ProcessBuilder("npm","run","test:e2e","--","school-followup-live.spec.ts");builder.directory(new java.io.File("web-demo"));
        builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);builder.environment().put("E2E_SCHOOL_FOLLOWUP",JSON.writeValueAsString(config));builder.environment().put("E2E_REPORT_FILE","../target/school-followup-browser.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/school-followup-browser.log"));var process=builder.start();
        try {if(!process.waitFor(240,TimeUnit.SECONDS))fail("Browser timed out; see target/school-followup-browser.log");assertEquals(0,process.exitValue(),"See target/school-followup-browser.log");}
        finally {if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        assertEquals("REVIEWED",jdbc.queryForObject("SELECT status FROM student_preferences WHERE preference_id=?",String.class,preference));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mentor_invitation_refs WHERE hr_user_id=? AND state='REVOKED'",Integer.class,hr.id));
        assertEquals("TERMINATED",jdbc.queryForObject("SELECT state FROM internship_cases WHERE id=?",String.class,caseId));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_enterprise_feedback WHERE school_org_id=? AND enterprise_org_id=?",Integer.class,school,enterprise));
    }
}
