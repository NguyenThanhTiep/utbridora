package vn.utbridora.auth;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.onboarding.*;
import vn.utbridora.auth.tokens.*;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.support.ClamdFixture;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;
import static org.mockito.Mockito.when;

/** Isolated real MySQL + ClamAV + SMTP. This class must never run against shared developer Docker. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.t5.placement-delivery.enabled=false",
    "app.onboarding.import-worker-enabled=false","app.onboarding.mail.worker-enabled=false","app.onboarding.activation-events-enabled=false"})
class StudentImportIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static final GenericContainer<?> CLAMD=ClamdFixture.start();
    static final GenericContainer<?> MAIL=new GenericContainer<>("axllent/mailpit:latest").withExposedPorts(1025,8025);
    static final Path ROOT;
    static {try {ROOT=Files.createTempDirectory("student-import-it-");}catch(IOException e) {throw new ExceptionInInitializerError(e);}MYSQL.start();MAIL.start();}
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.mail.host",MAIL::getHost);r.add("spring.mail.port",()->MAIL.getMappedPort(1025));
        r.add("app.files.storage-root",ROOT::toString);r.add("app.files.scan.host",CLAMD::getHost);r.add("app.files.scan.port",()->CLAMD.getMappedPort(3310));
        r.add("app.onboarding.mail.key-id",()->"synthetic-it-key");
        r.add("app.onboarding.mail.keys",()->"{\"synthetic-it-key\":\""+Base64.getEncoder().encodeToString(new byte[32])+"\"}");
    }
    @MockitoBean(name="onboardingClock") Clock clock;
    @Autowired JdbcTemplate jdbc;@Autowired UserRepository users;@Autowired MembershipRepository memberships;@Autowired OrganizationRepository organizations;
    @Autowired PasswordEncoder encoder;@Autowired AuthorizationPolicy core;@Autowired RosterImportQuarantine quarantine;@Autowired StudentImportService imports;
    @Autowired StudentImportProcessor processor;@Autowired StudentActivationResend resend;@Autowired ActivationMailQueue mails;@Autowired ActivationMailCipher cipher;
    @Autowired AccountTokenService accounts;@Autowired EmailService email;@Autowired StudentActivationEvents events;
    @LocalServerPort int port;
    final AtomicReference<Instant> now=new AtomicReference<>();
    final String password="Synthetic-student-import-password-789";
    String school,term,staffId,delegation;Authentication staff;String termCode;
    @BeforeEach void setup() {
        now.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));when(clock.instant()).thenAnswer(i->now.get());when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var org=new Organization();org.type=Organization.Type.UNIVERSITY;org.status=Organization.Status.VERIFIED;org.name="Synthetic import school";school=organizations.saveAndFlush(org).id;
        var admin=user("Synthetic admin");var adminMembership=new Membership();adminMembership.userId=admin.id;adminMembership.role=Role.ADMIN;memberships.saveAndFlush(adminMembership);
        var user=user("Synthetic staff");staffId=user.id;var m=new Membership();m.userId=user.id;m.organizationId=school;m.role=Role.SCHOOL_STAFF;memberships.saveAndFlush(m);
        term=UUID.randomUUID().toString();termCode="IMPORT-"+term.substring(0,8);
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES(?,?,?,'2026-01-01','2028-12-31','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,termCode);
        jdbc.update("INSERT INTO academic_catalogs(term_id,school_org_id,catalog_version) VALUES(?,?,'v1')",term,school);
        jdbc.update("INSERT INTO academic_catalog_entries(term_id,kind,code,name,major_codes,status) VALUES(?,'MAJOR','IT','Synthetic IT',JSON_ARRAY(),'ACTIVE')",term);
        delegation=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES(?,?,?,?,?,'SYNTHETIC_IMPORT_AUTHORITY',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,user.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES(?,'schoolRosterImport:manage')",delegation);
        staff=core.currentSubject(staffId).orElseThrow();
        // Other tests may leave unsent synthetic mail; cancel it without changing the new test's claims.
        jdbc.update("UPDATE activation_mail_outbox SET state='CANCELLED',encrypted_payload=NULL,key_id=NULL,nonce=NULL,tag=NULL WHERE state IN ('QUEUED','RETRY_WAIT','SENDING')");
        jdbc.update("UPDATE mail_dispatch_budget SET attempts=0,lease_owner=NULL,lease_until=NULL,window_start=?",utc(now.get()));
    }
    UserAccount user(String name) {var user=new UserAccount();user.email=UUID.randomUUID()+"@synthetic-import.test";user.displayName=name;user.status=AccountStatus.ACTIVE;user.passwordHash=encoder.encode(password);return users.saveAndFlush(user);}
    String address(String code) {return code.toLowerCase(Locale.ROOT)+"-"+term+"@synthetic-import.test";}
    String csv(String code) {return "studentCode,email,fullName,majorCode,termCode\n"+line(code);}
    String line(String code) {return code+","+address(code)+",Synthetic Student "+code+",IT,"+termCode+"\n";}
    ImportPreviewDto preview(String text,String key) {
        try(var file=quarantine.prepare(new MockMultipartFile("file","students.csv","text/csv",text.getBytes(StandardCharsets.UTF_8)))) {return imports.preview(staff,term,key,file);}
    }
    ImportPreviewDto preview(String text) {return preview(text,UUID.randomUUID().toString());}
    ImportAccepted confirm(ImportPreviewDto p) {
        var selected=imports.listRows(staff,p.job().importJobId(),0,100,"VALID",null,null).content().stream().map(ImportRowDto::rowId).toList();
        return imports.confirm(staff,p.job().importJobId(),UUID.randomUUID().toString(),new ConfirmImport(p.job().jobVersion(),p.job().fileHash(),selected));
    }
    void finish(String id) {var lease=processor.claim(UUID.randomUUID().toString());assertNotNull(lease);assertEquals(id,lease.jobId());int count=0;while(processor.process(lease)) {if(++count>5100) fail("Import did not terminate");}}
    @Test void cleanPreviewHasNoStudentEffectsAndConfirmReplayCrossJobReuseActivationAreAtomic() throws Exception {
        String content=csv("000Ab"),operation=UUID.randomUUID().toString();var p=preview(content,operation);
        assertEquals(p,preview(content,operation));assertEquals(0,count("student_rosters","school_org_id",school));assertEquals(0,count("invitation_claims","school_org_id",school));
        assertEquals(1,p.job().validRows());assertEquals("000Ab",p.rows().content().getFirst().sourceData().studentCode());
        String key=UUID.randomUUID().toString();var request=new ConfirmImport(1L,p.job().fileHash(),List.of(p.rows().content().getFirst().rowId()));
        var accepted=imports.confirm(staff,p.job().importJobId(),key,request);assertEquals(accepted,imports.confirm(staff,p.job().importJobId(),key,request));
        finish(p.job().importJobId());var row=imports.listRows(staff,p.job().importJobId(),0,25,null,null,null).content().getFirst();
        assertEquals("PROVISIONED",row.rowState());assertEquals("PENDING",row.activationState());assertEquals(0,count("users","email",address("000Ab")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles p JOIN student_rosters r ON r.user_id=p.user_id WHERE r.school_org_id=?",Integer.class,school));
        var again=preview(content);confirm(again);finish(again.job().importJobId());
        assertEquals(row.provisioningEffectId(),imports.listRows(staff,again.job().importJobId(),0,25,null,null,null).content().getFirst().provisioningEffectId());
        assertEquals(1,count("student_rosters","school_org_id",school));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM activation_mail_outbox m JOIN invitation_claims c ON c.id=m.claim_id WHERE c.school_org_id=?",Integer.class,school));
        var mail=mails.claim(UUID.randomUUID().toString());assertNotNull(mail);String raw=cipher.decrypt(mail.payload(),mail.claimId(),mail.generation(),mail.id());
        email.activation(mail.email(),raw,mail.messageId());mails.complete(mail,null,false);assertMailbox(address("000Ab"),raw,mail.messageId());
        String acceptKey=UUID.randomUUID().toString();assertEquals(accounts.acceptInvitationV11(raw,null,password,acceptKey),accounts.acceptInvitationV11(raw,null,password,acceptKey));
        assertCode("TOKEN_ALREADY_USED",()->accounts.acceptInvitationV11(raw,null,password,UUID.randomUUID().toString()));
        var active=imports.listRows(staff,p.job().importJobId(),0,25,null,null,null).content().getFirst();assertEquals("ACTIVE",active.activationState());assertNotNull(active.userId());
        assertEquals(1,count("student_profiles","user_id",active.userId()));assertEquals(1,count("student_term_eligibilities","user_id",active.userId()));
        assertFalse(jdbc.queryForObject("SELECT school_approved FROM student_term_eligibilities WHERE user_id=?",Boolean.class,active.userId()));
        assertTrue(events.dispatchOne());assertEquals(1,count("student_activation_inbox","aggregate_id",active.userId()));
        assertCode("ACCOUNT_ALREADY_ACTIVE",()->resend.resend(staff,p.job().importJobId(),row.rowId(),UUID.randomUUID().toString(),new ResendActivation(1L,1L)));
        assertEquals("COMPLETED",imports.get(staff,p.job().importJobId()).jobState());
    }
    @Test void resendVersionCooldownExpiryRotationAndStaleLinkHaveDeterministicGuards() {
        var p=preview(csv("0002"));confirm(p);finish(p.job().importJobId());
        var row=imports.listRows(staff,p.job().importJobId(),0,25,null,null,null).content().getFirst();var original=mails.claim(UUID.randomUUID().toString());assertNotNull(original);
        String raw=cipher.decrypt(original.payload(),original.claimId(),original.generation(),original.id());
        assertCode("MAIL_SEND_IN_PROGRESS",()->resend.resend(staff,p.job().importJobId(),row.rowId(),UUID.randomUUID().toString(),new ResendActivation(row.rowVersion(),row.invitationVersion())));
        mails.complete(original,"SMTP_PERMANENT",true);assertNull(jdbc.queryForObject("SELECT encrypted_payload FROM activation_mail_outbox WHERE id=?",byte[].class,original.id()));
        var limited=assertCode("RESEND_RATE_LIMITED",()->resend.resend(staff,p.job().importJobId(),row.rowId(),UUID.randomUUID().toString(),new ResendActivation(row.rowVersion(),row.invitationVersion())));assertEquals(429,limited.status);assertEquals(300,limited.retryAfterSeconds);
        now.set(now.get().plusSeconds(300));String key=UUID.randomUUID().toString();var request=new ResendActivation(row.rowVersion(),row.invitationVersion());
        var issued=resend.resend(staff,p.job().importJobId(),row.rowId(),key,request);assertEquals(issued,resend.resend(staff,p.job().importJobId(),row.rowId(),key,request));
        assertCode("STATE_CONFLICT",()->resend.resend(staff,p.job().importJobId(),row.rowId(),UUID.randomUUID().toString(),request));
        assertCode("TOKEN_REVOKED",()->accounts.acceptInvitationV11(raw,"Name",password,UUID.randomUUID().toString()));
        now.set(now.get().plusSeconds(172800));assertNull(mails.claim(UUID.randomUUID().toString()),"Expired backlog rotates first, never sends old link");
        assertEquals(3,jdbc.queryForObject("SELECT generation FROM invitation_claims WHERE id=?",Integer.class,original.claimId()));
        assertEquals("COMPLETED",imports.get(staff,p.job().importJobId()).jobState());
    }
    @Test void invalidRowsPreviewExpiryAndReferenceChangesDoNotProvision() {
        var invalid=preview("studentCode,email,fullName,majorCode,termCode\n,invalid,Name,MISSING,WRONG\n");assertEquals(1,invalid.job().invalidRows());
        assertCode("INVALID_ROW_SELECTION",()->imports.confirm(staff,invalid.job().importJobId(),UUID.randomUUID().toString(),new ConfirmImport(1L,invalid.job().fileHash(),List.of(invalid.rows().content().getFirst().rowId()))));
        var p=preview(csv("expire"));now.set(now.get().plusSeconds(1800));assertCode("PREVIEW_EXPIRED",()->confirm(p));
        now.set(Instant.now());var stale=preview(csv("stale"));jdbc.update("UPDATE academic_catalogs SET catalog_version='v2' WHERE term_id=?",term);assertCode("PREVIEW_STALE",()->confirm(stale));
        assertEquals(0,count("student_rosters","school_org_id",school));assertEquals(0,count("invitation_claims","school_org_id",school));
    }
    @Test void selectedThousandRowsRecoverOnlyUnfinishedAndAuthorityMustBeCurrent() {
        var text=new StringBuilder("studentCode,email,fullName,majorCode,termCode\n");for(int i=0;i<1000;i++) text.append(line("REC"+i));var p=preview(text.toString());
        var ids=new ArrayList<String>();for(int page=0;page<10;page++) imports.listRows(staff,p.job().importJobId(),page,100,"VALID",null,null).content().forEach(r->ids.add(r.rowId()));
        imports.confirm(staff,p.job().importJobId(),UUID.randomUUID().toString(),new ConfirmImport(1L,p.job().fileHash(),ids));
        var lease=processor.claim(UUID.randomUUID().toString());assertNotNull(lease);for(int i=0;i<600;i++) assertTrue(processor.process(lease));
        assertEquals(600,imports.get(staff,p.job().importJobId()).successRows());now.set(now.get().plusSeconds(121));assertNull(processor.claim(UUID.randomUUID().toString()));
        assertFalse(processor.process(lease));var failed=imports.get(staff,p.job().importJobId());assertEquals("FAILED",failed.jobState());
        imports.resume(staff,p.job().importJobId(),UUID.randomUUID().toString(),new ResumeImport(failed.jobVersion()));
        var next=processor.claim(UUID.randomUUID().toString());assertNotNull(next);jdbc.update("UPDATE school_delegations SET active=false,version=version+1 WHERE id=?",delegation);
        assertFalse(processor.process(next));assertEquals("AUTHORITY_REVOKED",jdbc.queryForObject("SELECT last_error_code FROM student_import_jobs WHERE id=?",String.class,p.job().importJobId()));
        var replacement=user("Synthetic recovery staff");var membership=new Membership();membership.userId=replacement.id;membership.organizationId=school;membership.role=Role.SCHOOL_STAFF;memberships.saveAndFlush(membership);
        String replacementGrant=UUID.randomUUID().toString(),grantor=jdbc.queryForObject("SELECT granted_by FROM school_delegations WHERE id=?",String.class,delegation);
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES(?,?,?,?,?,'SYNTHETIC_RECOVERY_AUTHORITY',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",replacementGrant,replacement.id,school,term,grantor);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES(?,'schoolRosterImport:manage')",replacementGrant);
        var recovery=core.currentSubject(replacement.id).orElseThrow();failed=imports.get(recovery,p.job().importJobId());imports.resume(recovery,p.job().importJobId(),UUID.randomUUID().toString(),new ResumeImport(failed.jobVersion()));finish(p.job().importJobId());
        assertEquals(1000,imports.get(recovery,p.job().importJobId()).successRows());assertEquals(replacement.id,jdbc.queryForObject("SELECT initiated_by FROM student_import_jobs WHERE id=?",String.class,p.job().importJobId()));assertEquals(1000,count("student_rosters","school_org_id",school));
        assertEquals(1000,jdbc.queryForObject("SELECT COUNT(*) FROM student_import_effects WHERE school_org_id=?",Integer.class,school));
    }
    @Test void fiveThousandRowsWithTwoHundredInvalidCompleteWithExactly4800Successes() {
        var text=new StringBuilder("studentCode,email,fullName,majorCode,termCode\n");for(int i=0;i<5000;i++) text.append(i<200?",bad,Name,IT,"+termCode+"\n":line("BULK"+i));
        var p=preview(text.toString());assertEquals(5000,p.job().totalRows());assertEquals(200,p.job().invalidRows());assertEquals(4800,p.job().validRows());
        var ids=new ArrayList<String>();for(int page=0;page<48;page++) imports.listRows(staff,p.job().importJobId(),page,100,"VALID",null,null).content().forEach(r->ids.add(r.rowId()));
        imports.confirm(staff,p.job().importJobId(),UUID.randomUUID().toString(),new ConfirmImport(1L,p.job().fileHash(),ids));finish(p.job().importJobId());
        var done=imports.get(staff,p.job().importJobId());assertEquals("COMPLETED",done.jobState());assertEquals(4800,done.successRows());assertEquals(4800,done.processedRows());assertEquals(200,done.invalidRows());
        assertEquals(4800,count("student_rosters","school_org_id",school));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email LIKE ?",Integer.class,"%-"+term+"@synthetic-import.test"));
    }
    @Test void httpScopeCsrfClosedBodiesAndWireNeverExposeTokenOrCiphertext() throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();String csrf=csrf(client);
        var account=users.findById(staffId).orElseThrow();assertEquals(200,http(client,"POST","/auth/login",JSON.writeValueAsString(Map.of("email",account.email,"password",password)),csrf,null).statusCode());csrf=csrf(client);
        var p=preview(csv("HTTP"));String path="/school/student-imports/"+p.job().importJobId();
        var response=http(client,"GET",path,null,null,null);assertEquals(200,response.statusCode(),response.body());assertFalse(response.body().contains("encrypted_payload"));assertFalse(response.body().contains("tokenHash"));
        var confirm=new ConfirmImport(1L,p.job().fileHash(),List.of(p.rows().content().getFirst().rowId()));
        assertEquals(403,http(client,"POST",path+"/confirm",JSON.writeValueAsString(confirm),null,UUID.randomUUID().toString()).statusCode());
        assertEquals(400,http(client,"POST",path+"/confirm","{\"expectedJobVersion\":1}",csrf,UUID.randomUUID().toString()).statusCode());
        assertEquals(404,http(client,"GET","/school/student-imports/"+UUID.randomUUID(),null,null,null).statusCode());
        var otherUser=user("Unassigned school staff");var m=new Membership();m.userId=otherUser.id;m.organizationId=school;m.role=Role.SCHOOL_STAFF;memberships.saveAndFlush(m);
        assertCode("RESOURCE_NOT_FOUND",()->imports.get(core.currentSubject(otherUser.id).orElseThrow(),p.job().importJobId()));
        assertEquals(400,http(client,"POST","/auth/invitations/accept","{invalid",csrf,UUID.randomUUID().toString(),true).statusCode());
        assertEquals(422,http(client,"POST","/auth/invitations/accept",JSON.writeValueAsString(Map.of("token","unknown","newPassword",password)),csrf,UUID.randomUUID().toString(),true).statusCode());
    }
    @Test void twoResendsAndTwoAcceptsHaveOneWinningGenerationAndOneIdentity() throws Exception {
        var p=preview(csv("RACE"));confirm(p);finish(p.job().importJobId());
        var row=imports.listRows(staff,p.job().importJobId(),0,25,null,null,null).content().getFirst();now.set(now.get().plusSeconds(300));
        var pool=Executors.newFixedThreadPool(2);try {
            var start=new CountDownLatch(1);var results=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) results.add(pool.submit(()->{start.await();try {resend.resend(staff,p.job().importJobId(),row.rowId(),UUID.randomUUID().toString(),new ResendActivation(row.rowVersion(),row.invitationVersion()));return "ACCEPTED";}catch(OperationFailure failure) {return failure.code;}}));
            start.countDown();var actual=new ArrayList<String>();for(var result:results)actual.add(result.get(30,TimeUnit.SECONDS));Collections.sort(actual);assertEquals(List.of("ACCEPTED","STATE_CONFLICT"),actual);
            var mail=mails.claim(UUID.randomUUID().toString());assertNotNull(mail);String raw=cipher.decrypt(mail.payload(),mail.claimId(),mail.generation(),mail.id());
            mails.complete(mail,"SMTP_PERMANENT",true); // Explicit transport fault fixture; real SMTP is proved separately.
            var activate=new CountDownLatch(1);results.clear();
            for(int i=0;i<2;i++) results.add(pool.submit(()->{activate.await();try {accounts.acceptInvitationV11(raw,"Synthetic race student",password,UUID.randomUUID().toString());return "ACTIVATED";}catch(OperationFailure failure) {return failure.code;}}));
            activate.countDown();actual.clear();for(var result:results)actual.add(result.get(30,TimeUnit.SECONDS));Collections.sort(actual);assertEquals(List.of("ACTIVATED","TOKEN_ALREADY_USED"),actual);
            assertEquals(1,count("users","email",address("RACE")));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles p JOIN users u ON u.id=p.user_id WHERE u.email=?",Integer.class,address("RACE")));
        } finally {pool.shutdownNow();}
    }
    @Test void mailRetryKeepsMessageAndLinkAndPurgesTransportAfterFourthFailure() {
        var p=preview(csv("RETRY"));confirm(p);finish(p.job().importJobId());String id=null,message=null;int[] backoff={5,30,120};
        for(int attempt=1;attempt<=4;attempt++) {
            var mail=mails.claim(UUID.randomUUID().toString());assertNotNull(mail);if(id==null) {id=mail.id();message=mail.messageId();}
            assertEquals(id,mail.id());assertEquals(message,mail.messageId());assertEquals(attempt,mail.attempt());
            mails.complete(mail,"SMTP_TEMPORARY",false);mails.complete(mail,"SMTP_TEMPORARY",false);
            assertEquals(attempt,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE resource_id=? AND action='ACTIVATION_EMAIL_FAILED'",Integer.class,id));
            if(attempt<4) {assertNull(mails.claim(UUID.randomUUID().toString()));now.set(now.get().plusSeconds(backoff[attempt-1]));}
        }
        assertEquals("FAILED",jdbc.queryForObject("SELECT state FROM activation_mail_outbox WHERE id=?",String.class,id));assertNull(jdbc.queryForObject("SELECT encrypted_payload FROM activation_mail_outbox WHERE id=?",byte[].class,id));
        assertEquals("COMPLETED",imports.get(staff,p.job().importJobId()).jobState());
    }
    @Test void scannerInfrastructureFailureCheckpointCannotBeResumedOrSelected() {
        imports.failedPreview(staff,term,"a".repeat(64),"SCANNER_UNAVAILABLE");String id=jdbc.queryForObject("SELECT id FROM student_import_jobs WHERE school_org_id=?",String.class,school);
        var failed=imports.get(staff,id);assertEquals("FAILED",failed.jobState());assertEquals(0,failed.totalRows());assertEquals(0,failed.selectedRows());
        assertCode("STATE_CONFLICT",()->imports.resume(staff,id,UUID.randomUUID().toString(),new ResumeImport(failed.jobVersion())));
        assertEquals(0,count("student_rosters","school_org_id",school));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="frontend.e2e",matches="true")
    void browserUsesRealPreviewConfirmResendAndActivationApis() throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npx.cmd playwright test tests/student-import-runtime.spec.ts"):new ProcessBuilder("npx","playwright","test","tests/student-import-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));var env=builder.environment();env.put("API_PROXY_TARGET","http://localhost:"+port);env.put("E2E_PORT","5178");
        env.put("E2E_REPORT_FILE","../target/student-import-runtime-browser.xml");env.put("IMPORT_E2E","true");env.put("IMPORT_STAFF_EMAIL",users.findById(staffId).orElseThrow().email);
        env.put("IMPORT_PASSWORD",password);env.put("IMPORT_TERM",term);env.put("IMPORT_TERM_CODE",termCode);env.put("IMPORT_STUDENT_EMAIL",address("000UI"));env.put("IMPORT_BROWSER_URL","http://127.0.0.1:5178");env.put("IMPORT_MAILPIT","http://"+MAIL.getHost()+":"+MAIL.getMappedPort(8025));
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/student-import-runtime-e2e.log"));var process=builder.start();long deadline=System.nanoTime()+Duration.ofSeconds(180).toNanos();boolean advanced=false;
        try {
            while(process.isAlive() && System.nanoTime()<deadline) {
                if(jdbc.queryForObject("SELECT COUNT(*) FROM student_import_jobs WHERE school_org_id=? AND state='PROCESSING'",Integer.class,school)>0) {
                    var lease=processor.claim(UUID.randomUUID().toString());if(lease!=null) while(processor.process(lease)) { }
                }
                if(!advanced && jdbc.queryForObject("SELECT COUNT(*) FROM student_import_jobs WHERE school_org_id=? AND state='COMPLETED'",Integer.class,school)>0) {now.set(now.get().plusSeconds(300));advanced=true;}
                if(jdbc.queryForObject("SELECT COUNT(*) FROM invitation_claims WHERE school_org_id=? AND generation=2 AND claim_state='PENDING'",Integer.class,school)>0) {
                    var mail=mails.claim(UUID.randomUUID().toString());if(mail!=null) {String raw=cipher.decrypt(mail.payload(),mail.claimId(),mail.generation(),mail.id());email.activation(mail.email(),raw,mail.messageId());mails.complete(mail,null,false);}
                }
                Thread.sleep(100);
            }
            if(process.isAlive()) {process.destroyForcibly();fail("Import browser timed out; see isolated runtime log");}
            assertEquals(0,process.exitValue(),"See target/student-import-runtime-e2e.log");
            assertEquals(1,count("student_rosters","school_org_id",school));assertEquals(1,count("users","email",address("000UI")));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM student_profiles p JOIN student_rosters r ON r.user_id=p.user_id WHERE r.school_org_id=?",Integer.class,school));
        } finally {if(process.isAlive()) process.destroyForcibly();}
    }
    int count(String table,String column,String value) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Integer.class,value);}
    OperationFailure assertCode(String code,Runnable action) {var failure=assertThrows(OperationFailure.class,action::run);assertEquals(code,failure.code);return failure;}
    HttpResponse<String> http(HttpClient c,String method,String path,String body,String csrf,String key) throws Exception {return http(c,method,path,body,csrf,key,false);}
    HttpResponse<String> http(HttpClient c,String method,String path,String body,String csrf,String key,boolean v11) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
        if(csrf!=null)b.header("X-CSRF-TOKEN",csrf);if(key!=null)b.header("Idempotency-Key",key);if(v11)b.header("X-UTB-Contract-Version","1.1");
        return c.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    String csrf(HttpClient client) throws Exception {return JSON.readTree(http(client,"GET","/auth/csrf",null,null,null).body()).get("token").asString();}
    void assertMailbox(String recipient,String raw,String expectedMessage) throws Exception {
        var c=HttpClient.newHttpClient();String base="http://"+MAIL.getHost()+":"+MAIL.getMappedPort(8025);
        var messages=JSON.readTree(c.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("messages");
        boolean found=false;for(var summary:messages) {
            var value=JSON.readTree(c.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/message/"+summary.get("ID").asString())).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            if(value.get("Text").asString().contains("#token="+raw)) {assertTrue(value.toString().contains(recipient));assertTrue(value.toString().contains(expectedMessage));found=true;}
        }assertTrue(found,"Real SMTP must deliver the current activation link to dedicated Mailpit");
    }
}
