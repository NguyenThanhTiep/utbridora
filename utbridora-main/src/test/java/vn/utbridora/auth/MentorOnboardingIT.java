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
}
