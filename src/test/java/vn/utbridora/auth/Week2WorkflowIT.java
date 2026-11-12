package vn.utbridora.auth;

import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.application.*;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.student.application.StudentProfileService;
import vn.utbridora.auth.operations.OperationFailure;

/** Dedicated database; synthetic week-1 seed is a prerequisite, never workflow acceptance evidence. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-week2-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-week2-admin-password-456","app.t5.placement-delivery.enabled=false","logging.level.org.springframework.jdbc=WARN"})
@ActiveProfiles("local")
class Week2WorkflowIT {
    @org.springframework.beans.factory.annotation.Value("${DEMO_PASSWORD}")
    private String qaWeek2DemoPassword;
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);}
    @Autowired JdbcTemplate jdbc;@Autowired AuthorizationPolicy core;@Autowired SchoolDelegationService delegations;
    @Autowired StudentProfileService profiles;@Autowired MatchingService matching;@Autowired CooperationService cooperations;
    @Autowired DemandService demands;@Autowired ProposalService proposals;
    @Autowired vn.utbridora.auth.modules.student.application.StudentEligibilityService eligibility;
    @Autowired vn.utbridora.auth.modules.student.application.StudentConsentService consents;
    @Autowired ProfileShareService shares;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime workspace;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.WorkspaceTaskActions taskActions;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAuthority workspaceAuthority;
    @Autowired vn.utbridora.auth.tokens.MentorInvitationService mentorInvites;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAssignmentService assignments;
    @Autowired vn.utbridora.auth.user.UserRepository users;
    @Autowired vn.utbridora.auth.authorization.MembershipService memberships;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired vn.utbridora.auth.audit.AuditService audit;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    static String id(int i) {return "00000000-0000-4000-8000-"+String.format("%012d",i);}
    static String key() {return UUID.randomUUID().toString();}
    Authentication as(int id) {var a=core.currentSubject(id(id)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(a);return a;}
    @AfterEach void clear() {SecurityContextHolder.clearContext();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    @Test void actualDelegationPolicyMatchingAndCooperationUseOwnerSourcesAndRollback() throws Exception {
        var admin=core.currentSubject(jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'",String.class)).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(admin);
                var grant = delegations.bootstrap(admin, key(), new SchoolDelegationService.Bootstrap(
                id(2), id(11), id(21),
                List.of("school:delegation:manage", "matching:run:execute",
                        "matching:school:detail_read", "matching:school:nominate",
                        "cooperation:school:review", "t5:proposal:manage",
                        "student:eligibility:school:approve", "student_profile:share:school:manage",
                        "student_profile:school:read"),
                List.of("displayName"),
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400),
                "DEMO-WEEK2-TEST"));
        var school=as(2);var student=as(6);assertTrue(profiles.read(student,id(21)).completeness().checks().stream().filter(c->c.code().equals("MAJOR_VALID")).findFirst().orElseThrow().passed());
        String demand=jdbc.queryForObject("SELECT id FROM demands LIMIT 1",String.class);
        school=as(2);var evaluated=matching.evaluate(school,key(),new MatchingService.Evaluate(demand,1L,1L));
        assertEquals("COMPLETED",evaluated.summary().run().state());assertEquals(1,evaluated.summary().matched()+evaluated.summary().missingData()+evaluated.summary().notMatched());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM match_runs",Integer.class));
        var denied=as(4);assertThrows(OperationFailure.class,()->matching.details(denied,evaluated.summary().run().matchRunId(),"MATCH",0,25));
        var hr=as(4);String command=key();var sent=cooperations.submitWithCooperation(hr,id(12),command,new CooperationService.Submit(demand,1L));
        assertEquals("READY",sent.demand().state());assertEquals("SUBMITTED",sent.cooperation().state());
        assertEquals(sent,cooperations.submitWithCooperation(hr,id(12),command,new CooperationService.Submit(demand,1L)));
        final var reviewer=as(2);
        var approved=cooperations.decide(reviewer,id(11),sent.cooperation().cooperationId(),key(),new CooperationService.Decision(sent.cooperation().version(),"APPROVE",null,"DEMO-COOP-W2",List.of()));
        assertEquals("APPROVED",approved.state());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM cooperations",Integer.class));
        assertThrows(OperationFailure.class,()->cooperations.decide(reviewer,id(11),approved.cooperationId(),key(),new CooperationService.Decision(1L,"APPROVE",null,"DEMO-STALE",List.of())));

        // The profile is changed through T2's real owner, using the actual criteria from T5.
        var demandData=demands.list(as(4),id(12),null,null,false,0,25).content().getFirst();
        var current=profiles.read(as(6),id(21));var before=current.profile();
        var skillInputs=demandData.data().required().skills().stream().map(s->new vn.utbridora.auth.modules.student.api.StudentProfileDtos.CanonicalSkillInput(s.skillCode(),5,new java.math.BigDecimal("10"),null)).toList();
        var range=demandData.data().required().dateRange();
        var input=new vn.utbridora.auth.modules.student.api.StudentProfileDtos.ProfileInput(current.profileVersion(),id(21),before.majorCode(),true,skillInputs,List.of(),true,List.of(new vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange(range.startDate().toString(),range.endDate().toString())),before.orientationCodes(),before.subjectCodes(),before.projects(),before.certificates(),before.cvFileId(),before.phone());
        assertTrue(profiles.save(as(6),key(),vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(input)).completeness().complete());
        final var matchingSchool=as(2);var matched=matching.evaluate(matchingSchool,key(),new MatchingService.Evaluate(demand,2L,1L));assertEquals(1,matched.summary().matched());
        // QA01 T1-05-TC08: stale T5 demand/criteria versions must not create a matching run.
        assertEquals(2L, matched.summary().run().demandVersion(), "TC08: demand version in run");
        assertEquals(1L, matched.summary().run().criteriaVersion(), "TC08: criteria version in run");
        assertFalse(matched.summary().run().datasetSnapshotId().isBlank(), "TC08: dataset snapshot reference");
        assertFalse(matched.summary().run().profileSnapshotVersion().isBlank(), "TC08: owner snapshot version");
        assertEquals(vn.utbridora.auth.modules.matching.domain.StrictClassifier.RULE_VERSION,
                matched.summary().run().ruleVersion());
        int qa01Tc08RunsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM match_runs", Integer.class);
        var qa01Tc08StaleDemand = assertThrows(OperationFailure.class,
                () -> matching.evaluate(matchingSchool, key(), new MatchingService.Evaluate(demand, 1L, 1L)));
        assertEquals(409, qa01Tc08StaleDemand.status, "TC08: stale demand version status");
        assertEquals("CRITERIA_STALE", qa01Tc08StaleDemand.code, "TC08: stale demand version code");
        var qa01Tc08StaleCriteria = assertThrows(OperationFailure.class,
                () -> matching.evaluate(matchingSchool, key(), new MatchingService.Evaluate(demand, 2L, 2L)));
        assertEquals(409, qa01Tc08StaleCriteria.status, "TC08: stale criteria version status");
        assertEquals("CRITERIA_STALE", qa01Tc08StaleCriteria.code, "TC08: stale criteria version code");
        assertEquals(qa01Tc08RunsBefore,
                jdbc.queryForObject("SELECT COUNT(*) FROM match_runs", Integer.class),
                "TC08: rejected stale evaluations must not create matching runs");
        var nominate=new ProposalService.Nominate(List.of(id(6)),2L,1L,approved.version(),null);
        assertEquals("SCHOOL_APPROVAL_REQUIRED",assertThrows(OperationFailure.class,()->proposals.nominate(matchingSchool,matched.summary().run().matchRunId(),key(),nominate)).code);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM proposal_batches",Integer.class));
        eligibility.decide(matchingSchool,key(),new vn.utbridora.auth.modules.student.application.StudentEligibilityService.EligibilityDecision(id(6),id(21),1L,true,"DEMO_ELIGIBLE","DEMO-W2-ELIGIBILITY"));
        String nominationKey=key();var nomination=proposals.nominate(matchingSchool,matched.summary().run().matchRunId(),nominationKey,nominate);
        assertEquals(nomination,proposals.nominate(matchingSchool,matched.summary().run().matchRunId(),nominationKey,nominate));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proposal_items",Integer.class));
        var invitation=proposals.invite(matchingSchool,nomination.items().getFirst().proposalItemId(),key(),new ProposalService.Invite(1L,null));
        final var studentAuth=as(6);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            var calls=new ArrayList<java.util.concurrent.Callable<String>>();
            for(int n=0;n<20;n++) calls.add(()->{SecurityContextHolder.getContext().setAuthentication(studentAuth);try {return proposals.decide(studentAuth,invitation.invitationId(),key(),new ProposalService.InvitationDecision("ACCEPT",1L,null,null)).state();}catch(OperationFailure conflict){assertEquals(409,conflict.status);return "CONFLICT";}finally{SecurityContextHolder.clearContext();}});
            var results=pool.invokeAll(calls);int won=0;for(var result:results) if(result.get().equals("ACCEPTED")) won++;assertEquals(1,won);
        } finally {pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='INVITATION_RESPONDED'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM student_consents",Integer.class));
        int placements=jdbc.queryForObject("SELECT COUNT(*) FROM placements",Integer.class);
        var grantConsent=consents.grant(as(6),key(),new vn.utbridora.auth.modules.student.application.StudentConsentService.GrantConsent(invitation.invitationId(),"INTERNSHIP_SELECTION",List.of("majorCode","skills"),Instant.now().plusSeconds(86400),"demo-consent-v1",0L,0L));
        // QA01 T1-05-TC06: verify T2 owner ports with the same live consent before sharing/revoking.
        qa01OwnerPortsScopeTC06(grantConsent);
        var item=proposals.list(as(2),approved.cooperationId(),0,25).content().getFirst();
        var share=shares.grant(as(2),item.proposalItemId(),key(),new ProfileShareService.GrantProfileShare(item.proposalItemId(),grantConsent.consentId(),grantConsent.consentVersion(),item.version(),List.of("majorCode","skills"),grantConsent.expiresAt()));
        var projection=shares.read(as(4),id(6),id(21),share.shareId(),List.of("majorCode","skills","email"),"INTERNSHIP_SELECTION");
        assertEquals(Set.of("majorCode","skills"),projection.data().keySet());assertFalse(projection.data().containsKey("email"));
        // QA01 T1-05-TC10: real HTTP access before student consent is revoked.
        qa01Tc10PrivateProfileAccessBeforeRevoke(share.shareId());
        consents.revoke(as(6),grantConsent.consentId(),key(),new vn.utbridora.auth.modules.student.application.StudentConsentService.Revoke(1L,"STUDENT_REVOKED"));
        final var enterprise=as(4);assertEquals(403,assertThrows(OperationFailure.class,()->shares.read(enterprise,id(6),id(21),share.shareId(),List.of("majorCode"),"INTERNSHIP_SELECTION")).status);
        assertEquals(placements,jdbc.queryForObject("SELECT COUNT(*) FROM placements",Integer.class));

        // Existing T3 Case/Placement is an explicitly synthetic prerequisite. New Task/acceptance is real.
        var mentor=as(5);String selected=workspace.assignments(mentor).stream().filter(a->a.kind().equals("MENTOR")).findFirst().orElseThrow().id();context(selected);
        String group=jdbc.queryForObject("SELECT id FROM internship_groups LIMIT 1",String.class),caseId=jdbc.queryForObject("SELECT id FROM internship_cases LIMIT 1",String.class);
        var task=workspace.createTask(mentor,key(),new vn.utbridora.auth.modules.internship.api.TaskDtos.CreateTask(UUID.fromString(group),"W2 integration task","Real task from assigned Mentor",Instant.now().plusSeconds(3600),vn.utbridora.auth.modules.internship.api.TaskDtos.TaskPriority.NORMAL,List.of(UUID.fromString(caseId)),true));
        assertEquals(1,task.assignments().size());
        var intern=as(7);var started=taskActions.start(intern,task.assignments().getFirst().assignmentId().toString(),key(),new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceTaskActions.StartAssignment(1L,"IN_PROGRESS"));
        assertEquals("IN_PROGRESS",started.state().name());assertEquals(1,taskActions.calendar(intern,Instant.now(),Instant.now().plusSeconds(86400),null,0,25).content().size());
        assertEquals(409,assertThrows(OperationFailure.class,()->taskActions.start(intern,started.assignmentId().toString(),key(),new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceTaskActions.StartAssignment(2L,"COMPLETED"))).status);

        var hrAuth=as(4);context(workspace.assignments(hrAuth).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());
        var mentorInvite=mentorInvites.issue(hrAuth,key(),new vn.utbridora.auth.tokens.MentorInvitationService.MentorInviteRequest("mentor@example.test",id(12),List.of(group),id(21),"DEMO-W2-MENTOR"));
        assertEquals("MEMBERSHIP_PENDING",mentorInvite.state());var acceptedMentor=mentorInvites.accept(as(5),mentorInvite.invitationId(),key(),new vn.utbridora.auth.tokens.MentorInvitationService.Accept(1L));assertEquals("ACCEPTED",acceptedMentor.state());
        assertEquals(2,workspace.assignments(as(5)).stream().filter(a->a.kind().equals("MENTOR")).count());
        context(workspace.assignments(as(4)).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());
        mentorInvites.revoke(as(4),mentorInvite.invitationId(),key(),new vn.utbridora.auth.tokens.MentorInvitationService.Revoke(2L,"HR_REVOKED"));
        assertEquals(1,workspace.assignments(as(5)).stream().filter(a->a.kind().equals("MENTOR")).count());
        httpGuards(demand,matched.summary().run().matchRunId(),invitation.invitationId(),share.shareId(),task.taskId().toString());
        // QA01 T1-05-TC11: tampered DTO fields, resource identity and side-effect guards.
        qa01Tc11RejectForgedIdentityAndCrossScope(invitation.invitationId(),
                matched.summary().run().matchRunId());
        if(Boolean.getBoolean("frontend.e2e")) {
            var d=demandData.data();var browserDemand=demands.create(as(4),id(12),key(),new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput(d.schoolOrgId(),d.termId(),"W2 browser cooperation",d.quantity(),d.description(),d.location(),d.benefits(),d.deadline(),d.required(),d.preferred(),d.academicCatalogVersion(),d.taxonomyVersion()));
            cooperations.submitWithCooperation(as(4),id(12),key(),new CooperationService.Submit(browserDemand.demandId(),browserDemand.version()));
            browser();
        }
        consentEdges(invitation.invitationId(),share.shareId());
        cooperationEdges(demandData.data());
enterpriseIsolationTC03(
    demandData.data(),
    sent.cooperation().cooperationId()
);
        invitationEdges(demandData.data());
        handoverBoundary(group,caseId,task.taskId());
        // QA01 T1-04-TC08: global ADMIN must not inherit business access or self-bootstrap.
        adminBusinessAndSelfBootstrapTC08(matched.summary().run().matchRunId());
        delegationRevocationBoundary(grant,matched.summary().run().matchRunId());
    }
    private static void context(String id) {var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("X-Context-Assignment-Id",id);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));}
    private void cooperationEdges(vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput input) throws Exception {
        var draft=demands.create(as(4),id(12),key(),input);
        var submitted=cooperations.submitWithCooperation(as(4),id(12),key(),new CooperationService.Submit(draft.demandId(),1L));String id=submitted.cooperation().cooperationId();
        var changes=cooperations.decide(as(2),id(11),id,key(),new CooperationService.Decision(2L,"REQUEST_INFO","MISSING_INFORMATION","DEMO-REVISE",List.of("description")));
        assertEquals("NEEDS_CHANGES",changes.state());assertEquals("DRAFT",jdbc.queryForObject("SELECT state FROM demands WHERE id=?",String.class,draft.demandId()));
        var changed=demands.update(as(4),id(12),draft.demandId(),key(),new vn.utbridora.auth.modules.cooperation.domain.DemandModels.UpdateDemand(3L,input));
        var resubmitted=cooperations.submit(as(4),id(12),id,key(),new CooperationService.Resubmit(changes.version(),changed.version()));assertEquals(2,resubmitted.submissionNo());
        var rejected=cooperations.decide(as(2),id(11),id,key(),new CooperationService.Decision(resubmitted.version(),"REJECT","SCHOOL_REJECTED","DEMO-REJECT",List.of()));
        var reopened=cooperations.reopen(as(4),id(12),id,key(),new CooperationService.Reopen(rejected.version(),"ENTERPRISE_REOPENED"));assertEquals("DRAFT",reopened.state());
        long version=jdbc.queryForObject("SELECT version FROM demands WHERE id=?",Long.class,draft.demandId());
        var third=cooperations.submit(as(4),id(12),id,key(),new CooperationService.Resubmit(reopened.version(),version));assertEquals(3,third.submissionNo());
        cooperations.decide(as(2),id(11),id,key(),new CooperationService.Decision(third.version(),"APPROVE",null,"DEMO-FINAL-APPROVE",List.of()));
        var history=cooperations.history(as(2),id(11),id,0,25);assertEquals(3,history.totalElements());assertEquals(List.of("APPROVE","REJECT","REQUEST_INFO"),history.content().stream().map(CooperationService.SubmissionHistory::decision).toList());
        assertEquals(2,history.content().getLast().demandVersion(),"First submitted snapshot must remain immutable after revisions");
        var rollbackDraft=demands.create(as(4),id(12),key(),input);final var author=as(4);String operation=key();
        String prior=jdbc.queryForList("SELECT DISTINCT resource_id FROM audit_logs WHERE action='COOPERATION_SUBMITTED'",String.class).stream().map(value->"'"+UUID.fromString(value)+"'").collect(java.util.stream.Collectors.joining(","));
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT week2_audit_failure CHECK(action<>'COOPERATION_SUBMITTED' OR resource_id IN ("+prior+"))");
        try {
            assertThrows(RuntimeException.class,()->cooperations.submitWithCooperation(author,id(12),operation,new CooperationService.Submit(rollbackDraft.demandId(),1L)));
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK week2_audit_failure");}
        assertEquals("DRAFT",jdbc.queryForObject("SELECT state FROM demands WHERE id=?",String.class,rollbackDraft.demandId()));assertNull(jdbc.queryForObject("SELECT cooperation_id FROM demands WHERE id=?",String.class,rollbackDraft.demandId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM cooperations WHERE demand_id=?",Integer.class,rollbackDraft.demandId()));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",Integer.class,operation));
        // QA01 T1-05-TC09: separate fresh fixture; verify replay/version guards in T5.
        qa01Tc09CooperationReplayAndVersions(input);
        qa01Tc09HttpErrorContract(input);
    }

    /**
     * QA01 / T1-05-TC09 (R02/G02, R19/G19, R27/G27), service/ledger portion.
     * Covers stale version, successful atomic submit, same-key replay, and key reuse
     * with a changed payload. HTTP error envelope is a separate unverified gap.
     */
    private void qa01Tc09CooperationReplayAndVersions(
            vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput input) {
        var draft = demands.create(as(4), id(12), key(), input);
        var hr = as(4);
        String enterpriseId = id(12);
        String demandId = draft.demandId();
        long expectedVersion = draft.version();

        String staleKey = key();
        var stale = assertThrows(OperationFailure.class,
                () -> cooperations.submitWithCooperation(hr, enterpriseId, staleKey,
                        new CooperationService.Submit(demandId, expectedVersion + 1)));
        assertEquals(409, stale.status, "TC09: stale demand version must return 409");
        assertEquals("VERSION_CONFLICT", stale.code, "TC09: stale demand version code");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooperations WHERE demand_id=?", Integer.class, demandId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?", Integer.class, staleKey),
                "TC09: failed mutation must not reserve an idempotency key");

        String successKey = key();
        var validRequest = new CooperationService.Submit(demandId, expectedVersion);
        var submitted = cooperations.submitWithCooperation(hr, enterpriseId, successKey, validRequest);
        assertEquals("SUBMITTED", submitted.cooperation().state());
        assertEquals(demandId, submitted.cooperation().demandId());
        assertEquals(submitted, cooperations.submitWithCooperation(hr, enterpriseId, successKey, validRequest),
                "TC09: identical payload and key must replay the original response");

        var differentPayload = assertThrows(OperationFailure.class,
                () -> cooperations.submitWithCooperation(hr, enterpriseId, successKey,
                        new CooperationService.Submit(demandId, expectedVersion + 1)));
        assertEquals(409, differentPayload.status, "TC09: key reuse with new payload must be 409");
        assertEquals("IDEMPOTENCY_KEY_REUSED", differentPayload.code,
                "TC09: key reuse with new payload must be refused");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooperations WHERE demand_id=?", Integer.class, demandId),
                "TC09: replay and rejected request must not create another cooperation");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?", Integer.class, successKey),
                "TC09: one idempotency ledger record for a successful command");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooperation_history WHERE cooperation_id=? AND operation='SUBMIT'",
                Integer.class, submitted.cooperation().cooperationId()),
                "TC09: replay must not duplicate a submission history event");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='COOPERATION_SUBMITTED' AND resource_id=?",
                Integer.class, submitted.cooperation().cooperationId()),
                "TC09: replay must not duplicate the success audit");
    }
    /**
     * QA01 / T1-05-TC09 HTTP acceptance: check the CooperationController's
     * actual response envelope and server-generated request correlation.
     * Deliberately exercises only real, published Cooperation endpoints;
     * do not assume every legacy API has the new C.0 envelope.
     */
    private void qa01Tc09HttpErrorContract(
            vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput input) throws Exception {
        HttpSession hrSession = login("hr@example.test", "Synthetic-week2-test-password-123");
        String enterprise = id(12);

        // Invalid schema is rejected BEFORE the cooperation command is executed.
        String invalidKey = key();
        var malformed = qa01Tc09Post(hrSession, "/enterprise/cooperations", "{}", enterprise, invalidKey);
        qa01Tc09AssertErrorEnvelope(malformed, 400, "SCHEMA_VALIDATION");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?", Integer.class, invalidKey),
                "TC09 HTTP: invalid schema must not reserve an idempotency key");

        // Existing valid demand, but forged actorId in JSON is not a public DTO field.
        var draft = demands.create(as(4), enterprise, key(), input);
        String demandId = draft.demandId();
        long version = draft.version();
        String forgedKey = key();
        String forgedActor = "{\"demandId\":\"" + demandId
                + "\",\"expectedDemandVersion\":" + version
                + ",\"actorId\":\"" + id(1) + "\"}";
        var forged = qa01Tc09Post(hrSession, "/enterprise/cooperations", forgedActor, enterprise, forgedKey);
        qa01Tc09AssertErrorEnvelope(forged, 400, "SCHEMA_VALIDATION");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooperations WHERE demand_id=?", Integer.class, demandId),
                "TC09 HTTP: rejected unknown DTO field must not create a cooperation");

        // Stale demand version must return a typed 409, not generic legacy CONFLICT.
        String staleKey = key();
        String staleJson = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(
                new CooperationService.Submit(demandId, version + 1));
        var stale = qa01Tc09Post(hrSession, "/enterprise/cooperations", staleJson, enterprise, staleKey);
        qa01Tc09AssertErrorEnvelope(stale, 409, "VERSION_CONFLICT");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooperations WHERE demand_id=?", Integer.class, demandId),
                "TC09 HTTP: stale version must not create a cooperation");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?", Integer.class, staleKey),
                "TC09 HTTP: stale version must not reserve a key");

        // Replaying the same valid HTTP command returns the original creation result.
        String successKey = key();
        String validJson = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(
                new CooperationService.Submit(demandId, version));
        var created = qa01Tc09Post(hrSession, "/enterprise/cooperations", validJson, enterprise, successKey);
        assertEquals(201, created.statusCode(), "TC09 HTTP: valid cooperation create status");
        var createdBody = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(created.body());
        assertEquals(demandId, createdBody.get("demandId").asString(), "TC09 HTTP: created demand reference");
        String cooperationId = createdBody.get("cooperationId").asString();
        assertDoesNotThrow(() -> UUID.fromString(cooperationId), "TC09 HTTP: cooperation ID must be UUID");

        var replay = qa01Tc09Post(hrSession, "/enterprise/cooperations", validJson, enterprise, successKey);
        assertEquals(201, replay.statusCode(), "TC09 HTTP: identical replay HTTP status");
        var replayBody = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(replay.body());
        assertEquals(cooperationId, replayBody.get("cooperationId").asString(),
                "TC09 HTTP: replay must reference original cooperation");

        // Same idempotency key + changed payload must return 409 and do no mutation.
        var reused = qa01Tc09Post(hrSession, "/enterprise/cooperations", staleJson, enterprise, successKey);
        qa01Tc09AssertErrorEnvelope(reused, 409, "IDEMPOTENCY_KEY_REUSED");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM cooperations WHERE demand_id=?", Integer.class, demandId),
                "TC09 HTTP: replay/key reuse must not create duplicate cooperations");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?", Integer.class, successKey),
                "TC09 HTTP: replay/key reuse must not create a second ledger row");
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='COOPERATION_DRAFT_SAVED' AND resource_id=?",
                Integer.class, cooperationId),
                "TC09 HTTP: replay/key reuse must not duplicate a success audit event");
    }

    private java.net.http.HttpResponse<String> qa01Tc09Post(
            HttpSession session, String path, String json, String organizationId, String idempotencyKey)
            throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("X-Organization-Id", organizationId)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-ID", "qa01-forged-client-id")
                .header(session.header(), session.token())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                .build();
        return session.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private void qa01Tc09AssertErrorEnvelope(
            java.net.http.HttpResponse<String> response, int expectedStatus, String expectedCode)
            throws Exception {
        assertEquals(expectedStatus, response.statusCode(), "TC09 HTTP status; body=" + response.body());
        var body = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(response.body());
        assertEquals(expectedCode, body.get("errorCode").asString(), "TC09 HTTP errorCode");
        assertEquals(expectedCode, body.get("error").asString(), "TC09 HTTP legacy-alias error");
        assertNotNull(body.get("message"), "TC09 HTTP message must be present");
        assertFalse(body.get("message").asString().isBlank(), "TC09 HTTP message must not be empty");
        String requestId = body.get("requestId").asString();
        assertDoesNotThrow(() -> UUID.fromString(requestId), "TC09 HTTP server requestId must be UUID");
        assertNotEquals("qa01-forged-client-id", requestId,
                "TC09 HTTP must not trust client-supplied X-Request-ID");
        assertEquals(requestId, response.headers().firstValue("X-Request-ID").orElseThrow(),
                "TC09 HTTP response header/body requestId must match");
        assertTrue(body.get("fieldErrors").isArray(), "TC09 HTTP fieldErrors must be an array");
        assertFalse(response.body().contains("Synthetic-week2-test-password-123"),
                "TC09 HTTP must not expose authentication secrets");
    }

    /**
     * QA01 / T1-05-TC06 (R02/G02, R27/G27): the real T2 owner ports must
     * accept the correct school/term/recipient/version and deny altered scope.
     * Uses the already-approved student and the live consent created above;
     * neither production data nor direct mutation of owner records is needed.
     */
    private void qa01OwnerPortsScopeTC06(
            vn.utbridora.auth.modules.student.port.StudentConsentPort.ConsentGrant grant) {
        var eligibilityPort = (vn.utbridora.auth.modules.student.port.StudentEligibilityPort) eligibility;
        var consentPort = (vn.utbridora.auth.modules.student.port.StudentConsentPort) consents;
        Authentication school = as(2);
        Long eligibilityVersionBefore = jdbc.queryForObject(
                "SELECT version FROM student_term_eligibilities WHERE user_id=? AND term_id=?",
                Long.class, id(6), id(21));

        // Correct delegated school + active term + approved student: accepted.
        transactions.executeWithoutResult(tx ->
                eligibilityPort.assertEligibleForNomination(school, id(6), id(11), id(21)));
        // Same port must reject a different school, term or student ID.
        assertEquals(404, assertThrows(OperationFailure.class, () ->
                transactions.executeWithoutResult(tx ->
                        eligibilityPort.assertEligibleForNomination(school, id(6), id(12), id(21)))).status,
                "TC06: eligibility must reject a foreign school scope");
        assertEquals(404, assertThrows(OperationFailure.class, () ->
                transactions.executeWithoutResult(tx ->
                        eligibilityPort.assertEligibleForNomination(school, id(6), id(11), key()))).status,
                "TC06: eligibility must reject a foreign term scope");
        assertEquals(404, assertThrows(OperationFailure.class, () ->
                transactions.executeWithoutResult(tx ->
                        eligibilityPort.assertEligibleForNomination(school, key(), id(11), id(21)))).status,
                "TC06: eligibility must reject a foreign student");
        assertEquals(eligibilityVersionBefore, jdbc.queryForObject(
                "SELECT version FROM student_term_eligibilities WHERE user_id=? AND term_id=?",
                Long.class, id(6), id(21)),
                "TC06: eligibility port checks must not mutate approval version");

        var enterpriseActor = vn.utbridora.auth.contracts.ActorContext
                .fromAuthenticatedWorkflow(as(4), id(12));
        var validRequest = new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                grant.consentId(), grant.consentVersion(), null,
                id(6), id(11), id(21), id(12), "INTERNSHIP_SELECTION",
                List.of("majorCode", "skills"));
        var allowed = transactions.execute(tx ->
                consentPort.lockAndCheckForShare(enterpriseActor, validRequest));
        assertNotNull(allowed);
        assertTrue(allowed.valid(), "TC06: consent owner port must accept matching scope");
        assertEquals("ALLOWED", allowed.reasonCode());
        assertEquals(List.of("majorCode", "skills"), allowed.allowedFields());
        assertEquals(grant.expiresAt(), allowed.validUntil());
        assertEquals(Long.valueOf(grant.consentVersion()), allowed.currentVersion());

        Long lineageVersionBefore = jdbc.queryForObject(
                "SELECT version FROM student_consent_lineages WHERE student_user_id=? AND school_org_id=? AND term_id=? AND recipient_org_id=?",
                Long.class, id(6), id(11), id(21), id(12));

        // The same enterprise context is authorized, but altered school/term/student
        // must be denied by the owner record instead of trusting caller-provided DTOs.
        for (var invalid : List.of(
                new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                        grant.consentId(), grant.consentVersion(), null,
                        id(6), key(), id(21), id(12), "INTERNSHIP_SELECTION", List.of("skills")),
                new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                        grant.consentId(), grant.consentVersion(), null,
                        id(6), id(11), key(), id(12), "INTERNSHIP_SELECTION", List.of("skills")),
                new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                        grant.consentId(), grant.consentVersion(), null,
                        key(), id(11), id(21), id(12), "INTERNSHIP_SELECTION", List.of("skills")))) {
            var rejected = transactions.execute(tx ->
                    consentPort.lockAndCheckForShare(enterpriseActor, invalid));
            assertNotNull(rejected);
            assertFalse(rejected.valid(), "TC06: mismatched owner scope must be denied");
            assertEquals("SCOPE_MISMATCH", rejected.reasonCode());
            assertTrue(rejected.allowedFields().isEmpty());
        }

        // School actor has share-management delegation but cannot change the consent's recipient.
        var schoolActor = vn.utbridora.auth.contracts.ActorContext
                .fromAuthenticatedWorkflow(as(2), id(11));
        var wrongRecipient = new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                grant.consentId(), grant.consentVersion(), null,
                id(6), id(11), id(21), key(), "INTERNSHIP_SELECTION", List.of("skills"));
        var recipientDenied = transactions.execute(tx ->
                consentPort.lockAndCheckForShare(schoolActor, wrongRecipient));
        assertNotNull(recipientDenied);
        assertFalse(recipientDenied.valid());
        assertEquals("SCOPE_MISMATCH", recipientDenied.reasonCode());

        // Version, purpose and requested fields are validated by the same T2 port.
        var staleVersion = new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                grant.consentId(), grant.consentVersion() + 1, null,
                id(6), id(11), id(21), id(12), "INTERNSHIP_SELECTION", List.of("skills"));
        var stale = transactions.execute(tx ->
                consentPort.lockAndCheckForShare(enterpriseActor, staleVersion));
        assertNotNull(stale);
        assertFalse(stale.valid());
        assertEquals("REPLACED", stale.reasonCode(),
                "TC06: a stale consent version must not grant sharing access");

        var wrongPurpose = new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                grant.consentId(), grant.consentVersion(), null,
                id(6), id(11), id(21), id(12), "OTHER_PURPOSE", List.of("skills"));
        var purposeDenied = transactions.execute(tx ->
                consentPort.lockAndCheckForShare(enterpriseActor, wrongPurpose));
        assertNotNull(purposeDenied);
        assertFalse(purposeDenied.valid());
        assertEquals("PURPOSE_MISMATCH", purposeDenied.reasonCode());

        var extraField = new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(
                grant.consentId(), grant.consentVersion(), null,
                id(6), id(11), id(21), id(12), "INTERNSHIP_SELECTION", List.of("email"));
        var fieldDenied = transactions.execute(tx ->
                consentPort.lockAndCheckForShare(enterpriseActor, extraField));
        assertNotNull(fieldDenied);
        assertFalse(fieldDenied.valid());
        assertEquals("FIELD_DENIED", fieldDenied.reasonCode());
        assertTrue(fieldDenied.allowedFields().isEmpty());
        assertEquals(lineageVersionBefore, jdbc.queryForObject(
                "SELECT version FROM student_consent_lineages WHERE student_user_id=? AND school_org_id=? AND term_id=? AND recipient_org_id=?",
                Long.class, id(6), id(11), id(21), id(12)),
                "TC06: denied owner port checks must not advance the consent lineage");
    }

    private void consentEdges(String invitation,String oldShare) throws Exception {
        long lineage=jdbc.queryForObject("SELECT version FROM student_consent_lineages WHERE student_user_id=? AND recipient_org_id=?",Long.class,id(6),id(12));
        var current=consents.grant(as(6),key(),new vn.utbridora.auth.modules.student.application.StudentConsentService.GrantConsent(invitation,"INTERNSHIP_SELECTION",List.of("skills"),Instant.now().plusSeconds(3600),"demo-consent-v1",0L,lineage));
        final var enterprise=as(4);
        assertEquals(403,assertThrows(OperationFailure.class,()->shares.read(enterprise,id(6),id(21),oldShare,List.of("skills"),"INTERNSHIP_SELECTION")).status,"Regrant must not revive an older share");
        final var student=as(6);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var futures=new ArrayList<java.util.concurrent.Future<Boolean>>();
            for(boolean revoke:List.of(true,false)) futures.add(pool.submit(()->{gate.await();SecurityContextHolder.getContext().setAuthentication(student);try {
                if(revoke) consents.revoke(student,current.consentId(),key(),new vn.utbridora.auth.modules.student.application.StudentConsentService.Revoke(current.lineageVersion(),"STUDENT_REVOKED"));
                else consents.grant(student,key(),new vn.utbridora.auth.modules.student.application.StudentConsentService.GrantConsent(invitation,"INTERNSHIP_SELECTION",List.of("skills"),Instant.now().plusSeconds(3600),"demo-consent-v1",0L,current.lineageVersion()));
                return true;
            } catch(OperationFailure e) {assertEquals(409,e.status);return false;} finally{SecurityContextHolder.clearContext();}}));
            gate.countDown();int winners=0;for(var future:futures) if(future.get()) winners++;assertEquals(1,winners);
        } finally {pool.shutdownNow();}
        long next=jdbc.queryForObject("SELECT version FROM student_consent_lineages WHERE student_user_id=? AND recipient_org_id=?",Long.class,id(6),id(12));
        assertEquals(current.lineageVersion()+1,next);
        var expiring=consents.grant(as(6),key(),new vn.utbridora.auth.modules.student.application.StudentConsentService.GrantConsent(invitation,"INTERNSHIP_SELECTION",List.of("skills"),Instant.now().plusSeconds(2),"demo-consent-v1",0L,next));
        var context=vn.utbridora.auth.contracts.ActorContext.fromAuthenticatedWorkflow(as(4),id(12));
        var check=new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(expiring.consentId(),expiring.consentVersion(),null,id(6),id(11),id(21),id(12),"INTERNSHIP_SELECTION",List.of("skills"));
        assertTrue(transactions.execute(status->consents.lockAndCheckForShare(context,check)).valid());
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(4)).until(()->!Instant.now().isBefore(expiring.expiresAt()));
        var denied=transactions.execute(status->consents.lockAndCheckForShare(context,check));assertFalse(denied.valid());assertEquals("EXPIRED",denied.reasonCode());
        var wrongRecipient=new vn.utbridora.auth.modules.student.port.StudentConsentPort.GrantCheck(expiring.consentId(),expiring.consentVersion(),null,id(6),id(11),id(21),UUID.randomUUID().toString(),"INTERNSHIP_SELECTION",List.of("skills"));
        assertThrows(OperationFailure.class,()->transactions.execute(status->consents.lockAndCheckForShare(context,wrongRecipient)));
    }
    private vn.utbridora.auth.modules.cooperation.port.InternshipInvitationPort.InternshipInvitation offer(vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput d,Duration lifetime) {
        var created=demands.create(as(4),id(12),key(),d);
        var submitted=cooperations.submitWithCooperation(as(4),id(12),key(),new CooperationService.Submit(created.demandId(),1L));
        var approved=cooperations.decide(as(2),id(11),submitted.cooperation().cooperationId(),key(),new CooperationService.Decision(2L,"APPROVE",null,"DEMO-INVITATION-EDGE",List.of()));
        var run=matching.evaluate(as(2),key(),new MatchingService.Evaluate(created.demandId(),2L,1L));
        var batch=proposals.nominate(as(2),run.summary().run().matchRunId(),key(),new ProposalService.Nominate(List.of(id(6)),2L,1L,approved.version(),null));
        return proposals.invite(as(2),batch.items().getFirst().proposalItemId(),key(),new ProposalService.Invite(1L,lifetime==null?null:Instant.now().plus(lifetime)));
    }
    private void invitationEdges(vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput input) throws Exception {
        var invitation=offer(input,null);final var student=as(6);var pool=java.util.concurrent.Executors.newFixedThreadPool(20);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var calls=new ArrayList<java.util.concurrent.Callable<String>>();
            for(int n=0;n<20;n++) {final boolean accept=n%2==0;calls.add(()->{gate.await();SecurityContextHolder.getContext().setAuthentication(student);try{return proposals.decide(student,invitation.invitationId(),key(),new ProposalService.InvitationDecision(accept?"ACCEPT":"DECLINE",1L,accept?null:"STUDENT_DECLINED",null)).state();}catch(OperationFailure e){assertEquals(409,e.status);return "CONFLICT";}finally{SecurityContextHolder.clearContext();}});}
            var futures=calls.stream().map(pool::submit).toList();gate.countDown();int winners=0;
            for(var future:futures) if(!future.get().equals("CONFLICT")) winners++;
            assertEquals(1,winners);assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='INVITATION_RESPONDED' AND resource_id=?",Integer.class,invitation.invitationId()));
        } finally {pool.shutdownNow();}
        var expiring=offer(input,Duration.ofSeconds(3));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(5)).until(()->!Instant.now().isBefore(expiring.expiresAt()));
        final var owner=as(6);
        assertEquals("INVITATION_EXPIRED",assertThrows(OperationFailure.class,()->proposals.decide(owner,expiring.invitationId(),key(),new ProposalService.InvitationDecision("ACCEPT",1L,null,null))).code);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='INVITATION_RESPONDED' AND resource_id=?",Integer.class,expiring.invitationId()));
        final var school=as(2);assertEquals(404,assertThrows(OperationFailure.class,()->proposals.decide(school,expiring.invitationId(),key(),new ProposalService.InvitationDecision("DECLINE",1L,"SCHOOL_CANNOT_ACCEPT",null))).status);
    }
    private void handoverBoundary(String oldGroup,String caseId,UUID task) {
        String next=UUID.randomUUID().toString(),third=UUID.randomUUID().toString(),cohort=jdbc.queryForObject("SELECT cohort_id FROM internship_groups WHERE id=?",String.class,oldGroup);
        // Two empty destination groups are fixtures; move, acceptance and handover use actual owner services.
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES(?,?,?,1)",next,cohort,"W2-HANDOVER-2");jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES(?,?,?,1)",third,cohort,"W2-HANDOVER-3");
        var hr=as(4);context(workspace.assignments(hr).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());
        var invitation=mentorInvites.issue(hr,key(),new vn.utbridora.auth.tokens.MentorInvitationService.MentorInviteRequest("mentor@example.test",id(12),List.of(next),id(21),"DEMO-HANDOVER-ACCEPT"));mentorInvites.accept(as(5),invitation.invitationId(),key(),new vn.utbridora.auth.tokens.MentorInvitationService.Accept(1L));
        hr=as(4);context(workspace.assignments(hr).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());
        long caseVersion=jdbc.queryForObject("SELECT version FROM internship_cases WHERE id=?",Long.class,caseId),groupVersion=jdbc.queryForObject("SELECT version FROM internship_groups WHERE id=?",Long.class,oldGroup);
        var moved=workspace.move(hr,UUID.fromString(oldGroup),key(),new vn.utbridora.auth.modules.internship.api.CohortGroupDtos.MoveCase(UUID.fromString(caseId),UUID.fromString(next),caseVersion,groupVersion,1L,"HR_MOVED"));
        final var before=as(5);String assignment=workspace.assignments(before).stream().filter(a->next.equals(a.scope().groupId())).findFirst().orElseThrow().id();context(assignment);
        assertThrows(vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.class,()->workspace.readTask(before,task));
        hr=as(4);context(workspace.assignments(hr).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());
        var handover=assignments.handover(hr,caseId,key(),new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAssignmentService.Handover(caseId,id(5),moved.caseRef().version(),"HR_HANDOVER"));
        var after=as(5);context(assignment);assertEquals(task,workspace.readTask(after,task).taskId());
        hr=as(4);context(workspace.assignments(hr).stream().filter(a->a.kind().equals("HR")).findFirst().orElseThrow().id());workspace.move(hr,UUID.fromString(next),key(),new vn.utbridora.auth.modules.internship.api.CohortGroupDtos.MoveCase(UUID.fromString(caseId),UUID.fromString(third),handover.version(),2L,1L,"HR_MOVED_AGAIN"));
        final var former=as(5);context(assignment);assertThrows(vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.class,()->workspace.readTask(former,task));
    }
    private void delegationRevocationBoundary(SchoolDelegationService.Delegation parent,String run) throws Exception {
        var admin=as(1);var subject=new vn.utbridora.auth.user.UserAccount();subject.id=id(22);subject.email="week2-delegate@example.test";subject.displayName="Synthetic delegated reviewer";subject.passwordHash=passwords.encode("Synthetic-week2-delegate-password-123");subject.status=vn.utbridora.auth.user.AccountStatus.ACTIVE;users.saveAndFlush(subject);
        memberships.grant(id(1),subject.id,vn.utbridora.auth.user.Role.SCHOOL_STAFF,id(11));
        delegations.grant(as(2),id(21),key(),new SchoolDelegationService.Grant(subject.id,List.of("matching:run:execute"),List.of(),Instant.now(),Instant.now().plusSeconds(1800),"DEMO-W2-EXECUTE-ONLY"));
        String demand=jdbc.queryForObject("SELECT demand_id FROM match_runs WHERE id=?",String.class,run);long version=jdbc.queryForObject("SELECT version FROM demands WHERE id=?",Long.class,demand);
                var executeOnly = matching.evaluate(as(22), key(),
            new MatchingService.Evaluate(demand, version, 1L));
        assertEquals("COMPLETED", executeOnly.summary().run().state());

        String executeOnlyRun = executeOnly.summary().run().matchRunId();
        String detailsPath = "/matching/runs/" + executeOnlyRun + "/details?overallStatus=MATCH";
        String delegatePassword = "Synthetic-week2-delegate-password-123";

        var beforeGrant = login(subject.email, delegatePassword);
        assertEquals(404, http(beforeGrant, "GET", detailsPath, null, false, null),
            "TC04: Staff with execute-only grant must not read matching details");
String rejectedDecisionRef = "DEMO-W2-TC05-EXPIRY";
String grantAuditCountSql =
        "SELECT COUNT(*) FROM audit_logs WHERE action = 'CAPABILITY_GRANTED'";
Integer grantAuditCountBefore = jdbc.queryForObject(grantAuditCountSql, Integer.class);

var expiryDenied = assertThrows(OperationFailure.class, () ->
        delegations.grant(as(2), id(21), key(), new SchoolDelegationService.Grant(
                subject.id, List.of("matching:school:detail_read"), List.of(),
                Instant.now(), parent.validUntil().plusSeconds(1), rejectedDecisionRef)));

assertEquals(403, expiryDenied.status);
assertEquals("ACCESS_DENIED", expiryDenied.code);
assertEquals(0, jdbc.queryForObject(
        "SELECT COUNT(*) FROM school_delegations WHERE decision_ref = ?",
        Integer.class, rejectedDecisionRef));
assertEquals(grantAuditCountBefore, jdbc.queryForObject(grantAuditCountSql, Integer.class));
        String apiDecisionRef = "DEMO-W2-TC05-HTTP-EXPIRY";
        var schoolSession = login("school@example.test", qaWeek2DemoPassword);
        String apiGrantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", subject.id,
                "permissionCodes", List.of("matching:school:detail_read"),
                "profileReadFields", List.of(),
                "validFrom", Instant.now().toString(),
                "validUntil", parent.validUntil().plusSeconds(1).toString(),
                "decisionRef", apiDecisionRef));

        assertEquals(403, http(schoolSession, "POST",
                "/school/terms/" + id(21) + "/delegations", apiGrantBody, true, null),
                "TC05: API must reject a grant that outlasts its parent");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations WHERE decision_ref = ?",
                Integer.class, apiDecisionRef));
        assertEquals(grantAuditCountBefore, jdbc.queryForObject(grantAuditCountSql, Integer.class));
        String permissionDecisionRef = "DEMO-W2-TC05-HTTP-PERMISSION";
        String permissionGrantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", subject.id,
                "permissionCodes", List.of("enterprise_verification:school:review"),
                "profileReadFields", List.of(),
                "validFrom", Instant.now().toString(),
                "validUntil", parent.validUntil().minusSeconds(1).toString(),
                "decisionRef", permissionDecisionRef));

        assertEquals(403, http(schoolSession, "POST",
                "/school/terms/" + id(21) + "/delegations", permissionGrantBody, true, null),
                "TC05: API must reject a permission missing from the parent grant");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations WHERE decision_ref = ?",
                Integer.class, permissionDecisionRef));
        assertEquals(grantAuditCountBefore, jdbc.queryForObject(grantAuditCountSql, Integer.class));
        String fieldDecisionRef = "DEMO-W2-TC05-HTTP-FIELD";
        String fieldGrantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", subject.id,
                "permissionCodes", List.of("student_profile:school:read"),
                "profileReadFields", List.of("majorCode"),
                "validFrom", Instant.now().toString(),
                "validUntil", parent.validUntil().minusSeconds(1).toString(),
                "decisionRef", fieldDecisionRef));

        assertEquals(403, http(schoolSession, "POST",
                "/school/terms/" + id(21) + "/delegations", fieldGrantBody, true, null),
                "TC05: API must reject a profile field missing from the parent grant");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations WHERE decision_ref = ?",
                Integer.class, fieldDecisionRef));
        assertEquals(grantAuditCountBefore, jdbc.queryForObject(grantAuditCountSql, Integer.class));

              String validDecisionRef = "DEMO-W2-CHILD";
        String validGrantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", subject.id,
                "permissionCodes", List.of("matching:school:detail_read"),
                "profileReadFields", List.of(),
                "validFrom", Instant.now().toString(),
                "validUntil", parent.validUntil().minusSeconds(1).toString(),
                "decisionRef", validDecisionRef));
        String otherTermId = key();
        jdbc.update("""
                INSERT INTO terms(id, school_org_id, term_code, start_date, end_date,
                                  status, version, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_DATE - INTERVAL 1 DAY,
                        CURRENT_DATE + INTERVAL 30 DAY, 'ACTIVE', 1,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, otherTermId, id(11), "QA01-W2-OTHER-TERM");

        assertEquals(404, http(schoolSession, "POST",
                "/school/terms/" + otherTermId + "/delegations", validGrantBody, true, null),
                "TC05: API must reject a grant in another term of the same school");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations WHERE term_id = ?",
                Integer.class, otherTermId));
        assertEquals(grantAuditCountBefore, jdbc.queryForObject(grantAuditCountSql, Integer.class));
        String foreignSchoolId = key();
        String foreignTermId = key();
        jdbc.update("""
                INSERT INTO organizations(id, name, type, status, created_at)
                VALUES (?, ?, 'UNIVERSITY', 'VERIFIED', UTC_TIMESTAMP(6))
                """, foreignSchoolId, "QA01 TC05 Foreign School");

        jdbc.update("""
                INSERT INTO terms(id, school_org_id, term_code, start_date, end_date,
                                  status, version, created_at, updated_at)
                VALUES (?, ?, ?, CURRENT_DATE - INTERVAL 1 DAY,
                        CURRENT_DATE + INTERVAL 30 DAY, 'ACTIVE', 1,
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, foreignTermId, foreignSchoolId, "QA01-TC05-FOREIGN-TERM");

        assertEquals(404, http(schoolSession, "POST",
                "/school/terms/" + foreignTermId + "/delegations", validGrantBody, true, null),
                "TC05: API must reject a grant in another school");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations WHERE term_id = ?",
                Integer.class, foreignTermId));
        assertEquals(grantAuditCountBefore, jdbc.queryForObject(grantAuditCountSql, Integer.class));

        assertEquals(201, http(schoolSession, "POST",
                "/school/terms/" + id(21) + "/delegations", validGrantBody, true, null),
                "TC05: API must accept a grant within its parent's validity");
        String childId = jdbc.queryForObject(
                "SELECT id FROM school_delegations WHERE decision_ref = ?",
                String.class, validDecisionRef);
        assertNotNull(childId);
        var session = login(subject.email, delegatePassword);
        assertEquals(200, http(session, "GET", detailsPath, null, false, null),
            "TC04: The same staff can read the same run after receiving detail_read");

        matchingDetailsStayWithinSchool(session, run);
        // QA01 T1-04-TC07: verify grant access for ACTIVE, EXPIRED, and FUTURE periods.
        delegationValidityTC07(schoolSession, detailsPath, parent);
        // QA01 T1-05-TC01: verify successful HTTP grant/revoke audit, metadata and request correlation.
        auditWorkflowMetadataTC01(schoolSession, subject.id, parent);
        // QA01 T1-05-TC02: synthetic sensitive canaries must not be saved in audit rows.
        auditSensitiveDataTC02(subject.id, parent);
        admin=as(1);delegations.revoke(admin,parent.id(),key(),new SchoolDelegationService.Revoke(parent.version(),"ROOT_REVOKED"));
        assertEquals(401,http(session,"GET","/matching/runs/"+run+"/details?overallStatus=MATCH",null,false,null));
        var newSession=login(subject.email,"Synthetic-week2-delegate-password-123");assertEquals(404,http(newSession,"GET","/matching/runs/"+run+"/details?overallStatus=MATCH",null,false,null));
        assertTrue(jdbc.queryForObject("SELECT active FROM school_delegations WHERE id=?",Boolean.class,childId),"Child row may remain active but its revoked parent must deny access");
        var hr=login("hr@example.test","Synthetic-week2-test-password-123");String coordinator=jdbc.queryForObject("SELECT id FROM internship_context_assignments WHERE kind='COORDINATOR'",String.class);
        assignments.revoke(as(2),coordinator,key(),new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAssignmentService.Revoke(1L,"SCHOOL_REVOKED"));
        assertEquals(401,http(hr,"GET","/t3/context-assignments/me",null,false,null));assertEquals(0,workspace.assignments(as(4)).size());assertEquals(0,workspace.assignments(as(5)).size());
    }
    /** QA01 T1-05-TC01: compare actual HTTP request IDs with persisted Grant/Revoke audit events. */
    private void auditWorkflowMetadataTC01(HttpSession schoolSession, String recipient,
                                           SchoolDelegationService.Delegation parent) throws Exception {
        String decisionRef = "DEMO-W2-T1-05-TC01-" + key();
        Instant from = Instant.now().minusSeconds(5);
        Instant until = Instant.now().plusSeconds(900);
        assertFalse(from.isBefore(parent.validFrom()), "TC01: grant must remain within parent start");
        assertFalse(until.isAfter(parent.validUntil()), "TC01: grant must remain within parent end");

        String grantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", recipient,
                "permissionCodes", List.of("matching:school:detail_read"),
                "profileReadFields", List.of(),
                "validFrom", from.toString(),
                "validUntil", until.toString(),
                "decisionRef", decisionRef));
        Instant grantBefore = Instant.now();
        var granted = auditHttpTC01(schoolSession, "POST",
                "/school/terms/" + id(21) + "/delegations", grantBody);
        Instant grantAfter = Instant.now();
        assertEquals(201, granted.statusCode(), "TC01: HTTP grant must succeed: " + granted.body());
        String child = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(granted.body())
                .get("id").asString();
        assertEquals(child, jdbc.queryForObject(
                "SELECT id FROM school_delegations WHERE decision_ref=?", String.class, decisionRef));
        assertEquals(1, jdbc.queryForObject(
                "SELECT version FROM school_delegations WHERE id=?", Integer.class, child));

        assertAuditWorkflowTC01("CAPABILITY_GRANTED", child, id(2), id(11), granted,
                grantBefore, grantAfter,
                Map.of("scopeKind", "SCHOOL", "scopeId", id(11), "termId", id(21),
                       "assignmentKind", "SCHOOL", "validFrom", from.toString(),
                       "validUntil", until.toString(), "decisionRef", decisionRef));

        String reason = "QA01_REVOKED";
        String revokeBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "expectedVersion", 1, "reasonCode", reason));
        Instant revokeBefore = Instant.now();
        var revoked = auditHttpTC01(schoolSession, "DELETE",
                "/school/delegations/" + child, revokeBody);
        Instant revokeAfter = Instant.now();
        assertEquals(200, revoked.statusCode(), "TC01: HTTP revoke must succeed: " + revoked.body());
        assertEquals(2, jdbc.queryForObject(
                "SELECT version FROM school_delegations WHERE id=?", Integer.class, child));
        assertFalse(jdbc.queryForObject(
                "SELECT active FROM school_delegations WHERE id=?", Boolean.class, child));

        assertAuditWorkflowTC01("CAPABILITY_REVOKED", child, id(2), id(11), revoked,
                revokeBefore, revokeAfter,
                Map.of("scopeKind", "SCHOOL", "scopeId", id(11), "termId", id(21),
                       "assignmentKind", "SCHOOL", "reasonCode", reason));
    }

    private java.net.http.HttpResponse<String> auditHttpTC01(HttpSession session,
            String method, String path, String body) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key())
                .header(session.header(), session.token())
                .header("X-Request-ID", "untrusted-qa01-client-id")
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build();
        return session.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private void assertAuditWorkflowTC01(String action, String resourceId, String expectedActor,
            String expectedOrg, java.net.http.HttpResponse<String> response,
            Instant started, Instant ended, Map<String,String> expectedMetadata) {
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertNotEquals("untrusted-qa01-client-id", requestId,
                "TC01: request ID must be generated by server");
        assertDoesNotThrow(() -> UUID.fromString(requestId),
                "TC01: server request ID must be a UUID");
        var records = jdbc.queryForList("""
                SELECT actor_id, action, resource_type, resource_id, result,
                       organization_id, request_id, event_version, metadata_json, created_at
                FROM audit_logs WHERE action=? AND resource_id=?
                """, action, resourceId);
        assertEquals(1, records.size(), "TC01: exactly one successful audit per HTTP workflow");
        var row = records.getFirst();
        assertEquals(expectedActor, row.get("actor_id"), "TC01: actor ID");
        assertEquals(action, row.get("action"), "TC01: action");
        assertEquals("capability_assignment", row.get("resource_type"), "TC01: resource type");
        assertEquals(resourceId, row.get("resource_id"), "TC01: resource ID");
        assertEquals("SUCCESS", row.get("result"), "TC01: result");
        assertEquals(expectedOrg, row.get("organization_id"), "TC01: organization ID");
        assertEquals(requestId, row.get("request_id"), "TC01: HTTP request correlation");
        assertEquals(1, ((Number)row.get("event_version")).intValue(), "TC01: audit schema version");

        // JdbcTemplate returns DATETIME as LocalDateTime with MySQL Connector/J.
        // The AuditLog entity writes an Instant in UTC; accept Timestamp as a fallback.
        Object createdAtValue = row.get("created_at");
        Instant created;
        if (createdAtValue instanceof LocalDateTime dateTime) {
            created = dateTime.toInstant(ZoneOffset.UTC);
        } else if (createdAtValue instanceof java.sql.Timestamp timestamp) {
            created = timestamp.toInstant();
        } else {
            throw new AssertionError("TC01: unexpected created_at type: " +
                    (createdAtValue == null ? "null" : createdAtValue.getClass().getName()));
        }
        assertFalse(created.isBefore(started.minusSeconds(5)), "TC01: audit timestamp too early");
        assertFalse(created.isAfter(ended.plusSeconds(5)), "TC01: audit timestamp too late");
        var metadata = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(
                String.valueOf(row.get("metadata_json")));
        assertEquals(expectedMetadata.size(), metadata.size(), "TC01: metadata allowlist/field count");
        expectedMetadata.forEach((field, value) -> {
            assertNotNull(metadata.get(field), "TC01: missing audit field " + field);
            assertEquals(value, metadata.get(field).asString(), "TC01: metadata field " + field);
        });
    }

    /**
     * QA01 T1-05-TC02: exercise the real HTTP Grant/Revoke flows with synthetic
     * secret-like values in an extra request header and a benign extra cookie.
     * Check the actually persisted audit rows, then reject non-allowlisted
     * workflow metadata in the centralized AuditService without a side effect.
     * Never use actual passwords, cookies, or tokens as test input.
     */
    private void auditSensitiveDataTC02(String recipient,
            SchoolDelegationService.Delegation parent) throws Exception {
        String tag = UUID.randomUUID().toString().replace("-", "");
        String passwordCanary = "QA01_TC02_PASSWORD_" + tag;
        String tokenCanary = "QA01_TC02_TOKEN_" + tag;
        String cookieCanary = "QA01_TC02_COOKIE_" + tag;
        String bodyCanary = "QA01_TC02_BODY_" + tag;
        String piiCanary = "QA01_TC02_PII_" + tag;
        var canaries = List.of(passwordCanary, tokenCanary, cookieCanary, bodyCanary, piiCanary);
        String headerCanary = String.join("_", canaries);

        var privacySession = login("school@example.test", qaWeek2DemoPassword);
        // Actual additional (synthetic) Cookie header; the normal session cookie remains intact.
        var cookies = (java.net.CookieManager) privacySession.client().cookieHandler().orElseThrow();
        var probeCookie = new java.net.HttpCookie("QA01_TC02", cookieCanary);
        probeCookie.setPath("/");
        cookies.getCookieStore().add(java.net.URI.create("http://localhost:" + port), probeCookie);

        Instant start = Instant.now().minusSeconds(5);
        Instant end = Instant.now().plusSeconds(600);
        assertFalse(start.isBefore(parent.validFrom()), "TC02: child validity within parent");
        assertFalse(end.isAfter(parent.validUntil()), "TC02: child validity within parent");

        String decisionRef = "DEMO-W2-T1-05-TC02-" + key();
        String grantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", recipient,
                "permissionCodes", List.of("matching:school:detail_read"),
                "profileReadFields", List.of(),
                "validFrom", start.toString(),
                "validUntil", end.toString(),
                "decisionRef", decisionRef));
        var grant = auditHttpTC02(privacySession, "POST",
                "/school/terms/" + id(21) + "/delegations", grantBody, headerCanary);
        assertEquals(201, grant.statusCode(), "TC02: legitimate grant must succeed");
        String childId = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(grant.body())
                .get("id").asString();

        String revokeBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "expectedVersion", 1, "reasonCode", "QA01_REVOKED"));
        var revoke = auditHttpTC02(privacySession, "DELETE",
                "/school/delegations/" + childId, revokeBody, headerCanary);
        assertEquals(200, revoke.statusCode(), "TC02: legitimate revoke must succeed");

        var records = jdbc.queryForList("""
                SELECT action, metadata_json, reason_code, request_id, resource_id
                FROM audit_logs
                WHERE resource_id=? AND action IN ('CAPABILITY_GRANTED','CAPABILITY_REVOKED')
                """, childId);
        assertEquals(2, records.size(), "TC02: expect one grant and one revoke audit row");
        var allowed = Map.of(
                "CAPABILITY_GRANTED", Set.of("permissionCode", "scopeKind", "scopeId", "termId",
                        "assignmentKind", "validFrom", "validUntil", "securityVersion", "decisionRef"),
                "CAPABILITY_REVOKED", Set.of("permissionCode", "scopeKind", "scopeId", "termId",
                        "assignmentKind", "securityVersion", "reasonCode"));
        assertEquals(Set.of("CAPABILITY_GRANTED", "CAPABILITY_REVOKED"),
                records.stream().map(row -> String.valueOf(row.get("action")))
                        .collect(java.util.stream.Collectors.toSet()),
                "TC02: both successful audit actions must be present");

        for (var row : records) {
            String action = String.valueOf(row.get("action"));
            String metadataRaw = String.valueOf(row.get("metadata_json"));
            var metadata = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(metadataRaw);
            assertTrue(metadata.isObject(), "TC02: metadata must be a JSON object");
            // Jackson 3: JsonNode.propertyNames() replaces fieldNames().
            for (String field : metadata.propertyNames()) {
                assertTrue(allowed.get(action).contains(field),
                        "TC02: non-allowlisted audit field: " + field);
            }
            String savedRow = row.values().stream().map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining("|"));
            for (String canary : canaries)
                assertFalse(savedRow.contains(canary), "TC02: synthetic secret leaked to audit row");
            assertFalse(savedRow.contains(headerCanary), "TC02: request header leaked to audit row");
            assertFalse(savedRow.contains(grantBody), "TC02: raw HTTP request body was persisted");
            assertFalse(savedRow.contains(revokeBody), "TC02: raw HTTP revoke body was persisted");
        }

        // Non-allowlisted metadata must be rejected BEFORE AuditService persists an event.
        for (String forbidden : List.of("password", "token", "cookie", "requestBody", "email")) {
            String attemptId = key();
            var unsafe = Map.<String,Object>of(forbidden, passwordCanary);
            assertThrows(IllegalArgumentException.class,
                    () -> transactions.executeWithoutResult(status ->
                            audit.workflow(id(2), "CAPABILITY_GRANTED", attemptId, id(11), unsafe)),
                    "TC02: AuditService must refuse metadata field " + forbidden);
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE resource_id=?", Integer.class, attemptId),
                    "TC02: rejected metadata must not persist an audit row");
        }
    }

    private java.net.http.HttpResponse<String> auditHttpTC02(HttpSession session, String method,
            String path, String body, String canary) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                        "http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key())
                .header("X-QA01-Privacy-Canary", canary)
                .header(session.header(), session.token())
                .method(method, java.net.http.HttpRequest.BodyPublishers.ofString(body))
                .build();
        return session.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    /** QA01 T1-04-TC08: global ADMIN is not a scoped business actor and cannot self-grant. */
    private void adminBusinessAndSelfBootstrapTC08(String run) throws Exception {
        String adminId = jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ?", String.class, "admin@example.test");
        Authentication adminAuth = core.currentSubject(adminId).orElseThrow();

        // Global administration remains available, but does not imply school/enterprise memberships.
        assertTrue(core.hasGlobal(adminAuth, "membership:admin:write"),
                "TC08: test actor must have global ADMIN permission");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE user_id = ? AND organization_id IS NOT NULL AND active = true",
                Integer.class, adminId),
                "TC08: global ADMIN fixture must not have scoped business membership");
        assertFalse(core.hasOrganization(adminAuth, id(11),
                vn.utbridora.auth.user.Role.SCHOOL_STAFF, "matching:school:detail_read"),
                "TC08: ADMIN cannot inherit SCHOOL_STAFF read permission");
        assertFalse(core.hasOrganization(adminAuth, id(12),
                vn.utbridora.auth.user.Role.ENTERPRISE, "student_profile:shared:read"),
                "TC08: ADMIN cannot inherit ENTERPRISE business permission");

        var adminSession = login("admin@example.test", "Synthetic-week2-admin-password-456");
        // The route rejects a global ADMIN lacking a scoped business role at the HTTP security boundary (403).
        assertEquals(403, http(adminSession, "GET",
                "/matching/runs/" + run + "/details?overallStatus=MATCH", null, false, null),
                "TC08: ADMIN without a school business role must be forbidden from reading matching details");

        String serviceDecisionRef = "DEMO-W2-TC08-SELF-SERVICE-" + key();
        String apiDecisionRef = "DEMO-W2-TC08-SELF-HTTP-" + key();
        int delegationsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations", Integer.class);
        int grantAuditBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CAPABILITY_GRANTED'", Integer.class);
        Instant from = Instant.now().minusSeconds(30);
        Instant until = Instant.now().plusSeconds(1800);

        var selfBootstrap = new SchoolDelegationService.Bootstrap(
                adminId, id(11), id(21),
                List.of("matching:school:detail_read"), List.of(),
                from, until, serviceDecisionRef);
        var denied = assertThrows(OperationFailure.class,
                () -> delegations.bootstrap(adminAuth, key(), selfBootstrap),
                "TC08: self-bootstrap must be denied by service");
        assertEquals(403, denied.status,
                "TC08: service self-bootstrap denial must be HTTP 403");
        assertEquals("ACCESS_DENIED", denied.code);

        String apiBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                "userId", adminId,
                "schoolOrgId", id(11),
                "termId", id(21),
                "permissionCodes", List.of("matching:school:detail_read"),
                "profileReadFields", List.of(),
                "validFrom", from.toString(),
                "validUntil", until.toString(),
                "decisionRef", apiDecisionRef));
        assertEquals(403, http(adminSession, "POST", "/admin/school-delegations",
                apiBody, true, null),
                "TC08: HTTP bootstrap must reject ADMIN granting to themself");

        // Both rejected requests must leave the grant table and successful-grant audit unchanged.
        assertEquals(delegationsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations", Integer.class),
                "TC08: rejected self-bootstrap must not create delegations");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_delegations WHERE decision_ref IN (?, ?)",
                Integer.class, serviceDecisionRef, apiDecisionRef));
        assertEquals(grantAuditBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'CAPABILITY_GRANTED'", Integer.class),
                "TC08: rejected self-bootstrap must not write successful grant audit");
    }

    /** QA01 T1-04-TC07: current grants are usable; expired and future grants are not. */
    private void delegationValidityTC07(HttpSession schoolSession, String detailsPath,
                                        SchoolDelegationService.Delegation parent) throws Exception {
        Instant now = Instant.now();
        String[] scenarios = {"ACTIVE", "EXPIRED", "FUTURE"};
        Instant[] starts = {
                now.minusSeconds(5),
                parent.validFrom().plusSeconds(1),
                now.plusSeconds(3600)
        };
        Instant[] ends = {
                now.plusSeconds(1800),
                parent.validFrom().plusSeconds(2),
                now.plusSeconds(5400)
        };
        int[] expectedStatuses = {200, 404, 404};

        // The expired interval is within the parent's bounds but already in the past.
        assertTrue(ends[1].isBefore(now), "TC07: EXPIRED fixture must be in the past");
        assertTrue(starts[2].isAfter(now), "TC07: FUTURE fixture must begin later");
        for (int i = 0; i < scenarios.length; i++) {
            String scenario = scenarios[i];
            assertFalse(starts[i].isBefore(parent.validFrom()),
                    "TC07 " + scenario + ": start must be inside parent interval");
            assertFalse(ends[i].isAfter(parent.validUntil()),
                    "TC07 " + scenario + ": end must be inside parent interval");

            // Each case has its own user, so one grant cannot mask another.
            String password = "Synthetic-week2-tc07-password-123";
            var staff = new vn.utbridora.auth.user.UserAccount();
            staff.id = key();
            staff.email = "qa01-tc07-" + staff.id + "@example.test";
            staff.displayName = "QA01 TC07 " + scenario;
            staff.passwordHash = passwords.encode(password);
            staff.status = vn.utbridora.auth.user.AccountStatus.ACTIVE;
            users.saveAndFlush(staff);
            as(1);
            memberships.grant(id(1), staff.id, vn.utbridora.auth.user.Role.SCHOOL_STAFF, id(11));

            String decisionRef = "DEMO-W2-TC07-" + scenario;
            String grantBody = vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of(
                    "userId", staff.id,
                    "permissionCodes", List.of("matching:school:detail_read"),
                    "profileReadFields", List.of(),
                    "validFrom", starts[i].toString(),
                    "validUntil", ends[i].toString(),
                    "decisionRef", decisionRef));

            // A validly structured grant can be recorded even when not currently effective.
            assertEquals(201, http(schoolSession, "POST",
                    "/school/terms/" + id(21) + "/delegations", grantBody, true, null),
                    "TC07 " + scenario + ": grant creation must succeed");
            assertEquals(1, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM school_delegations WHERE decision_ref = ? AND active = true",
                    Integer.class, decisionRef),
                    "TC07 " + scenario + ": grant must exist in database");

            var staffSession = login(staff.email, password);
            assertEquals(expectedStatuses[i],
                    http(staffSession, "GET", detailsPath, null, false, null),
                    "TC07 " + scenario + ": access must obey grant validity");
        }
    }

    private void matchingDetailsStayWithinSchool(HttpSession ownerSchool, String run) throws Exception {
        String school = key();
        String term = key();
        jdbc.update("INSERT INTO organizations(id,name,type,status,created_at) VALUES(?,?,'UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6))",
            school, "QA01 cross-school fixture");
        jdbc.update("""
            INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at)
            VALUES(?,?,?,CURRENT_DATE - INTERVAL 1 DAY,CURRENT_DATE + INTERVAL 30 DAY,'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
            """, term, school, "QA01-W2-CROSS-SCHOOL");

        String password = "Qa01-" + key();
        var staff = new vn.utbridora.auth.user.UserAccount();
        staff.id = key();
        staff.email = "qa01-" + staff.id + "@example.test";
        staff.displayName = "QA01 staff from another school";
        staff.passwordHash = passwords.encode(password);
        staff.status = vn.utbridora.auth.user.AccountStatus.ACTIVE;
        users.saveAndFlush(staff);
        as(1);
        memberships.grant(id(1), staff.id, vn.utbridora.auth.user.Role.SCHOOL_STAFF, school);
        Instant now = Instant.now();
        delegations.bootstrap(as(1), key(), new SchoolDelegationService.Bootstrap(
            staff.id, school, term, List.of("matching:school:detail_read"), List.of(),
            now.minusSeconds(10), now.plusSeconds(1800), "DEMO-QA01-CROSS-SCHOOL"));

        var otherSchool = login(staff.email, password);
        var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
            "http://localhost:" + port + "/api/v1/matching/runs/" + run + "/details?overallStatus=MATCH"))
            .GET().build();
        var bodyHandler = java.net.http.HttpResponse.BodyHandlers.ofString();
        var allowed = ownerSchool.client().send(request, bodyHandler);
        assertEquals(200, allowed.statusCode(), "Staff from the owning school must be able to read the run");
        var details = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(allowed.body());
        assertEquals(run, details.get("run").get("matchRunId").asString());
// QA01 - TC01: Kiem tra school va term cua run
var scope = jdbc.queryForMap(
    "SELECT school_org_id, term_id FROM match_runs WHERE id = ?",
    run
);

assertEquals(id(11), scope.get("school_org_id"),
    "Run phai thuoc dung truong A");

assertEquals(id(21), scope.get("term_id"),
    "Run phai thuoc dung hoc ky A");

        var denied = otherSchool.client().send(request, bodyHandler);
        assertEquals(404, denied.statusCode(), "Staff with detail-read delegation in another school must be denied");
        var error = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(denied.body());
        assertEquals("RESOURCE_NOT_FOUND", error.get("errorCode").asString());
        assertNull(error.get("run"));
        assertNull(error.get("results"));
        assertFalse(denied.body().contains(run), "The denial must not disclose the run identifier");
    }

private void enterpriseIsolationTC03(
    vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput input,
    String cooperationA
) throws Exception {
    // Tao doanh nghiep B trong database test
    String orgB = key();
    jdbc.update(
        "INSERT INTO organizations(id,name,type,status,created_at) " +
        "VALUES(?,?,'ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",
        orgB, "QA01 Enterprise B"
    );

    // Tao tai khoan HR B
    String passwordB = "Qa01-" + key();
    var hrB = new vn.utbridora.auth.user.UserAccount();
    hrB.id = key();
    hrB.email = "qa01-hr-" + hrB.id + "@example.test";
    hrB.displayName = "QA01 HR B";
    hrB.passwordHash = passwords.encode(passwordB);
    hrB.status = vn.utbridora.auth.user.AccountStatus.ACTIVE;
    users.saveAndFlush(hrB);

    // Cap role ENTERPRISE cho HR B
    as(1);
    memberships.grant(
        id(1), hrB.id,
        vn.utbridora.auth.user.Role.ENTERPRISE, orgB
    );

    // Tao cooperation thuoc doanh nghiep B
    var authB = core.currentSubject(hrB.id).orElseThrow();
    SecurityContextHolder.getContext().setAuthentication(authB);
    var demandB = demands.create(authB, orgB, key(), input);
    var cooperationB = cooperations.create(
        authB, orgB, key(),
        new CooperationService.Submit(
            demandB.demandId(), demandB.version()
        )
    );

    // Dang nhap va kiem tra qua API
    var sessionA = login(
        "hr@example.test", "Synthetic-week2-test-password-123"
    );
    var sessionB = login(hrB.email, passwordB);

    String pathA = "/enterprise/cooperations/" + cooperationA;
    String pathB = "/enterprise/cooperations/" +
        cooperationB.cooperationId();

    // HR A doc ban ghi A
    assertEquals(200, http(
        sessionA, "GET", pathA, null, false, id(12)
    ));

    // HR A khong duoc doc ban ghi B
    assertEquals(404, http(
        sessionA, "GET", pathB, null, false, id(12)
    ));

    // HR B doc ban ghi B
    assertEquals(200, http(
        sessionB, "GET", pathB, null, false, orgB
    ));

    // HR B khong duoc doc ban ghi A
    assertEquals(404, http(
        sessionB, "GET", pathA, null, false, orgB
    ));
}

    private record HttpSession(java.net.http.HttpClient client,String header,String token) {}
    private HttpSession login(String email,String password) throws Exception {
        var client=java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).build();
        var before=vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).body());
        var response=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/v1/auth/login")).header("Content-Type","application/json").header(before.get("headerName").asString(),before.get("token").asString()).POST(java.net.http.HttpRequest.BodyPublishers.ofString(vn.utbridora.auth.operations.WorkflowValues.JSON.writeValueAsString(Map.of("email",email,"password",password)))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());
        var csrf=vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString()).body());return new HttpSession(client,csrf.get("headerName").asString(),csrf.get("token").asString());
    }
    private int http(HttpSession session,String method,String path,String body,boolean csrf,String org) throws Exception {
        var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/v1"+path)).header("Idempotency-Key",key());
        if(body!=null) request.header("Content-Type","application/json");if(csrf) request.header(session.header(),session.token());if(org!=null) request.header("X-Organization-Id",org);
        return session.client().send(request.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    /** QA01 T1-05-TC10: verify actual private-profile routes while consent and share are live. */
    private void qa01Tc10PrivateProfileAccessBeforeRevoke(String shareId) throws Exception {
        HttpSession studentSession = login("student@example.test", "Synthetic-week2-test-password-123");
        HttpSession hrSession = login("hr@example.test", "Synthetic-week2-test-password-123");
        HttpSession adminSession = login("admin@example.test", "Synthetic-week2-admin-password-456");
        String ownPath = "/students/me/profile?termId=" + id(21);
        String sharedBase = "/students/" + id(6) + "/profile?termId=" + id(21)
                + "&shareId=" + shareId;
        String sharedPath = sharedBase + "&fields=majorCode&fields=skills&fields=email"
                + "&purpose=INTERNSHIP_SELECTION";

        // The student alone may read the self-profile route, even if ADMIN exists.
        assertEquals(200, http(studentSession, "GET", ownPath, null, false, null),
                "TC10: student owner must be able to read own profile");
        assertEquals(403, http(adminSession, "GET", ownPath, null, false, null),
                "TC10: ADMIN must not inherit student self-profile access");

        // Enterprise HR may read only fields explicitly shared with this organization.
        String shareBefore = jdbc.queryForObject(
                "SELECT data_json FROM profile_shares WHERE id=?", String.class, shareId);
        var allowedRequest = java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/api/v1" + sharedPath)).GET().build();
        var allowedResponse = hrSession.client().send(allowedRequest,
                java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, allowedResponse.statusCode(),
                "TC10: HR with live share and consent must be allowed");
        var allowedJson = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(
                allowedResponse.body());
        var allowedData = allowedJson.get("data");
        assertNotNull(allowedData, "TC10: profile projection must include data object");
        assertTrue(allowedData.has("majorCode"), "TC10: majorCode is in granted whitelist");
        assertTrue(allowedData.has("skills"), "TC10: skills is in granted whitelist");
        assertFalse(allowedData.has("email"), "TC10: unshared email must not leak");
        assertFalse(allowedData.has("phone"), "TC10: unshared phone must not leak");

        // Neither global ADMIN nor student identity grants enterprise share access.
        assertEquals(404, http(adminSession, "GET", sharedPath, null, false, null),
                "TC10: ADMIN must not read enterprise-shared profile");
        assertEquals(404, http(studentSession, "GET", sharedPath, null, false, null),
                "TC10: student identity is not an enterprise share credential");
        String emailOnly = sharedBase + "&fields=email&purpose=INTERNSHIP_SELECTION";
        assertEquals(403, http(hrSession, "GET", emailOnly, null, false, null),
                "TC10: HR cannot request only unshared fields");
        assertEquals(shareBefore, jdbc.queryForObject(
                "SELECT data_json FROM profile_shares WHERE id=?", String.class, shareId),
                "TC10: denied reads must not modify share state");
        // Existing httpGuards() checks the same HR's HTTP 403 after consent revocation.
    }

    /**
     * QA01 T1-05-TC11 (AZ-10, R27/G27): a public DTO must not supply actor/role
     * authority, and service authorization must be derived from the authenticated
     * principal. All rejected attempts must leave invitation/nomination data intact.
     * This reuses the accepted invitation and live matching run from the workflow.
     */
    private void qa01Tc11RejectForgedIdentityAndCrossScope(String invitationId, String runId)
            throws Exception {
        String currentState = jdbc.queryForObject(
                "SELECT state FROM internship_invitations WHERE id=?", String.class, invitationId);
        long currentVersion = jdbc.queryForObject(
                "SELECT version FROM internship_invitations WHERE id=?", Long.class, invitationId);
        int beforeHistory = jdbc.queryForObject(
                "SELECT COUNT(*) FROM internship_invitation_history WHERE invitation_id=?",
                Integer.class, invitationId);
        int beforeAudit = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE resource_id=?",
                Integer.class, invitationId);
        int beforeBatches = jdbc.queryForObject(
                "SELECT COUNT(*) FROM proposal_batches", Integer.class);
        int beforeItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM proposal_items", Integer.class);

        HttpSession studentSession = login("student@example.test", "Synthetic-week2-test-password-123");
        // Known public DTO fields are decision, expectedVersion, reasonCode, reasonText.
        // Client-supplied actorId and role must fail schema validation, not be trusted.
        for (String extra : List.of(
                "\"actorId\":\"" + id(1) + "\"",
                "\"role\":\"ADMIN\"")) {
            String rejectedKey = key();
            String tamperedJson = "{\"decision\":\"ACCEPT\",\"expectedVersion\":"
                    + currentVersion + ",\"reasonCode\":null,\"reasonText\":null,"
                    + extra + "}";
            var resp = qa01Tc11Post(studentSession,
                    "/invitations/" + invitationId + "/decision", tamperedJson, rejectedKey);
            assertEquals(400, resp.statusCode(), "TC11 forged DTO: " + resp.body());
            var error = vn.utbridora.auth.operations.WorkflowValues.JSON.readTree(resp.body());
            assertEquals("SCHEMA_VALIDATION", error.path("errorCode").asText(),
                    "TC11 forged DTO must be rejected before business logic");
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
                    Integer.class, rejectedKey), "TC11: no operation record for forged DTO");
        }

        // Wrong principal must not read or act on another student's invitation.
        Authentication otherStudent = as(7);
        var deniedRead = assertThrows(OperationFailure.class,
                () -> proposals.readMine(otherStudent, invitationId));
        assertEquals(404, deniedRead.status, "TC11 other user cannot read student invitation");
        String deniedKey = key();
        var deniedDecision = assertThrows(OperationFailure.class,
                () -> proposals.decide(otherStudent, invitationId, deniedKey,
                        new ProposalService.InvitationDecision("ACCEPT", currentVersion, null, null)));
        assertEquals(404, deniedDecision.status, "TC11 forged resource target denied at service");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
                Integer.class, deniedKey), "TC11 denied service call cannot enter ledger");

        // ADMIN has no implicit business ownership even when resource ID is known.
        Authentication admin = as(1);
        assertEquals(404, assertThrows(OperationFailure.class,
                () -> proposals.readMine(admin, invitationId)).status,
                "TC11 ADMIN must not impersonate a student");

        // Enterprise HR cannot nominate on a school-owned matching run.
        Authentication enterpriseHr = as(4);
        String crossScopeKey = key();
        var forbiddenNomination = assertThrows(OperationFailure.class,
                () -> proposals.nominate(enterpriseHr, runId, crossScopeKey,
                        new ProposalService.Nominate(List.of(id(6)), 2L, 1L, 2L, null)));
        assertTrue(Set.of(403, 404).contains(forbiddenNomination.status),
                "TC11 cross-scope nomination must be denied, actual=" + forbiddenNomination.status);
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
                Integer.class, crossScopeKey), "TC11 denied nomination cannot enter ledger");

        assertEquals(currentState, jdbc.queryForObject(
                "SELECT state FROM internship_invitations WHERE id=?", String.class, invitationId));
        assertEquals(currentVersion, jdbc.queryForObject(
                "SELECT version FROM internship_invitations WHERE id=?", Long.class, invitationId));
        assertEquals(beforeHistory, jdbc.queryForObject(
                "SELECT COUNT(*) FROM internship_invitation_history WHERE invitation_id=?",
                Integer.class, invitationId));
        assertEquals(beforeAudit, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE resource_id=?", Integer.class, invitationId));
        assertEquals(beforeBatches, jdbc.queryForObject(
                "SELECT COUNT(*) FROM proposal_batches", Integer.class));
        assertEquals(beforeItems, jdbc.queryForObject(
                "SELECT COUNT(*) FROM proposal_items", Integer.class));
    }

    private java.net.http.HttpResponse<String> qa01Tc11Post(
            HttpSession session, String path, String json, String idempotencyKey) throws Exception {
        var request = java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .header(session.header(), session.token())
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json)).build();
        return session.client().send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private void httpGuards(String demand,String run,String invitation,String share,String task) throws Exception {
        HttpSession student=login("student@example.test","Synthetic-week2-test-password-123"),intern=login("intern@example.test","Synthetic-week2-test-password-123"),hr=login("hr@example.test","Synthetic-week2-test-password-123"),admin=login("admin@example.test","Synthetic-week2-admin-password-456");
        assertEquals(403,http(student,"POST","/matching/evaluate","{\"demandId\":\""+demand+"\",\"expectedDemandVersion\":2,\"expectedCriteriaVersion\":1}",false,null));
        assertEquals(403,http(hr,"GET","/matching/runs/"+run+"/details?overallStatus=MATCH",null,false,null));
        assertEquals(404,http(intern,"POST","/invitations/"+invitation+"/decision","{\"decision\":\"ACCEPT\",\"expectedVersion\":1,\"reasonCode\":null,\"reasonText\":null}",true,null));
        assertEquals(400,http(student,"POST","/invitations/"+invitation+"/decision","{\"decision\":\"ACCEPT\",\"expectedVersion\":1,\"reasonCode\":null,\"reasonText\":null,\"actorId\":\""+id(7)+"\"}",true,null));
        assertEquals(404,http(student,"GET","/t3/tasks/"+task,null,false,null));
        assertEquals(404,http(admin,"GET","/students/"+id(6)+"/profile?termId="+id(21)+"&shareId="+share+"&fields=majorCode&purpose=INTERNSHIP_SELECTION",null,false,null));
        assertEquals(403,http(hr,"GET","/students/"+id(6)+"/profile?termId="+id(21)+"&shareId="+share+"&fields=majorCode&purpose=INTERNSHIP_SELECTION",null,false,null));
        assertEquals(400,http(student,"POST","/students/me/consents","{}",true,null));
        var anonymous=new HttpSession(java.net.http.HttpClient.newHttpClient(),"X-CSRF-TOKEN","");assertEquals(401,http(anonymous,"GET","/invitations/me",null,false,null));
    }
    private void browser() throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- week2-runtime.spec.ts"):new ProcessBuilder("npm","run","test:e2e","--","week2-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);builder.environment().put("E2E_WEEK2","true");builder.environment().put("E2E_PASSWORD","Synthetic-week2-test-password-123");builder.environment().put("E2E_REPORT_FILE","../target/week2-browser-results.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/week2-browser.log"));var process=builder.start();
        if(!process.waitFor(180,java.util.concurrent.TimeUnit.SECONDS)) {process.descendants().forEach(child->child.destroyForcibly());process.destroyForcibly();fail("Browser timeout; see target/week2-browser.log");}
        assertEquals(0,process.exitValue(),"See target/week2-browser.log");
    }
}
