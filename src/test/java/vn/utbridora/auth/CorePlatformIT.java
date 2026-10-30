package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.core.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.tokens.*;
import vn.utbridora.auth.user.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP cookies, real MySQL 8.4, real Flyway and JDBC sessions. No security filters disabled. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.allow-org-verification=false"})
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(TestAuthorizationEndpoints.class)
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CorePlatformIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("core_test").withUsername("core_test").withPassword(UUID.randomUUID().toString());
    static { MYSQL.start(); } // PER_CLASS Spring injection happens before JUnit's @BeforeAll callbacks.
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired MembershipRepository memberships;
    @Autowired TokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired AuditService audit;
    @Autowired AccountService accounts;
    @Autowired AccountLifecycleService lifecycle;
    @Autowired MembershipService membershipService;
    @Autowired AuthorizationPolicy policy;
    @Autowired ProfileVisibilityPort visibility;
    @Autowired TransactionTemplate transactions;
    @Autowired MockMvc mvc;
    @MockitoBean EmailService email;
    final String password="Test-"+UUID.randomUUID();
    String passwordHash;
    UserAccount admin,otherAdmin,student,enterprise;
    Organization university,orgA,orgB;

    @BeforeAll void hash() { passwordHash=encoder.encode(password); }
    @BeforeEach void seed() {
        SecurityContextHolder.clearContext();
        for(String table:List.of("SPRING_SESSION_ATTRIBUTES","SPRING_SESSION","audit_logs","account_status_history",
                "one_time_tokens","files","memberships","users","organizations")) jdbc.update("DELETE FROM "+table);
        university=organization(Organization.Type.UNIVERSITY);
        orgA=organization(Organization.Type.ENTERPRISE);orgB=organization(Organization.Type.ENTERPRISE);
        admin=user(Role.ADMIN,null);otherAdmin=user(Role.ADMIN,null);
        student=user(Role.STUDENT,university.id);enterprise=user(Role.ENTERPRISE,orgA.id);
    }
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }
    Organization organization(Organization.Type type) {
        Organization org=new Organization();org.name="Tổ chức thử nghiệm "+UUID.randomUUID();
        org.type=type;org.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(org);
    }
    UserAccount user(Role role,String organizationId) {
        UserAccount user=new UserAccount();user.email=UUID.randomUUID()+"@example.test";
        user.displayName="Người dùng kiểm thử";user.passwordHash=passwordHash;user.status=AccountStatus.ACTIVE;
        users.saveAndFlush(user);Membership m=new Membership();m.userId=user.id;m.role=role;m.organizationId=organizationId;
        memberships.saveAndFlush(m);return user;
    }
    String token(UserAccount user,OneTimeToken.Purpose purpose,Instant expiry) {
        String raw=TokenCodec.generate();OneTimeToken token=new OneTimeToken();
        token.tokenHash=TokenCodec.hash(raw);token.purpose=purpose;token.expiresAt=expiry;
        token.email=user==null?UUID.randomUUID()+"@example.test":user.email;
        token.userId=user==null?null:user.id;token.inviteRole=Role.STUDENT;token.organizationId=university.id;
        tokens.saveAndFlush(token);return raw;
    }
    void as(UserAccount user) {
        var p=new AppPrincipal(user.id,user.email,null,user.securityVersion,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
    class Browser {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        String csrf;
        HttpResponse<String> send(String method,String path,Object body,boolean withCsrf) throws Exception {
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
            if(withCsrf && csrf!=null) request.header("X-CSRF-TOKEN",csrf);
            request.header("Content-Type","application/json");
            return client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(new tools.jackson.databind.ObjectMapper().writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
        }
        void csrf() throws Exception {var res=send("GET","/api/v1/auth/csrf",null,false);assertEquals(200,res.statusCode(),res.body());csrf=JsonPath.read(res.body(),"$.token");}
        HttpResponse<String> login(UserAccount user) throws Exception {return login(user,password);}
        HttpResponse<String> login(UserAccount user,String secret) throws Exception {
            csrf();return send("POST","/api/v1/auth/login",Map.of("email",user.email,"password",secret),true);
        }
        String session() {return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("SESSION")).map(HttpCookie::getValue).findFirst().orElse("");}
        HttpResponse<String> me() throws Exception {return send("GET","/api/v1/auth/me",null,false);}
    }
    Browser signedIn(UserAccount user) throws Exception {Browser b=new Browser();var res=b.login(user);assertEquals(200,res.statusCode(),res.body());b.csrf();return b;}

    @Test void anonymousPrivateApiIs401AndMockMvcCsrfIs403() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
    }
    @Test void loginRotatesSessionStoresJdbcContextAndReturnsSafeMe() throws Exception {
        Browser b=new Browser();b.csrf();String before=b.session();
        assertEquals(200,b.send("POST","/api/v1/auth/login",Map.of("email",student.email.toUpperCase(Locale.ROOT),"password",password),true).statusCode());
        assertNotEquals(before,b.session());assertFalse(before.isEmpty());
        var me=b.me();assertEquals(200,me.statusCode());assertEquals(student.id,JsonPath.read(me.body(),"$.userId"));
        assertFalse(me.body().contains("passwordHash"));assertFalse(me.body().contains("securityVersion"));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME='SPRING_SECURITY_CONTEXT'",Integer.class)>0);
        assertEquals("STUDENT",JsonPath.read(me.body(),"$.memberships[0].role"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(Role.class)
    void allFourRolesUseTheSameAuthenticationAndPermissionApi(Role role) throws Exception {
        UserAccount account=user(role,role==Role.ADMIN?null:role==Role.ENTERPRISE?orgA.id:university.id);
        Browser browser=signedIn(account);var me=browser.me();
        assertEquals(role.name(),JsonPath.read(me.body(),"$.memberships[0].role"));
        var permissions=browser.send("GET","/api/v1/auth/me/permissions",null,false);
        assertEquals(200,permissions.statusCode());assertEquals(role.name(),JsonPath.read(permissions.body(),"$[0].role"));
    }
    @Test void failedLoginUnknownLockedAndSuspendedReturnSameResponse() throws Exception {
        Browser b=new Browser();var wrong=b.login(student,"invalid-password");
        assertEquals(wrong.body(),new Browser().login(student,"x".repeat(73)).body());
        UserAccount unknown=new UserAccount();unknown.email=UUID.randomUUID()+"@example.test";
        assertEquals(wrong.body(),new Browser().login(unknown).body());
        jdbc.update("UPDATE users SET status='SUSPENDED' WHERE id=?",student.id);
        assertEquals(wrong.body(),new Browser().login(student).body());
        jdbc.update("UPDATE users SET status='ACTIVE',locked_until=DATE_ADD(UTC_TIMESTAMP(),INTERVAL 15 MINUTE) WHERE id=?",student.id);
        var locked=new Browser().login(student);assertEquals(401,locked.statusCode());assertEquals(wrong.body(),locked.body());
        assertEquals(401,b.me().statusCode());
    }
    @Test void fiveFailuresTemporarilyLockCredentialWithoutChangingStatus() throws Exception {
        Browser b=new Browser();for(int i=0;i<5;i++)assertEquals(401,b.login(student,"invalid-password").statusCode());
        UserAccount locked=users.findById(student.id).orElseThrow();assertNotNull(locked.lockedUntil);
        assertEquals(AccountStatus.ACTIVE,locked.status);assertEquals(401,b.login(student).statusCode());
        jdbc.update("UPDATE users SET locked_until=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 MINUTE) WHERE id=?",student.id);
        assertEquals(200,b.login(student).statusCode());
    }
    @Test void csrfTokenBeforeLoginIsRejectedAfterAuthentication() throws Exception {
        Browser b=new Browser();assertEquals(200,b.login(student).statusCode());
        assertEquals(403,b.send("POST","/api/v1/auth/logout",null,true).statusCode());
        b.csrf();assertEquals(200,b.send("POST","/api/v1/auth/logout",null,true).statusCode());
    }
    @Test void logoutInvalidatesSessionAndMissingCsrfCannotLogout() throws Exception {
        Browser b=signedIn(student);String cookie=b.session();
        assertEquals(403,b.send("POST","/api/v1/auth/logout",null,false).statusCode());
        assertEquals(200,b.me().statusCode());assertEquals(200,b.send("POST","/api/v1/auth/logout",null,true).statusCode());
        assertEquals(401,b.me().statusCode());assertEquals(401,replay(cookie,"/api/v1/auth/me").statusCode());
    }
    @Test void expiredJdbcSessionCannotCallMe() throws Exception {
        Browser b=signedIn(student);jdbc.update("UPDATE SPRING_SESSION SET EXPIRY_TIME=1, LAST_ACCESS_TIME=1");
        assertEquals(401,b.me().statusCode());
    }
    @Test void roleAndOrganizationIdorAreDeniedWithCorrelatedAudit() throws Exception {
        Browser b=signedIn(enterprise);
        var own=b.send("GET","/api/v1/demo/organizations/"+orgA.id+"/private",null,false);assertEquals(200,own.statusCode());
        var denied=b.send("GET","/api/v1/demo/organizations/"+orgB.id+"/private",null,false);assertEquals(403,denied.statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='ACCESS_DENIED' AND request_id=?",Integer.class,denied.headers().firstValue("X-Request-ID").orElseThrow()));
        assertEquals(403,b.send("POST","/api/v1/admin/memberships",Map.of("userId",enterprise.id,"role","ADMIN"),true).statusCode());
        assertEquals(403,b.send("GET","/api/v1/admin/audit-logs",null,false).statusCode());
        assertEquals(403,signedIn(student).send("PATCH","/api/v1/admin/users/"+enterprise.id+"/status",Map.of("status","SUSPENDED","reasonCode","SECURITY_REVIEW"),true).statusCode());
    }
    @Test void changedOrganizationStateAndMultipleMembershipsAreRechecked() throws Exception {
        Membership m=new Membership();m.userId=enterprise.id;m.organizationId=orgB.id;m.role=Role.ENTERPRISE;memberships.saveAndFlush(m);
        Browser b=signedIn(enterprise);
        assertEquals(200,b.send("GET","/api/v1/demo/organizations/"+orgB.id+"/private",null,false).statusCode());
        for(String status:List.of("PENDING","SUSPENDED")) {
            jdbc.update("UPDATE organizations SET status=? WHERE id=?",status,orgB.id);
            assertEquals(403,b.send("GET","/api/v1/demo/organizations/"+orgB.id+"/private",null,false).statusCode());
            assertEquals(200,b.send("GET","/api/v1/demo/organizations/"+orgA.id+"/private",null,false).statusCode());
        }
    }
    @Test void noMembershipAndDefaultProfileSharingAreDeniedIncludingAdmin() throws Exception {
        as(enterprise);assertFalse(policy.canReadStudentProfile(SecurityContextHolder.getContext().getAuthentication(),student.id,orgA.id));
        assertInstanceOf(DenyAllProfileVisibility.class,visibility);
        as(admin);assertFalse(policy.canReadStudentProfile(SecurityContextHolder.getContext().getAuthentication(),student.id,orgA.id));
        jdbc.update("UPDATE memberships SET active=0 WHERE user_id=?",enterprise.id);
        Browser b=signedIn(enterprise);assertEquals(403,b.send("GET","/api/v1/demo/organizations/"+orgA.id+"/private",null,false).statusCode());
    }
    @Test void organizationTamperingInBodyAndAdminBusinessAccessAreDenied() throws Exception {
        Browser enterpriseBrowser=signedIn(enterprise);
        String path="/api/v1/demo/test/organizations/"+orgA.id+"/command";
        assertEquals(200,enterpriseBrowser.send("POST",path,Map.of("organizationId",orgA.id),true).statusCode());
        assertEquals(403,enterpriseBrowser.send("POST",path,Map.of("organizationId",orgB.id),true).statusCode());
        assertEquals(403,enterpriseBrowser.send("POST","/api/v1/demo/test/organizations/"+orgB.id+"/command",Map.of("organizationId",orgB.id),true).statusCode());
        assertEquals(403,signedIn(admin).send("POST",path,Map.of("organizationId",orgA.id),true).statusCode());
        assertEquals(403,enterpriseBrowser.send("GET","/api/v1/demo/test/profiles/"+student.id+"?organizationId="+orgA.id,null,false).statusCode());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "PENDING_ACTIVATION,PENDING_ACTIVATION,true", "PENDING_ACTIVATION,ACTIVE,false",
        "PENDING_ACTIVATION,SUSPENDED,true", "PENDING_ACTIVATION,DEACTIVATED,true",
        "ACTIVE,PENDING_ACTIVATION,false", "ACTIVE,ACTIVE,true", "ACTIVE,SUSPENDED,true", "ACTIVE,DEACTIVATED,true",
        "SUSPENDED,PENDING_ACTIVATION,false", "SUSPENDED,ACTIVE,true", "SUSPENDED,SUSPENDED,true", "SUSPENDED,DEACTIVATED,true",
        "DEACTIVATED,PENDING_ACTIVATION,false", "DEACTIVATED,ACTIVE,false", "DEACTIVATED,SUSPENDED,false", "DEACTIVATED,DEACTIVATED,true"
    })
    void lifecycleTransitionMatrixIsEnforcedInDatabase(AccountStatus from,AccountStatus to,boolean allowed) {
        jdbc.update("UPDATE users SET status=? WHERE id=?",from.name(),student.id);as(admin);
        if(allowed) lifecycle.transition(admin.id,student.id,to,"MANUAL_REVIEW");
        else assertThrows(IllegalArgumentException.class,()->lifecycle.transition(admin.id,student.id,to,"MANUAL_REVIEW"));
        boolean changed=allowed && from!=to;
        UserAccount result=users.findById(student.id).orElseThrow();assertEquals(changed?to:from,result.status);
        assertEquals(changed?1:0,result.securityVersion);
        assertEquals(changed?1:0,jdbc.queryForObject("SELECT COUNT(*) FROM account_status_history WHERE user_id=?",Integer.class,student.id));
    }
    @Test void grantIsIdempotentWrongTypeIsRejectedAndRevokeInvalidatesSessions() throws Exception {
        Browser a=signedIn(admin),b=signedIn(enterprise);
        var body=Map.of("userId",enterprise.id,"role","ENTERPRISE","organizationId",orgA.id);
        long version=users.findById(enterprise.id).orElseThrow().securityVersion;
        assertEquals(200,a.send("POST","/api/v1/admin/memberships",body,true).statusCode());
        assertEquals(version,users.findById(enterprise.id).orElseThrow().securityVersion);
        assertEquals(400,a.send("POST","/api/v1/admin/memberships",Map.of("userId",enterprise.id,"role","ENTERPRISE","organizationId",university.id),true).statusCode());
        assertEquals(200,a.send("DELETE","/api/v1/admin/memberships",body,true).statusCode());assertEquals(401,b.me().statusCode());
        assertEquals(200,a.send("POST","/api/v1/admin/memberships",body,true).statusCode());
        assertEquals(version+2,users.findById(enterprise.id).orElseThrow().securityVersion);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=? AND role='ENTERPRISE'",Integer.class,enterprise.id));
    }
    @Test void statusHistoryAndAuditCommitTogetherAndOldSessionDoesNotReturn() throws Exception {
        Browser a=signedIn(admin),b=signedIn(student);
        assertEquals(200,a.send("PATCH","/api/v1/admin/users/"+student.id+"/status",Map.of("status","SUSPENDED","reasonCode","SECURITY_REVIEW"),true).statusCode());
        assertEquals(401,b.me().statusCode());assertEquals(401,new Browser().login(student).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM account_status_history WHERE user_id=? AND actor_id=? AND reason_code='SECURITY_REVIEW'",Integer.class,student.id,admin.id));
        assertEquals(200,a.send("PATCH","/api/v1/admin/users/"+student.id+"/status",Map.of("status","ACTIVE","reasonCode","REVIEW_COMPLETE"),true).statusCode());
        assertEquals(401,b.me().statusCode());assertEquals(200,new Browser().login(student).statusCode());
        assertEquals(200,a.send("GET","/api/v1/admin/users/"+student.id+"/status-history",null,false).statusCode());
    }
    @Test void invalidTransitionsSelfDisableAndSelfAdminRevokeAreRejected() throws Exception {
        Browser a=signedIn(admin);
        for(String from:List.of("PENDING_ACTIVATION","DEACTIVATED")) {
            jdbc.update("UPDATE users SET status=? WHERE id=?",from,student.id);
            assertEquals(400,a.send("PATCH","/api/v1/admin/users/"+student.id+"/status",Map.of("status","ACTIVE","reasonCode","MANUAL_REVIEW"),true).statusCode());
        }
        assertEquals(400,a.send("PATCH","/api/v1/admin/users/"+admin.id+"/status",Map.of("status","DEACTIVATED","reasonCode","MANUAL_REVIEW"),true).statusCode());
        assertEquals(400,a.send("DELETE","/api/v1/admin/memberships",Map.of("userId",admin.id,"role","ADMIN"),true).statusCode());
    }
    @Test void explicitSessionRevocationKeepsAccountActive() throws Exception {
        Browser a=signedIn(admin),b=signedIn(student);
        assertEquals(200,a.send("PATCH","/api/v1/admin/users/"+student.id+"/revoke-sessions",null,true).statusCode());
        assertEquals(401,b.me().statusCode());assertEquals(AccountStatus.ACTIVE,users.findById(student.id).orElseThrow().status);
    }
    @Test void passwordChangeRequiresCurrentSecretAndRevokesOtherSessions() throws Exception {
        Browser a=signedIn(student),b=signedIn(student);String next="Next-"+UUID.randomUUID();
        assertEquals(400,a.send("POST","/api/v1/auth/password/change",Map.of("currentPassword","wrong","newPassword",next),true).statusCode());
        assertEquals(200,a.send("POST","/api/v1/auth/password/change",Map.of("currentPassword",password,"newPassword",next),true).statusCode());
        assertEquals(401,a.me().statusCode());assertEquals(401,b.me().statusCode());
        assertEquals(200,new Browser().login(student,next).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='PASSWORD_CHANGED'",Integer.class));
    }
    @Test void resetTokenIsOneTimeExpiresAndRevokesSessions() throws Exception {
        Browser b=signedIn(student),anonymous=new Browser();anonymous.csrf();String next="Next-"+UUID.randomUUID();
        String expired=token(student,OneTimeToken.Purpose.PASSWORD_RESET,Instant.now().minusSeconds(5));
        assertEquals(400,anonymous.send("POST","/api/v1/auth/password/reset",Map.of("token",expired,"newPassword",next),true).statusCode());
        String valid=token(student,OneTimeToken.Purpose.PASSWORD_RESET,Instant.now().plusSeconds(300));
        assertEquals(200,anonymous.send("POST","/api/v1/auth/password/reset",Map.of("token",valid,"newPassword",next),true).statusCode());
        assertEquals(400,anonymous.send("POST","/api/v1/auth/password/reset",Map.of("token",valid,"newPassword",next),true).statusCode());
        assertEquals(401,b.me().statusCode());assertEquals(200,new Browser().login(student,next).statusCode());
    }
    @Test void invitationCreatesOneAccountAndExpiredInvitationIsRejected() throws Exception {
        Browser b=new Browser();b.csrf();String raw=token(null,OneTimeToken.Purpose.INVITATION,Instant.now().plusSeconds(300));
        var body=Map.of("token",raw,"displayName","Sinh viên thử nghiệm","password",password);
        assertEquals(200,b.send("POST","/api/v1/auth/invitations/accept",body,true).statusCode());
        assertEquals(400,b.send("POST","/api/v1/auth/invitations/accept",body,true).statusCode());
        assertEquals(5,users.count());
        String expired=token(null,OneTimeToken.Purpose.INVITATION,Instant.now().minusSeconds(5));
        assertEquals(400,b.send("POST","/api/v1/auth/invitations/accept",Map.of("token",expired,"displayName","Test","password",password),true).statusCode());
    }
    @Test void forgotResponseDoesNotRevealAccountOrSmtpFailure() throws Exception {
        Browser b=new Browser();b.csrf();
        var unknown=b.send("POST","/api/v1/auth/password/forgot",Map.of("email","missing@example.test"),true);
        doThrow(new org.springframework.mail.MailSendException("test failure")).when(email).reset(anyString(),anyString());
        var existing=b.send("POST","/api/v1/auth/password/forgot",Map.of("email",student.email),true);
        assertEquals(200,existing.statusCode());assertEquals(unknown.body(),existing.body());
        assertEquals(0,tokens.count());
    }
    @Test void adminReadsAreBoundedFilteredAndNeverExposeSecrets() throws Exception {
        Browser a=signedIn(admin);
        var list=a.send("GET","/api/v1/admin/users?search="+student.id+"&size=10",null,false);assertEquals(200,list.statusCode());
        var all=a.send("GET","/api/v1/admin/users",null,false);assertFalse(all.body().contains("passwordHash"));assertFalse(all.body().contains("securityVersion"));
        assertEquals(400,a.send("GET","/api/v1/admin/users?size=101",null,false).statusCode());
        var logs=a.send("GET","/api/v1/admin/audit-logs?action=LOGIN&result=SUCCESS&actorId="+admin.id+"&from=2000-01-01T00:00:00Z&until=2100-01-01T00:00:00Z",null,false);
        assertEquals(200,logs.statusCode());assertEquals(1,((Number)JsonPath.read(logs.body(),"$.totalElements")).intValue());
        assertFalse(logs.body().contains(password));assertFalse(logs.body().contains("token"));assertFalse(logs.body().contains("SESSION"));
        assertEquals(400,a.send("GET","/api/v1/admin/audit-logs?from=2100-01-01T00:00:00Z&until=2000-01-01T00:00:00Z",null,false).statusCode());
    }
    @Test void businessRollbackRollsBackAuditAndPasswordChange() {
        assertThrows(IllegalStateException.class,()->transactions.executeWithoutResult(tx->{
            accounts.changePassword(student.id,0,password,"Next-"+UUID.randomUUID());
            throw new IllegalStateException("business rollback");
        }));
        assertTrue(encoder.matches(password,users.findById(student.id).orElseThrow().passwordHash));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='PASSWORD_CHANGED'",Integer.class));
    }
    @Test void failedAuditRollsBackCredentialMutation() {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_test_audit CHECK (action <> 'PASSWORD_CHANGED')");
        try {assertThrows(RuntimeException.class,()->accounts.changePassword(student.id,0,password,"Next-"+UUID.randomUUID()));}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK reject_test_audit");}
        assertEquals(0,users.findById(student.id).orElseThrow().securityVersion);
        assertTrue(encoder.matches(password,users.findById(student.id).orElseThrow().passwordHash));
    }
    @Test void concurrentAdminSuspensionsRetainOneActiveAdministrator() throws Exception { concurrentAdminChange(false); }
    @Test void concurrentAdminRevocationsRetainOneActiveAdministrator() throws Exception { concurrentAdminChange(true); }
    void concurrentAdminChange(boolean revoke) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->adminChange(admin,otherAdmin,revoke,ready,start));
            var second=pool.submit(()->adminChange(otherAdmin,admin,revoke,ready,start));
            assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            int successes=first.get(20,TimeUnit.SECONDS)+second.get(20,TimeUnit.SECONDS);
            assertEquals(1,successes);assertEquals(1,memberships.countActiveGlobalRole(Role.ADMIN,AccountStatus.ACTIVE));
        }
    }
    int adminChange(UserAccount actor,UserAccount target,boolean revoke,CountDownLatch ready,CountDownLatch start) throws Exception {
        as(actor);ready.countDown();start.await();
        try {
            if(revoke) membershipService.revoke(actor.id,target.id,Role.ADMIN,null);
            else lifecycle.transition(actor.id,target.id,AccountStatus.SUSPENDED,"SECURITY_REVIEW");
            return 1;
        } catch(org.springframework.security.access.AccessDeniedException denied) {return 0;}
        finally {SecurityContextHolder.clearContext();}
    }
    @Test void concurrentResetCanConsumeTokenOnlyOnce() throws Exception {
        String raw=token(student,OneTimeToken.Purpose.PASSWORD_RESET,Instant.now().plusSeconds(300));
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt=()->{Browser b=new Browser();b.csrf();ready.countDown();start.await();return b.send("POST","/api/v1/auth/password/reset",Map.of("token",raw,"newPassword","Next-"+UUID.randomUUID()),true).statusCode();};
            var first=pool.submit(attempt);var second=pool.submit(attempt);assertTrue(ready.await(5,TimeUnit.SECONDS));start.countDown();
            List<Integer> statuses=new ArrayList<>(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(200,400),statuses);
            assertEquals(1,users.findById(student.id).orElseThrow().securityVersion);
        }
    }
    @Test void healthIsMinimalAndDemoVerificationIsDisabled() throws Exception {
        Browser b=signedIn(admin);var health=b.send("GET","/actuator/health",null,false);
        assertEquals(200,health.statusCode());assertFalse(health.body().contains("jdbc"));
        assertEquals(404,b.send("PATCH","/api/v1/demo/admin/organizations/"+orgA.id+"/verify",null,true).statusCode());
    }
    @Test void auditRejectsUnregisteredEventsAndSensitiveFreeText() {
        assertThrows(IllegalArgumentException.class,()->audit.write(admin.id,"UNREGISTERED_EVENT","user",student.id,"SUCCESS"));
        assertThrows(IllegalArgumentException.class,()->audit.writeScoped(admin.id,"PASSWORD_RESET","user",student.id,"SUCCESS",null,"password="+password));
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="frontend.e2e",matches="true")
    void frontendMustFlowsInChromium() throws Exception {
        UserAccount resetUser=user(Role.STUDENT,university.id);
        String invitation=token(null,OneTimeToken.Purpose.INVITATION,Instant.now().plusSeconds(600));
        String invitationEmail=tokens.findByTokenHashAndPurpose(TokenCodec.hash(invitation),OneTimeToken.Purpose.INVITATION).orElseThrow().email;
        String reset=token(resetUser,OneTimeToken.Purpose.PASSWORD_RESET,Instant.now().plusSeconds(600));
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e"):new ProcessBuilder("npm","run","test:e2e");
        builder.directory(new java.io.File("web-demo"));
        Map<String,String> env=builder.environment();env.put("API_PROXY_TARGET","http://localhost:"+port);
        env.put("E2E_PASSWORD",password);env.put("E2E_NEW_PASSWORD","Next-"+UUID.randomUUID());
        env.put("E2E_ADMIN_EMAIL",admin.email);env.put("E2E_STUDENT_EMAIL",student.email);env.put("E2E_ENTERPRISE_EMAIL",enterprise.email);
        env.put("E2E_INVITATION_TOKEN",invitation);env.put("E2E_INVITATION_EMAIL",invitationEmail);
        env.put("E2E_RESET_TOKEN",reset);env.put("E2E_RESET_EMAIL",resetUser.email);env.put("E2E_ORG_B_ID",orgB.id);
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/frontend-e2e.log"));
        Process process=builder.start();
        if(!process.waitFor(240,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Frontend E2E timed out; see target/frontend-e2e.log");}
        assertEquals(0,process.exitValue(),"See target/frontend-e2e.log and web-demo/test-results/e2e.xml");
    }
    HttpResponse<String> replay(String cookie,String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Cookie","SESSION="+cookie).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
}
