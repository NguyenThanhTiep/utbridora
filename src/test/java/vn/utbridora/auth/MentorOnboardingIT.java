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
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAuthority;
import vn.utbridora.auth.modules.internship.domain.WorkspaceScope;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
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
    @Autowired WorkspaceAuthority workspaceAuthority;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired vn.utbridora.auth.contracts.placement.PlacementReadPort qa01PlacementPort;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.PlacementInbox qa01PlacementInbox;
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
            // QA01 / T1-05-TC07: authorization boundary for a newly accepted T3 mentor.
            qa01Tc07MentorScopeAndRevocation(hr,auth,group,invited.invitationId());
        } finally {SecurityContextHolder.clearContext();RequestContextHolder.resetRequestAttributes();}
    }

    /**
     * QA01 / T1-05-TC07 (R18/G18, R20/G20, R27/G27):
     * Prove that a freshly onboarded T3 mentor receives only the accepted
     * assignment. Selecting an HR/unknown assignment or a different scope must
     * not grant read access, and revoking the accepted invitation must make a
     * previously valid assignment unusable. All data lives in the isolated IT DB.
     *
     * This method does NOT verify PlacementReadPort producer provenance or
     * absence of copied student credentials. Those require the owner adapter.
     */
    private void qa01Tc07MentorScopeAndRevocation(
            org.springframework.security.core.Authentication hr,
            org.springframework.security.core.Authentication mentor,
            String group,
            String invitationId) {
        var mentorAssignment = workspace.assignments(mentor).stream()
                .filter(a -> "MENTOR".equals(a.kind()) && group.equals(a.scope().groupId()))
                .findFirst().orElseThrow();
        var hrAssignment = workspace.assignments(hr).stream()
                .filter(a -> "HR".equals(a.kind()))
                .findFirst().orElseThrow();
        var s = mentorAssignment.scope();
        var correctScope = new WorkspaceScope(
                UUID.fromString(s.enterpriseOrgId()),
                UUID.fromString(s.schoolOrgId()),
                UUID.fromString(s.termId()));
        var otherSchool = new WorkspaceScope(
                UUID.fromString(s.enterpriseOrgId()),UUID.randomUUID(),UUID.fromString(s.termId()));
        var otherTerm = new WorkspaceScope(
                UUID.fromString(s.enterpriseOrgId()),UUID.fromString(s.schoolOrgId()),UUID.randomUUID());

        // WorkspaceAuthority may read ports requiring an existing transaction.
        transactions.executeWithoutResult(status -> {
            qa01SelectAssignment(mentorAssignment.id());
            assertEquals(mentorAssignment.id(),workspaceAuthority.selected(mentor).id(),
                    "TC07: a mentor can select their own live assignment");
            assertTrue(workspaceAuthority.canReadScope(mentor,correctScope,UUID.fromString(group)),
                    "TC07: own group should be readable");
            assertFalse(workspaceAuthority.canReadScope(mentor,correctScope,UUID.randomUUID()),
                    "TC07: a different group must be denied");
            assertFalse(workspaceAuthority.canReadScope(mentor,otherSchool,UUID.fromString(group)),
                    "TC07: a different school must be denied");
            assertFalse(workspaceAuthority.canReadScope(mentor,otherTerm,UUID.fromString(group)),
                    "TC07: a different term must be denied");

            qa01SelectAssignment(hrAssignment.id());
            assertThrows(WorkspaceRuleException.class,()->workspaceAuthority.selected(mentor),
                    "TC07: an HR assignment header must not confer HR authority on a mentor");
            qa01SelectAssignment(UUID.randomUUID().toString());
            assertThrows(WorkspaceRuleException.class,()->workspaceAuthority.selected(mentor),
                    "TC07: an invented assignment must not confer access");
            qa01SelectAssignment(null);
            assertThrows(WorkspaceRuleException.class,()->workspaceAuthority.selected(mentor),
                    "TC07: an absent assignment must not confer access");

        });

        qa01SelectAssignment(hrAssignment.id());
        var current = invitations.mine(mentor,0,25).content().stream()
                .filter(i -> invitationId.equals(i.invitationId()))
                .findFirst().orElseThrow();
        assertEquals("ACCEPTED",current.state(),"TC07: accepted invitation prerequisite");
        var revoked = invitations.revoke(hr,invitationId,UUID.randomUUID().toString(),
                new MentorInvitationService.Revoke(current.version(),"HR_REVOKED"));
        assertEquals("REVOKED",revoked.state(),"TC07: HR revocation must succeed");
        // Revocation may invalidate the old authentication immediately. Check
        // the persisted assignment separately, then enforce denied access.
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM internship_context_assignments "
                        + "WHERE id=? AND active=true AND invitation_accepted=true",
                Integer.class, mentorAssignment.id()),
                "TC07: revoked mentor assignment must not remain accepted and active");
        try {
            assertTrue(workspace.assignments(mentor).stream()
                    .noneMatch(a -> mentorAssignment.id().equals(a.id())),
                    "TC07: revoked mentor assignment must be absent from readable assignments");
        } catch (WorkspaceRuleException rejectedStaleAuth) {
            // UNAUTHENTICATED is also correct after the mentor session is revoked.
        }
        // Recheck only after revoke() has committed its own transaction.
        transactions.executeWithoutResult(status -> {
            qa01SelectAssignment(mentorAssignment.id());
            assertThrows(WorkspaceRuleException.class,()->workspaceAuthority.selected(mentor),
                    "TC07: the stale assignment header must not restore access");
            assertFalse(workspaceAuthority.canReadScope(mentor,correctScope,UUID.fromString(group)),
                    "TC07: revoked mentor must lose prior group read access");
        });
    }

    private static void qa01SelectAssignment(String id) {
        var request = new MockHttpServletRequest();
        if(id != null) request.addHeader("X-Context-Assignment-Id",id);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    /**
     * QA01 / T1-05-TC07 (Placement owner port / forged-delivery negative path).
     * Verifies a T3 case references the current T5 placement, that the owner
     * port is the source of identity/scope/version, and that a spoofed T5
     * delivery without an authentic outbox record cannot mutate the T3 inbox.
     * Does not replace a complete real T5 -> T3 lifecycle or cross-cohort test.
     */
    @Test void qa01Tc07PlacementOwnerPortAndSpoofedDelivery() {
        var placementId = jdbc.queryForObject(
                "SELECT id FROM placements ORDER BY id LIMIT 1", String.class);
        assertNotNull(placementId, "TC07 requires the seeded Placement fixture");

        var ownerRow = jdbc.queryForMap(
                "SELECT student_user_id,school_org_id,enterprise_org_id,term_id,state,version "
                    + "FROM placements WHERE id=?", placementId);
        var caseRow = jdbc.queryForMap(
                "SELECT placement_id,student_user_id,school_org_id,enterprise_org_id,term_id "
                    + "FROM internship_cases WHERE placement_id=? LIMIT 1", placementId);

        // PlacementReadPort is MANDATORY: join a real read transaction.
        transactions.executeWithoutResult(tx -> {
            var current = qa01PlacementPort.readCurrentForGuard(placementId).orElseThrow();
            assertEquals(placementId, current.placementId());
            assertEquals(ownerRow.get("student_user_id"), current.studentUserId());
            assertEquals(ownerRow.get("school_org_id"), current.schoolOrgId());
            assertEquals(ownerRow.get("enterprise_org_id"), current.enterpriseOrgId());
            assertEquals(ownerRow.get("term_id"), current.termId());
            assertEquals(ownerRow.get("state"), current.state());
            assertEquals(((Number) ownerRow.get("version")).longValue(), current.version());
            assertTrue(qa01PlacementPort.readCurrentForGuard(UUID.randomUUID().toString()).isEmpty(),
                    "TC07: nonexistent Placement must not yield a fabricated owner view");

            assertEquals(current.placementId(), caseRow.get("placement_id"));
            assertEquals(current.studentUserId(), caseRow.get("student_user_id"));
            assertEquals(current.schoolOrgId(), caseRow.get("school_org_id"));
            assertEquals(current.enterpriseOrgId(), caseRow.get("enterprise_org_id"));
            assertEquals(current.termId(), caseRow.get("term_id"));
        });

        var viewFields = java.util.Arrays.stream(
                vn.utbridora.auth.contracts.placement.PlacementReadPort.PlacementView.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList();
        assertTrue(viewFields.stream().noneMatch(name -> name.matches(
                "(?i).*(password|credential|secret|token|hash).*")),
                "TC07: owner port view must not contain credential fields");

        // Claimed producerModule=T5 is insufficient without the exact outbox tuple.
        var forged = org.mockito.Mockito.mock(
                vn.utbridora.auth.contracts.placement.PlacementLifecycleEvent.class);
        var spoofedContext = org.mockito.Mockito.mock(
                vn.utbridora.auth.contracts.placement.TrustedEventContext.class);
        org.mockito.Mockito.when(spoofedContext.producerModule()).thenReturn("T5");
        org.mockito.Mockito.when(spoofedContext.deliveryOperationId())
                .thenReturn(UUID.randomUUID().toString());
        org.mockito.Mockito.when(forged.eventId()).thenReturn(UUID.randomUUID().toString());
        org.mockito.Mockito.when(forged.placementId()).thenReturn(placementId);
        org.mockito.Mockito.when(forged.aggregateVersion()).thenReturn(99_999L);
        org.mockito.Mockito.when(forged.eventType()).thenReturn("PlacementApproved");

        transactions.executeWithoutResult(tx ->
                assertFalse(qa01PlacementPort.authenticDelivery(spoofedContext, forged),
                    "TC07: T5 claim without a matching outbox record must be rejected"));

        int casesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases", Integer.class);
        int inboxBefore = jdbc.queryForObject("SELECT COUNT(*) FROM placement_inbox", Integer.class);
        int historyBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM internship_case_projection_history", Integer.class);
        var rejected = qa01PlacementInbox.consume(spoofedContext, forged);
        assertEquals("REJECT", rejected.outcome(), "TC07: forged delivery must be rejected");
        assertEquals(casesBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM internship_cases", Integer.class));
        assertEquals(inboxBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM placement_inbox", Integer.class));
        assertEquals(historyBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM internship_case_projection_history", Integer.class));
    }

}
