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
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    static String id(int i) {return "00000000-0000-4000-8000-"+String.format("%012d",i);}
    static String key() {return UUID.randomUUID().toString();}
    Authentication as(int id) {var a=core.currentSubject(id(id)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(a);return a;}
    @AfterEach void clear() {SecurityContextHolder.clearContext();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    @Test void actualDelegationPolicyMatchingAndCooperationUseOwnerSourcesAndRollback() throws Exception {
        var admin=core.currentSubject(jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'",String.class)).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(admin);
        var grant=delegations.bootstrap(admin,key(),new SchoolDelegationService.Bootstrap(id(2),id(11),id(21),List.of("school:delegation:manage","matching:run:execute","matching:school:detail_read","matching:school:nominate","cooperation:school:review","t5:proposal:manage","student:eligibility:school:approve","student_profile:share:school:manage"),List.of(),Instant.now().minusSeconds(60),Instant.now().plusSeconds(86400),"DEMO-WEEK2-TEST"));
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
        var item=proposals.list(as(2),approved.cooperationId(),0,25).content().getFirst();
        var share=shares.grant(as(2),item.proposalItemId(),key(),new ProfileShareService.GrantProfileShare(item.proposalItemId(),grantConsent.consentId(),grantConsent.consentVersion(),item.version(),List.of("majorCode","skills"),grantConsent.expiresAt()));
        var projection=shares.read(as(4),id(6),id(21),share.shareId(),List.of("majorCode","skills","email"),"INTERNSHIP_SELECTION");
        assertEquals(Set.of("majorCode","skills"),projection.data().keySet());assertFalse(projection.data().containsKey("email"));
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
        if(Boolean.getBoolean("frontend.e2e")) {
            var d=demandData.data();var browserDemand=demands.create(as(4),id(12),key(),new vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput(d.schoolOrgId(),d.termId(),"W2 browser cooperation",d.quantity(),d.description(),d.location(),d.benefits(),d.deadline(),d.required(),d.preferred(),d.academicCatalogVersion(),d.taxonomyVersion()));
            cooperations.submitWithCooperation(as(4),id(12),key(),new CooperationService.Submit(browserDemand.demandId(),browserDemand.version()));
            browser();
        }
        consentEdges(invitation.invitationId(),share.shareId());
        cooperationEdges(demandData.data());
        invitationEdges(demandData.data());
        handoverBoundary(group,caseId,task.taskId());
        delegationRevocationBoundary(grant,matched.summary().run().matchRunId());
    }
    private static void context(String id) {var request=new org.springframework.mock.web.MockHttpServletRequest();request.addHeader("X-Context-Assignment-Id",id);org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));}
    private void cooperationEdges(vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput input) {
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
        var executeOnly=matching.evaluate(as(22),key(),new MatchingService.Evaluate(demand,version,1L));assertEquals("COMPLETED",executeOnly.summary().run().state());
        final var limited=as(22);assertThrows(OperationFailure.class,()->matching.details(limited,executeOnly.summary().run().matchRunId(),"MATCH",0,25));
        var child=delegations.grant(as(2),id(21),key(),new SchoolDelegationService.Grant(subject.id,List.of("matching:school:detail_read"),List.of(),Instant.now(),Instant.now().plusSeconds(1800),"DEMO-W2-CHILD"));
        var session=login(subject.email,"Synthetic-week2-delegate-password-123");
        matchingDetailsStayWithinSchool(session, run);
        admin=as(1);delegations.revoke(admin,parent.id(),key(),new SchoolDelegationService.Revoke(parent.version(),"ROOT_REVOKED"));
        assertEquals(401,http(session,"GET","/matching/runs/"+run+"/details?overallStatus=MATCH",null,false,null));
        var newSession=login(subject.email,"Synthetic-week2-delegate-password-123");assertEquals(404,http(newSession,"GET","/matching/runs/"+run+"/details?overallStatus=MATCH",null,false,null));
        assertTrue(jdbc.queryForObject("SELECT active FROM school_delegations WHERE id=?",Boolean.class,child.id()),"Child row may remain active but its revoked parent must deny access");
        var hr=login("hr@example.test","Synthetic-week2-test-password-123");String coordinator=jdbc.queryForObject("SELECT id FROM internship_context_assignments WHERE kind='COORDINATOR'",String.class);
        assignments.revoke(as(2),coordinator,key(),new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAssignmentService.Revoke(1L,"SCHOOL_REVOKED"));
        assertEquals(401,http(hr,"GET","/t3/context-assignments/me",null,false,null));assertEquals(0,workspace.assignments(as(4)).size());assertEquals(0,workspace.assignments(as(5)).size());
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
