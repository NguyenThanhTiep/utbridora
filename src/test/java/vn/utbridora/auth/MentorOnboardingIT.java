package vn.utbridora.auth;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.web.context.request.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.testcontainers.containers.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.tokens.*;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Real T1 token/identity, real T3 callback and real SMTP into an isolated Mailpit container. */
@SpringBootTest(properties={"app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-onboarding-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-onboarding-admin-password-456",
    "app.t5.placement-delivery.enabled=false","logging.level.org.springframework.jdbc.core.JdbcTemplate=INFO"})
@ActiveProfiles("local")
class MentorOnboardingIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static final GenericContainer<?> MAIL=new GenericContainer<>("axllent/mailpit:latest").withExposedPorts(1025,8025);
    static {MYSQL.start();MAIL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.mail.host",MAIL::getHost);r.add("spring.mail.port",()->MAIL.getMappedPort(1025));
    }
    @Autowired JdbcTemplate jdbc;@Autowired AuthorizationPolicy core;@Autowired MentorInvitationService invitations;
    @Autowired AccountTokenService accounts;@Autowired WorkspaceRuntime workspace;
    static String id(int i) {return "00000000-0000-4000-8000-"+String.format("%012d",i);}
    @Test void newMentorActivatesOwnIdentityAndScopedAssignmentsWithOneTimeT1Token() throws Exception {
        var hr=core.currentSubject(id(4)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(hr);
        String group=jdbc.queryForObject("SELECT id FROM internship_groups LIMIT 1",String.class);
        var request=new MockHttpServletRequest();request.addHeader("X-Context-Assignment-Id",workspace.assignments(hr).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var invited=invitations.issue(hr,UUID.randomUUID().toString(),new MentorInvitationService.MentorInviteRequest("new-mentor-week2@example.test",id(12),List.of(group),id(21),"DEMO-NEW-MENTOR-W2"));
            assertEquals("NEW_IDENTITY_PENDING",invited.state());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email='new-mentor-week2@example.test'",Integer.class));
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();String base="http://"+MAIL.getHost()+":"+MAIL.getMappedPort(8025);
            var messages=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("messages");
            assertEquals(1,messages.size(),"SMTP must deliver once to the dedicated Mailpit instance");
            String messageId=messages.get(0).get("ID").asString();var message=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/message/"+messageId)).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            var matcher=Pattern.compile("#token=([A-Za-z0-9_-]+)").matcher(message.get("Text").asString());assertTrue(matcher.find(),"Activation link must be delivered without returning a token to HR");String raw=matcher.group(1);
            accounts.acceptInvitation(raw,"Synthetic new Mentor","Synthetic-new-mentor-password-789");
            String user=jdbc.queryForObject("SELECT id FROM users WHERE email='new-mentor-week2@example.test'",String.class);
            var auth=core.currentSubject(user).orElseThrow();assertEquals(1,workspace.assignments(auth).size());assertEquals(group,workspace.assignments(auth).getFirst().scope().groupId());
            assertEquals("ACCEPTED",invitations.mine(auth,0,25).content().getFirst().state());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=? AND role='ENTERPRISE' AND organization_id=? AND active=true",Integer.class,user,id(12)));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=? AND role IN ('ADMIN','STUDENT','SCHOOL_STAFF')",Integer.class,user));
            assertThrows(IllegalArgumentException.class,()->accounts.acceptInvitation(raw,"Replay","Synthetic-new-mentor-password-789"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email='new-mentor-week2@example.test'",Integer.class));
        } finally {SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
    }

    @org.junit.jupiter.api.BeforeEach void clearDedicatedMailboxAndClock() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.systemUTC());
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://"+MAIL.getHost()+":"+MAIL.getMappedPort(8025)+"/api/v1/messages")).DELETE().build(),HttpResponse.BodyHandlers.discarding());
    }
    private void hrContext() {
        var hr=core.currentSubject(id(4)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(hr);
        var request=new MockHttpServletRequest();request.addHeader("X-Context-Assignment-Id",workspace.assignments(hr).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @org.junit.jupiter.api.AfterEach void restoreContextAndClock() {
        org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.systemUTC());
        SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();
    }
    @Test void samePendingMentorClaimRotatesOnlyAfterCooldownAndCannotTakeOverAnotherScope() {
        hrContext();var hr=core.currentSubject(id(4)).orElseThrow();String group=jdbc.queryForObject("SELECT id FROM internship_groups ORDER BY id LIMIT 1",String.class);
        var input=new MentorInvitationService.MentorInviteRequest("rotate-mentor@example.test",id(12),List.of(group),id(21),"DEMO-ROTATE");String key=UUID.randomUUID().toString();
        var issued=invitations.issue(hr,key,input);String old=jdbc.queryForObject("SELECT token_id FROM mentor_invitation_refs WHERE id=?",String.class,issued.invitationId());
        assertEquals(issued,invitations.issue(hr,key,input));
        var limited=assertThrows(vn.utbridora.auth.operations.OperationFailure.class,()->invitations.issue(hr,UUID.randomUUID().toString(),input));
        assertEquals(429,limited.status);assertEquals("RESEND_RATE_LIMITED",limited.code);
        var next=java.time.Instant.now().plusSeconds(301);org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.fixed(next,java.time.ZoneOffset.UTC));
        var rotated=invitations.issue(hr,UUID.randomUUID().toString(),input);
        assertEquals(issued.invitationId(),rotated.invitationId());assertEquals("ACCOUNT_PENDING",rotated.state());assertEquals(2,rotated.version());
        assertNotEquals(old,jdbc.queryForObject("SELECT token_id FROM mentor_invitation_refs WHERE id=?",String.class,issued.invitationId()));
        assertNotNull(jdbc.queryForObject("SELECT consumed_at FROM one_time_tokens WHERE id=?",java.sql.Timestamp.class,old));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email=? AND consumed_at IS NULL",Integer.class,input.email()));
        String other=UUID.randomUUID().toString();jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) SELECT ?,cohort_id,?,1 FROM internship_groups WHERE id=?",other,"OTHER-"+other,group);
        var review=invitations.issue(hr,UUID.randomUUID().toString(),new MentorInvitationService.MentorInviteRequest(input.email(),id(12),List.of(other),id(21),"DEMO-OTHER-CONTEXT"));
        assertEquals("REVIEW_REQUIRED",review.state());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email=? AND consumed_at IS NULL",Integer.class,input.email()));
    }
    @Test void threeManualReissuesPerRollingDayAreEnforcedAcrossKeys() {
        hrContext();var hr=core.currentSubject(id(4)).orElseThrow();String group=jdbc.queryForObject("SELECT id FROM internship_groups ORDER BY id LIMIT 1",String.class);
        var input=new MentorInvitationService.MentorInviteRequest("quota-mentor@example.test",id(12),List.of(group),id(21),"DEMO-QUOTA");
        invitations.issue(hr,UUID.randomUUID().toString(),input);var now=java.time.Instant.now();
        for(int n=1;n<=3;n++) {
            org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.fixed(now.plusSeconds(n*301L),java.time.ZoneOffset.UTC));
            assertEquals("ACCOUNT_PENDING",invitations.issue(hr,UUID.randomUUID().toString(),input).state());
        }
        org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.fixed(now.plusSeconds(4*301L),java.time.ZoneOffset.UTC));
        assertEquals(429,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,()->invitations.issue(hr,UUID.randomUUID().toString(),input)).status);
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM mentor_manual_reissues WHERE email=?",Integer.class,input.email()));
    }


    private String tokenFromDedicatedMailbox(String hash) throws Exception {
        var client=HttpClient.newHttpClient();String base="http://"+MAIL.getHost()+":"+MAIL.getMappedPort(8025);
        var messages=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/messages")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("messages");
        for(var entry:messages) {
            var msg=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/message/"+entry.get("ID").asString())).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
            var matcher=Pattern.compile("#token=([A-Za-z0-9_-]+)").matcher(msg.get("Text").asString());
            if(matcher.find() && TokenCodec.hash(matcher.group(1)).equals(hash)) return matcher.group(1);
        }
        throw new AssertionError("Current invitation was not delivered to dedicated Mailpit");
    }
    @Test void pendingIdentityActivationKeepsUserIdAndOldGenerationCannotActivate() throws Exception {
        hrContext();var hr=core.currentSubject(id(4)).orElseThrow();String group=jdbc.queryForObject("SELECT id FROM internship_groups ORDER BY id LIMIT 1",String.class);
        var input=new MentorInvitationService.MentorInviteRequest("pending-bound-mentor@example.test",id(12),List.of(group),id(21),"DEMO-PENDING-IDENTITY");
        var invitation=invitations.issue(hr,UUID.randomUUID().toString(),input);
        String oldHash=jdbc.queryForObject("SELECT t.token_hash FROM one_time_tokens t JOIN mentor_invitation_refs r ON r.token_id=t.id WHERE r.id=?",String.class,invitation.invitationId());
        String oldRaw=tokenFromDedicatedMailbox(oldHash),subject=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,security_version,failed_attempts,created_at,updated_at) VALUES(?,?,'not-an-active-password','Pending Mentor','PENDING_ACTIVATION',0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",subject,input.email());
        jdbc.update("UPDATE mentor_invitation_refs SET user_id=? WHERE id=?",subject,invitation.invitationId());
        org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.fixed(java.time.Instant.now().plusSeconds(301),java.time.ZoneOffset.UTC));
        var rotated=invitations.issue(hr,UUID.randomUUID().toString(),input);assertEquals("ACCOUNT_PENDING",rotated.state());
        assertThrows(IllegalArgumentException.class,()->accounts.acceptInvitation(oldRaw,"Rejected old token","Synthetic-pending-mentor-password-789"));
        String current=jdbc.queryForObject("SELECT t.token_hash FROM one_time_tokens t JOIN mentor_invitation_refs r ON r.token_id=t.id WHERE r.id=?",String.class,invitation.invitationId());
        accounts.acceptInvitation(tokenFromDedicatedMailbox(current),"Activated pending Mentor","Synthetic-pending-mentor-password-789");
        assertEquals(subject,jdbc.queryForObject("SELECT id FROM users WHERE email=?",String.class,input.email()));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,subject));
        assertEquals("ACCEPTED",invitations.mine(core.currentSubject(subject).orElseThrow(),0,25).content().getFirst().state());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?",Integer.class,input.email()));
    }
    @Test void concurrentDifferentKeysRotateExactlyOneGeneration() throws Exception {
        hrContext();String group=jdbc.queryForObject("SELECT id FROM internship_groups ORDER BY id LIMIT 1",String.class);
        var input=new MentorInvitationService.MentorInviteRequest("race-rotate-mentor@example.test",id(12),List.of(group),id(21),"DEMO-RACE-REISSUE");
        invitations.issue(core.currentSubject(id(4)).orElseThrow(),UUID.randomUUID().toString(),input);
        org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.fixed(java.time.Instant.now().plusSeconds(301),java.time.ZoneOffset.UTC));
        String assignment=((ServletRequestAttributes)RequestContextHolder.getRequestAttributes()).getRequest().getHeader("X-Context-Assignment-Id");
        var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(int n=0;n<2;n++) futures.add(executor.submit(()->{
                var auth=core.currentSubject(id(4)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(auth);
                var request=new MockHttpServletRequest();request.addHeader("X-Context-Assignment-Id",assignment);RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                ready.countDown();assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS));
                try {invitations.issue(core.currentSubject(id(4)).orElseThrow(),UUID.randomUUID().toString(),input);return 202;}
                catch(vn.utbridora.auth.operations.OperationFailure failure){return failure.status;}
                finally {SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
            }));
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            var codes=new java.util.ArrayList<Integer>();for(var future:futures) codes.add(future.get(20,java.util.concurrent.TimeUnit.SECONDS));
            java.util.Collections.sort(codes);assertEquals(List.of(202,429),codes);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mentor_manual_reissues WHERE email=?",Integer.class,input.email()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email=? AND consumed_at IS NULL",Integer.class,input.email()));
    }

    @Test void sameEmailDoesNotTakeOverAnUnboundPendingIdentity() {
        hrContext();var hr=core.currentSubject(id(4)).orElseThrow();String group=jdbc.queryForObject("SELECT id FROM internship_groups ORDER BY id LIMIT 1",String.class);
        var input=new MentorInvitationService.MentorInviteRequest("foreign-pending-mentor@example.test",id(12),List.of(group),id(21),"DEMO-FOREIGN-PENDING");
        var issued=invitations.issue(hr,UUID.randomUUID().toString(),input);
        String token=jdbc.queryForObject("SELECT token_id FROM mentor_invitation_refs WHERE id=?",String.class,issued.invitationId()),subject=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,security_version,failed_attempts,created_at,updated_at) VALUES(?,?,'unusable-pending','Other pending workflow','PENDING_ACTIVATION',0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",subject,input.email());
        org.springframework.test.util.ReflectionTestUtils.setField(invitations,"clock",java.time.Clock.fixed(java.time.Instant.now().plusSeconds(301),java.time.ZoneOffset.UTC));
        assertEquals("REVIEW_REQUIRED",invitations.issue(hr,UUID.randomUUID().toString(),input).state());
        assertEquals(token,jdbc.queryForObject("SELECT token_id FROM mentor_invitation_refs WHERE id=?",String.class,issued.invitationId()));
        assertEquals("PENDING_ACTIVATION",jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,subject));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM memberships WHERE user_id=?",Integer.class,subject));
    }
}
