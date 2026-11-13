package vn.utbridora.auth;

import java.net.*;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.modules.cooperation.application.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.cooperation.port.OpportunityPort;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.student.application.*;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos;
import vn.utbridora.auth.modules.student.port.StudentConsentPort.ConsentGrant;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-week3-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-week3-admin-password-456",
    "app.t5.placement-delivery.enabled=false","logging.level.org.springframework.jdbc=WARN"})
@ActiveProfiles("local") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EnterpriseProposalIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora");
    static {MYSQL.start();}
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);r.add("app.t5.placement-delivery.enabled",()->Boolean.getBoolean("t5.week3.browser"));}
    @Autowired JdbcTemplate jdbc; @Autowired AuthorizationPolicy core; @Autowired SchoolDelegationService delegations;
    @Autowired StudentProfileService profiles; @Autowired StudentEligibilityService eligibility;
    @Autowired MatchingService matching; @Autowired DemandService demands; @Autowired CooperationService cooperations;
    @Autowired ProposalService proposals; @Autowired StudentConsentService consents; @Autowired ProfileShareService shares;
    @Autowired OpportunityPort opportunityPort; @Autowired PlatformTransactionManager transactions;
    @LocalServerPort int port;
    DemandInput input; Session hr,student,school; Fixture f;
    record Fixture(CooperationService.CooperationDto cooperation,String proposalId) {}
    record Shared(ProfileShareService.Share share,ConsentGrant consent) {}
    record Session(HttpClient client,String header,String token) {}
    static String id(int n) {return "00000000-0000-4000-8000-"+String.format("%012d",n);}
    static String key() {return UUID.randomUUID().toString();}
    Authentication as(int n) {var auth=core.currentSubject(id(n)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(auth);return auth;}
    @BeforeAll void setup() throws Exception {
        var admin=core.currentSubject(jdbc.queryForObject("SELECT id FROM users WHERE email='admin@example.test'",String.class)).orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(admin);
        delegations.bootstrap(admin,key(),new SchoolDelegationService.Bootstrap(id(2),id(11),id(21),List.of("matching:run:execute","matching:school:detail_read","matching:school:nominate","cooperation:school:review","t5:proposal:manage","student:eligibility:school:approve","student_profile:share:school:manage","opportunity:school:publish"),List.of(),Instant.now().minusSeconds(60),Instant.now().plusSeconds(86400),"DEMO-WEEK3-TEST"));
        input=demands.list(as(4),id(12),null,null,false,0,25).content().getFirst().data();
        var profile=profiles.read(as(6),id(21));var old=profile.profile();var range=input.required().dateRange();
        var skills=input.required().skills().stream().map(s->new StudentProfileDtos.CanonicalSkillInput(s.skillCode(),5,new java.math.BigDecimal("10"),null)).toList();
        var update=new StudentProfileDtos.ProfileInput(profile.profileVersion(),id(21),old.majorCode(),true,skills,List.of(),true,List.of(new StudentProfileDtos.DateRange(range.startDate().toString(),range.endDate().toString())),old.orientationCodes(),old.subjectCodes(),old.projects(),old.certificates(),old.cvFileId(),old.phone());
        profiles.save(as(6),key(),JSON.writeValueAsString(update));
        eligibility.decide(as(2),key(),new StudentEligibilityService.EligibilityDecision(id(6),id(21),1L,true,"ELIGIBLE","WEEK3-ELIGIBILITY"));
        hr=login("hr@example.test");student=login("student@example.test");school=login("school@example.test");
    }
    @BeforeEach void fixture() {
        var draft=demands.create(as(4),id(12),key(),input);
        var submitted=cooperations.submitWithCooperation(as(4),id(12),key(),new CooperationService.Submit(draft.demandId(),draft.version()));
        var approved=cooperations.decide(as(2),id(11),submitted.cooperation().cooperationId(),key(),new CooperationService.Decision(2L,"APPROVE",null,"WEEK3-APPROVED",List.of()));
        var run=matching.evaluate(as(2),key(),new MatchingService.Evaluate(draft.demandId(),2L,1L));
        var batch=proposals.nominate(as(2),run.summary().run().matchRunId(),key(),new ProposalService.Nominate(List.of(id(6)),2L,1L,approved.version(),null));
        f=new Fixture(approved,batch.items().getFirst().proposalItemId());
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    String listPath() {return "/enterprise/cooperations/"+f.cooperation().cooperationId()+"/proposals";}
    String decisionPath() {return "/enterprise/proposals/"+f.proposalId()+"/decision";}
    String profilePath() {return "/enterprise/proposals/"+f.proposalId()+"/profile";}
    void assertHidden() throws Exception {
        var response=http(hr,"GET",listPath(),null,id(12),null,true);assertEquals(200,response.statusCode(),response.body());
        assertEquals(0,JSON.readTree(response.body()).get("totalElements").asInt());assertFalse(response.body().contains(id(6)));
    }
    Shared share() {
        var invitation=proposals.invite(as(2),f.proposalId(),key(),new ProposalService.Invite(1L,null));
        assertEquals(f.cooperation().cooperationId(),invitation.opportunityId());
        proposals.decide(as(6),invitation.invitationId(),key(),new ProposalService.InvitationDecision("ACCEPT",1L,null,null));
        return grant(invitation.invitationId());
    }
    Shared grant(String invitation) {
        long lineage=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM student_consent_lineages WHERE student_user_id=? AND term_id=? AND recipient_org_id=?",Long.class,id(6),id(21),id(12));
        var consent=consents.grant(as(6),key(),new StudentConsentService.GrantConsent(invitation,"INTERNSHIP_SELECTION",List.of("majorCode","skills","projects"),Instant.now().plusSeconds(3600),"demo-consent-v1",0L,lineage));
        var share=shares.grant(as(2),f.proposalId(),key(),new ProfileShareService.GrantProfileShare(f.proposalId(),consent.consentId(),consent.consentVersion(),3L,List.of("majorCode","skills","projects"),consent.expiresAt()));
        return new Shared(share,consent);
    }
    ProposalService.SchoolItem schoolItem() throws Exception {
        var response=http(school,"GET","/school/cooperations/"+f.cooperation().cooperationId()+"/proposals",null,id(11),null,true);
        assertEquals(200,response.statusCode(),response.body());
        return JSON.readValue(JSON.readTree(response.body()).get("content").get(0).toString(),ProposalService.SchoolItem.class);
    }
    String sharePath() {return "/school/proposals/"+f.proposalId()+"/shares";}
    String legacyProfilePath(String share) {return "/students/"+id(6)+"/profile?termId="+id(21)+"&shareId="+share+"&fields=skills&purpose=INTERNSHIP_SELECTION";}
    @Test void demandReferenceChoicesUseCurrentOwnerCatalogAndEnterpriseAccess() throws Exception {
        String base="/enterprise/demand-references";
        var schools=http(hr,"GET",base+"/schools?page=0&size=100",null,id(12),null,true);
        assertEquals(200,schools.statusCode(),schools.body());
        assertTrue(schools.body().contains(id(11)));
        assertFalse(schools.body().contains("registrationCode"));
        var terms=http(hr,"GET",base+"/terms?schoolOrgId="+id(11),null,id(12),null,true);
        assertEquals(200,terms.statusCode(),terms.body());assertTrue(terms.body().contains(id(21)));
        var catalog=http(hr,"GET",base+"/academic-catalog?schoolOrgId="+id(11)+"&termId="+id(21),null,id(12),null,true);
        assertEquals(200,catalog.statusCode(),catalog.body());
        assertEquals(input.academicCatalogVersion(),JSON.readTree(catalog.body()).get("academicCatalogVersion").asText());
        var wrongTerm=http(hr,"GET",base+"/academic-catalog?schoolOrgId="+id(11)+"&termId="+id(999),null,id(12),null,true);
        assertEquals(404,wrongTerm.statusCode(),wrongTerm.body());
        var denied=http(student,"GET",base+"/schools",null,id(12),null,true);
        assertEquals(403,denied.statusCode(),denied.body());
        var wrongEnterprise=http(hr,"GET",base+"/schools",null,id(11),null,true);
        assertEquals(403,wrongEnterprise.statusCode(),wrongEnterprise.body());
    }
    @Test void schoolSelectsEligibleMatchingStudentsThenNominatesAndInvitesThroughCanonicalApis() throws Exception {
        var draft=demands.create(as(4),id(12),key(),input);
        var submitted=cooperations.submitWithCooperation(as(4),id(12),key(),new CooperationService.Submit(draft.demandId(),draft.version()));
        var c=cooperations.decide(as(2),id(11),submitted.cooperation().cooperationId(),key(),new CooperationService.Decision(2L,"APPROVE",null,"DEMO-UI-NOMINATION",List.of()));
        var evaluated=http(school,"POST","/matching/evaluate",JSON.writeValueAsString(new MatchingService.Evaluate(draft.demandId(),2L,1L)),id(11),key(),true);
        assertEquals(200,evaluated.statusCode(),evaluated.body());String run=JSON.readValue(evaluated.body(),MatchingService.Evaluated.class).summary().run().matchRunId();
        String path="/school/cooperations/"+c.cooperationId()+"/nomination-candidates?matchRunId="+run;
        long version=jdbc.queryForObject("SELECT version FROM student_term_eligibilities WHERE user_id=? AND term_id=?",Long.class,id(6),id(21));
        eligibility.decide(as(2),key(),new StudentEligibilityService.EligibilityDecision(id(6),id(21),version,false,"REVIEW_REQUIRED","DEMO-PENDING-ELIGIBILITY"));
        try {
            var response=http(school,"GET",path,null,id(11),null,true);assertEquals(200,response.statusCode(),response.body());
            var context=JSON.readValue(response.body(),ProposalService.NominationContext.class);
            var candidate=context.candidates().content().stream().filter(row->row.match().studentUserId().equals(id(6))).findFirst().orElseThrow();
            assertFalse(candidate.selectable());assertEquals("SCHOOL_APPROVAL_REQUIRED",candidate.reasonCode());assertNotNull(candidate.studentCode());
            var approval=new StudentEligibilityService.EligibilityDecision(id(6),id(21),candidate.eligibilityVersion(),true,"SCHOOL_ELIGIBLE","DEMO-UI-ELIGIBLE");
            assertEquals(200,http(school,"POST","/school/student-eligibilities/decisions",JSON.writeValueAsString(approval),id(11),key(),true).statusCode());
            response=http(school,"GET",path,null,id(11),null,true);assertEquals(200,response.statusCode(),response.body());context=JSON.readValue(response.body(),ProposalService.NominationContext.class);
            assertTrue(context.candidates().content().stream().filter(row->row.match().studentUserId().equals(id(6))).findFirst().orElseThrow().selectable());assertNull(context.batchVersion());
            assertTrue(Set.of(403,404).contains(http(hr,"GET",path,null,id(12),null,true).statusCode()));
            assertEquals(404,http(school,"GET","/school/cooperations/"+f.cooperation().cooperationId()+"/nomination-candidates?matchRunId="+run,null,id(11),null,true).statusCode());
            String command=key();var request=new ProposalService.Nominate(List.of(id(6)),context.demandVersion(),context.criteriaVersion(),context.cooperationVersion(),context.batchVersion());
            var nominated=http(school,"POST","/matching/runs/"+run+"/nominate",JSON.writeValueAsString(request),id(11),command,true);
            assertEquals(201,nominated.statusCode(),nominated.body());assertEquals(nominated.body(),http(school,"POST","/matching/runs/"+run+"/nominate",JSON.writeValueAsString(request),id(11),command,true).body());
            var batch=JSON.readValue(nominated.body(),ProposalService.NominationResult.class);assertEquals(1,batch.items().size());
            var after=JSON.readValue(http(school,"GET",path,null,id(11),null,true).body(),ProposalService.NominationContext.class);
            var duplicate=after.candidates().content().stream().filter(row->row.match().studentUserId().equals(id(6))).findFirst().orElseThrow();assertFalse(duplicate.selectable());assertEquals("PROPOSAL_ALREADY_EXISTS",duplicate.reasonCode());assertEquals(1L,after.batchVersion());
            var duplicateRequest=new ProposalService.Nominate(List.of(id(6)),after.demandVersion(),after.criteriaVersion(),after.cooperationVersion(),after.batchVersion());
            assertEquals(409,http(school,"POST","/matching/runs/"+run+"/nominate",JSON.writeValueAsString(duplicateRequest),id(11),key(),true).statusCode());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM proposal_batches WHERE cooperation_id=?",Integer.class,c.cooperationId()));
            var invited=http(school,"POST","/school/proposals/"+batch.items().getFirst().proposalItemId()+"/invite","{\"expectedVersion\":1,\"expiresAt\":null}",id(11),key(),true);assertEquals(202,invited.statusCode(),invited.body());
            assertEquals("PENDING",JSON.readTree(invited.body()).get("state").asString());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM profile_shares WHERE proposal_item_id=?",Integer.class,batch.items().getFirst().proposalItemId()));
        } finally {
            var current=jdbc.queryForMap("SELECT school_approved,version FROM student_term_eligibilities WHERE user_id=? AND term_id=?",id(6),id(21));
            if(!Boolean.TRUE.equals(current.get("school_approved"))) eligibility.decide(as(2),key(),new StudentEligibilityService.EligibilityDecision(id(6),id(21),((Number)current.get("version")).longValue(),true,"SCHOOL_ELIGIBLE","DEMO-RESTORE-ELIGIBILITY"));
        }
    }
    @Test void schoolCanRevokeCurrentShareWithoutChangingInvitationConsentOrEnterpriseDecision() throws Exception {
        var shared=share();var before=schoolItem();assertEquals(shared.share().shareVersion(),before.shareVersion());
        var response=http(school,"POST","/school/profile-shares/"+before.shareId()+"/revoke",JSON.writeValueAsString(new ProfileShareService.Revoke(before.shareVersion(),"SCHOOL_REVIEW_REQUIRED")),id(11),key(),true);
        assertEquals(200,response.statusCode(),response.body());var after=schoolItem();assertEquals(before.state(),after.state());assertEquals(before.version(),after.version());
        assertEquals(before.activeConsentId(),after.activeConsentId());assertFalse(after.shareIsActive());assertTrue(after.shareNeedsUpdate());assertHidden();
        assertEquals(403,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
    }
    @Test void consentRegrantRequiresExplicitSchoolReshareWithOnlyNewFieldsAndExpiry() throws Exception {
        var invitation=http(school,"POST","/school/proposals/"+f.proposalId()+"/invite","{\"expectedVersion\":1,\"expiresAt\":null}",id(11),key(),true);
        assertEquals(202,invitation.statusCode(),invitation.body());String invitationId=JSON.readTree(invitation.body()).get("invitationId").asString();
        assertEquals(200,http(student,"POST","/invitations/"+invitationId+"/decision","{\"decision\":\"ACCEPT\",\"expectedVersion\":1,\"reasonCode\":null,\"reasonText\":null}",null,key(),true).statusCode());
        long lineage=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM student_consent_lineages WHERE student_user_id=? AND term_id=? AND recipient_org_id=?",Long.class,id(6),id(21),id(12));
        var request=new StudentConsentService.GrantConsent(invitationId,"INTERNSHIP_SELECTION",List.of("majorCode","skills","projects"),Instant.now().plusSeconds(3600),"demo-consent-v1",0L,lineage);
        var granted=http(student,"POST","/students/me/consents",JSON.writeValueAsString(request),null,key(),true);assertEquals(201,granted.statusCode(),granted.body());
        var firstConsent=JSON.readValue(granted.body(),ConsentGrant.class);var ready=schoolItem();
        assertEquals(firstConsent.consentId(),ready.activeConsentId());assertEquals(firstConsent.consentVersion(),ready.activeConsentVersion());
        assertNull(ready.shareId());assertFalse(ready.shareIsActive());assertHidden();
        var shareRequest=new ProfileShareService.GrantProfileShare(f.proposalId(),ready.activeConsentId(),ready.activeConsentVersion(),ready.version(),ready.shareableFields(),ready.activeConsentExpiresAt());
        var shared=http(school,"POST",sharePath(),JSON.writeValueAsString(shareRequest),id(11),key(),true);assertEquals(201,shared.statusCode(),shared.body());
        var first=JSON.readValue(shared.body(),ProfileShareService.Share.class);assertTrue(schoolItem().shareIsActive());
        assertEquals(200,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
        var revoked=http(student,"POST","/students/me/consents/"+firstConsent.consentId()+"/revoke",JSON.writeValueAsString(new StudentConsentService.Revoke(firstConsent.lineageVersion(),"STUDENT_REVOKED")),null,key(),true);
        assertEquals(200,revoked.statusCode(),revoked.body());var revokedConsent=JSON.readValue(revoked.body(),ConsentGrant.class);
        assertNull(schoolItem().activeConsentId());assertFalse(schoolItem().shareIsActive());assertHidden();
        assertEquals(403,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
        var again=new StudentConsentService.GrantConsent(invitationId,"INTERNSHIP_SELECTION",List.of("skills"),Instant.now().plusSeconds(1800),"demo-consent-v1",0L,revokedConsent.lineageVersion());
        var regranted=http(student,"POST","/students/me/consents",JSON.writeValueAsString(again),null,key(),true);assertEquals(201,regranted.statusCode(),regranted.body());
        var nextConsent=JSON.readValue(regranted.body(),ConsentGrant.class);assertTrue(nextConsent.lineageVersion()>firstConsent.lineageVersion());
        var update=schoolItem();assertTrue(update.shareNeedsUpdate());assertFalse(update.shareIsActive());
        assertEquals(List.of("skills"),update.shareableFields());assertEquals(nextConsent.consentId(),update.activeConsentId());
        assertEquals(403,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());assertHidden();
        assertEquals(403,http(hr,"GET",legacyProfilePath(first.shareId()),null,null,null,true).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM profile_shares WHERE proposal_item_id=?",Integer.class,f.proposalId()));
        var invalidFields=new ProfileShareService.GrantProfileShare(f.proposalId(),nextConsent.consentId(),1L,update.version(),List.of("skills","majorCode"),nextConsent.expiresAt());
        assertEquals(409,http(school,"POST",sharePath(),JSON.writeValueAsString(invalidFields),id(11),key(),true).statusCode());
        var invalidExpiry=new ProfileShareService.GrantProfileShare(f.proposalId(),nextConsent.consentId(),1L,update.version(),List.of("skills"),nextConsent.expiresAt().plusSeconds(60));
        assertEquals(422,http(school,"POST",sharePath(),JSON.writeValueAsString(invalidExpiry),id(11),key(),true).statusCode());
        var staleConsent=new ProfileShareService.GrantProfileShare(f.proposalId(),firstConsent.consentId(),firstConsent.consentVersion(),update.version(),List.of("skills"),nextConsent.expiresAt());
        assertEquals(409,http(school,"POST",sharePath(),JSON.writeValueAsString(staleConsent),id(11),key(),true).statusCode());
        var updatedRequest=new ProfileShareService.GrantProfileShare(f.proposalId(),update.activeConsentId(),update.activeConsentVersion(),update.version(),update.shareableFields(),update.activeConsentExpiresAt());
        String body=JSON.writeValueAsString(updatedRequest),operation=key();
        var updated=http(school,"POST",sharePath(),body,id(11),operation,true);assertEquals(201,updated.statusCode(),updated.body());
        assertEquals(updated.body(),http(school,"POST",sharePath(),body,id(11),operation,true).body());
        var next=JSON.readValue(updated.body(),ProfileShareService.Share.class);assertNotEquals(first.shareId(),next.shareId());assertEquals(2,next.shareVersion());
        assertTrue(schoolItem().shareIsActive());assertFalse(schoolItem().shareNeedsUpdate());assertEquals(5,schoolItem().version());
        var profile=http(hr,"GET",profilePath(),null,id(12),null,true);assertEquals(200,profile.statusCode(),profile.body());
        assertEquals(Set.of("skills"),new HashSet<>(JSON.readTree(profile.body()).get("data").propertyNames()));
        assertEquals(403,http(hr,"GET",legacyProfilePath(first.shareId()),null,null,null,true).statusCode());
        var enterpriseList=http(hr,"GET",listPath(),null,id(12),null,true);assertEquals(200,enterpriseList.statusCode());assertFalse(enterpriseList.body().contains("activeConsent"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM profile_shares WHERE proposal_item_id=?",Integer.class,f.proposalId()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='PROFILE_SHARED' AND resource_id=?",Integer.class,next.shareId()));
    }
    @Test void resharingAnExpiredOrRevokedShareKeepsOldUrlsBlockedAndRejectsTerminalStates() throws Exception {
        var first=share();shares.revoke(as(2),first.share().shareId(),key(),new ProfileShareService.Revoke(1L,"SCHOOL_REVOKED"));
        var ready=schoolItem();assertTrue(ready.shareNeedsUpdate());assertFalse(ready.shareIsActive());
        var request=new ProfileShareService.GrantProfileShare(f.proposalId(),ready.activeConsentId(),ready.activeConsentVersion(),ready.version(),List.of("skills"),ready.activeConsentExpiresAt());
        var result=http(school,"POST",sharePath(),JSON.writeValueAsString(request),id(11),key(),true);assertEquals(201,result.statusCode(),result.body());
        var current=JSON.readValue(result.body(),ProfileShareService.Share.class);assertEquals(3,current.shareVersion());
        assertEquals(403,http(hr,"GET",legacyProfilePath(first.share().shareId()),null,null,null,true).statusCode());
        jdbc.update("UPDATE profile_shares SET data_json=JSON_SET(data_json,'$.expiresAt',?) WHERE id=?",Instant.now().minusSeconds(1).toString(),current.shareId());
        assertTrue(schoolItem().shareNeedsUpdate());assertHidden();
        var expired=schoolItem();var renewed=new ProfileShareService.GrantProfileShare(f.proposalId(),expired.activeConsentId(),expired.activeConsentVersion(),expired.version(),List.of("skills"),expired.activeConsentExpiresAt());
        assertEquals(201,http(school,"POST",sharePath(),JSON.writeValueAsString(renewed),id(11),key(),true).statusCode());
        assertEquals(403,http(hr,"GET",legacyProfilePath(current.shareId()),null,null,null,true).statusCode());
        for(String state:List.of("PLACED","DECLINED","REVOKED","EXPIRED")) {
            jdbc.update("UPDATE proposal_items SET state=? WHERE id=?",state,f.proposalId());
            var item=schoolItem();assertFalse(item.shareNeedsUpdate());
            var rejected=new ProfileShareService.GrantProfileShare(f.proposalId(),item.activeConsentId(),item.activeConsentVersion(),item.version(),List.of("skills"),item.activeConsentExpiresAt());
            var response=http(school,"POST",sharePath(),JSON.writeValueAsString(rejected),id(11),key(),true);
            assertEquals(409,response.statusCode(),response.body());assertTrue(response.body().contains("PROPOSAL_STATE_CONFLICT"));
        }
    }
    @Test void consentRegrantAfterEnterpriseAcceptanceRestoresAccessAndPreservesDecision() throws Exception {
        var first=share();int placements=jdbc.queryForObject("SELECT COUNT(*) FROM placements",Integer.class);
        var accepted=http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",id(12),key(),true);
        assertEquals(200,accepted.statusCode(),accepted.body());assertEquals("ENTERPRISE_ACCEPTED",schoolItem().state());
        var revoked=http(student,"POST","/students/me/consents/"+first.consent().consentId()+"/revoke",JSON.writeValueAsString(new StudentConsentService.Revoke(first.consent().lineageVersion(),"STUDENT_REVOKED")),null,key(),true);
        assertEquals(200,revoked.statusCode(),revoked.body());var revokedConsent=JSON.readValue(revoked.body(),ConsentGrant.class);
        assertHidden();assertNull(schoolItem().activeConsentId());
        var consentRequest=new StudentConsentService.GrantConsent(first.consent().invitationId(),"INTERNSHIP_SELECTION",List.of("skills"),Instant.now().plusSeconds(1800),"demo-consent-v1",0L,revokedConsent.lineageVersion());
        var renewed=http(student,"POST","/students/me/consents",JSON.writeValueAsString(consentRequest),null,key(),true);
        assertEquals(201,renewed.statusCode(),renewed.body());var consent=JSON.readValue(renewed.body(),ConsentGrant.class);
        var ready=schoolItem();assertEquals("ENTERPRISE_ACCEPTED",ready.state());assertEquals(5,ready.version());
        assertTrue(ready.shareNeedsUpdate());assertFalse(ready.shareIsActive());assertEquals(consent.consentId(),ready.activeConsentId());assertEquals(List.of("skills"),ready.shareableFields());
        assertHidden();assertEquals(403,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
        var invalid=new ProfileShareService.GrantProfileShare(f.proposalId(),consent.consentId(),consent.consentVersion(),ready.version(),List.of("skills","majorCode"),consent.expiresAt());
        assertEquals(409,http(school,"POST",sharePath(),JSON.writeValueAsString(invalid),id(11),key(),true).statusCode());
        var request=new ProfileShareService.GrantProfileShare(f.proposalId(),ready.activeConsentId(),ready.activeConsentVersion(),ready.version(),ready.shareableFields(),ready.activeConsentExpiresAt());
        String body=JSON.writeValueAsString(request),operation=key();
        var shared=http(school,"POST",sharePath(),body,id(11),operation,true);assertEquals(201,shared.statusCode(),shared.body());
        var replay=http(school,"POST",sharePath(),body,id(11),operation,true);assertEquals(201,replay.statusCode());assertEquals(shared.body(),replay.body());
        var result=schoolItem();assertEquals("ENTERPRISE_ACCEPTED",result.state());assertEquals(6,result.version());assertTrue(result.shareIsActive());assertFalse(result.shareNeedsUpdate());
        assertNotEquals(first.share().shareId(),result.shareId());
        var profile=http(hr,"GET",profilePath(),null,id(12),null,true);assertEquals(200,profile.statusCode(),profile.body());
        assertEquals(Set.of("skills"),new HashSet<>(JSON.readTree(profile.body()).get("data").propertyNames()));
        assertEquals(403,http(hr,"GET",legacyProfilePath(first.share().shareId()),null,null,null,true).statusCode());
        var listed=http(hr,"GET",listPath(),null,id(12),null,true);assertEquals(200,listed.statusCode(),listed.body());
        assertEquals("ENTERPRISE_ACCEPTED",JSON.readTree(listed.body()).get("content").get(0).get("state").asString());
        assertEquals(409,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":6,\"decision\":\"ACCEPT\"}",id(12),key(),true).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE resource_id=? AND action='PROPOSAL_ITEM_DECIDED'",Integer.class,f.proposalId()));
        assertEquals(placements,jdbc.queryForObject("SELECT COUNT(*) FROM placements",Integer.class));
    }
    @Test void reshareAndPreviousShareRevocationRollbackTogetherWhenAuditFails() throws Exception {
        var first=share();String operation=key();
        var request=new ProfileShareService.GrantProfileShare(f.proposalId(),first.consent().consentId(),first.consent().consentVersion(),4L,List.of("skills"),first.consent().expiresAt());
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reshare_audit_failure CHECK(action<>'PROFILE_SHARE_REVOKED' OR resource_id<>'"+UUID.fromString(first.share().shareId())+"')");
        try {assertEquals(500,http(school,"POST",sharePath(),JSON.writeValueAsString(request),id(11),operation,true).statusCode());}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK reshare_audit_failure");}
        assertEquals(4,schoolItem().version());assertEquals(first.share().shareId(),schoolItem().shareId());assertTrue(schoolItem().shareIsActive());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM profile_shares WHERE proposal_item_id=?",Integer.class,f.proposalId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",Integer.class,operation));
        assertEquals(201,http(school,"POST",sharePath(),JSON.writeValueAsString(request),id(11),operation,true).statusCode());
        assertEquals(403,http(hr,"GET",legacyProfilePath(first.share().shareId()),null,null,null,true).statusCode());
    }
    @Test void concurrentResharesAndEnterpriseReadsKeepExactlyOneReplacement() throws Exception {
        var first=share();
        var request=new ProfileShareService.GrantProfileShare(f.proposalId(),first.consent().consentId(),first.consent().consentVersion(),4L,List.of("skills"),first.consent().expiresAt());
        String body=JSON.writeValueAsString(request);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(6);var gate=new java.util.concurrent.CountDownLatch(1);
        try {
            var calls=new ArrayList<java.util.concurrent.Callable<HttpResponse<String>>>();
            for(int n=0;n<2;n++) calls.add(()->{gate.await();return http(school,"POST",sharePath(),body,id(11),key(),true);});
            for(int n=0;n<4;n++) calls.add(()->{gate.await();return http(hr,"GET",legacyProfilePath(first.share().shareId()),null,null,null,true);});
            var futures=new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();for(var call:calls) futures.add(pool.submit(call));gate.countDown();
            int winners=0;
            for(int n=0;n<futures.size();n++) {
                var result=futures.get(n).get(15,java.util.concurrent.TimeUnit.SECONDS);
                if(n<2) {
                    if(result.statusCode()==201) winners++;
                    else {assertEquals(409,result.statusCode(),result.body());assertTrue(result.body().contains("VERSION_CONFLICT"));}
                } else assertTrue(result.statusCode()==200 || result.statusCode()==403 || result.statusCode()==409,result.body());
            }
            assertEquals(1,winners);
        } finally {pool.shutdownNow();}
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM profile_shares WHERE proposal_item_id=?",Integer.class,f.proposalId()));
        assertEquals(5,schoolItem().version());assertTrue(schoolItem().shareIsActive());
        assertEquals(403,http(hr,"GET",legacyProfilePath(first.share().shareId()),null,null,null,true).statusCode());
    }
    @Test void hidesEveryInternalStageAndReturnsOnlyWhitelistedSharedProfile() throws Exception {
        assertHidden();
        var invitation=proposals.invite(as(2),f.proposalId(),key(),new ProposalService.Invite(1L,null));assertHidden();
        proposals.decide(as(6),invitation.invitationId(),key(),new ProposalService.InvitationDecision("ACCEPT",1L,null,null));assertHidden();
        assertEquals(404,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
        grant(invitation.invitationId());
        var response=http(hr,"GET",listPath()+"?size=1",null,id(12),null,true);assertEquals(200,response.statusCode(),response.body());
        assertEquals(1,JSON.readTree(response.body()).get("totalElements").asInt());
        assertFalse(response.body().contains("studentUserId"));assertFalse(response.body().contains(id(6)));
        var profile=http(hr,"GET",profilePath(),null,id(12),null,true);assertEquals(200,profile.statusCode(),profile.body());
        var tree=JSON.readTree(profile.body());assertFalse(tree.has("studentUserId"));
        assertEquals(Set.of("majorCode","skills","projects"),new HashSet<>(tree.get("data").propertyNames()));
        assertFalse(profile.body().contains("student@example.test"));
    }
    @Test void projectionUsesExplicitShareFieldsBeyondTheDefaultThreeAndReturnsExpiry() throws Exception {
        var invitation=proposals.invite(as(2),f.proposalId(),key(),new ProposalService.Invite(1L,null));
        proposals.decide(as(6),invitation.invitationId(),key(),new ProposalService.InvitationDecision("ACCEPT",1L,null,null));
        jdbc.update("INSERT INTO school_sharing_policies VALUES(?,?,1,?,UTC_TIMESTAMP(6),?)",id(11),id(21),JSON.writeValueAsString(List.of("phone","certificates","email")),id(2));
        try {
            long lineage=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM student_consent_lineages WHERE student_user_id=? AND term_id=? AND recipient_org_id=?",Long.class,id(6),id(21),id(12));
            var consent=consents.grant(as(6),key(),new StudentConsentService.GrantConsent(invitation.invitationId(),"INTERNSHIP_SELECTION",List.of("phone","certificates"),Instant.now().plusSeconds(3600),"demo-consent-v1",1L,lineage));
            shares.grant(as(2),f.proposalId(),key(),new ProfileShareService.GrantProfileShare(f.proposalId(),consent.consentId(),1L,3L,List.of("phone","certificates"),consent.expiresAt()));
            var response=http(hr,"GET",profilePath(),null,id(12),null,true);assertEquals(200,response.statusCode(),response.body());
            var tree=JSON.readTree(response.body());
            assertEquals(Set.of("phone","certificates"),new HashSet<>(tree.get("data").propertyNames()));
            assertEquals("0901234567",tree.get("data").get("phone").asString());
            assertTrue(tree.get("data").get("certificates").isArray());
            assertTrue(Instant.parse(tree.get("validUntil").asString()).isAfter(Instant.now()));
            assertFalse(tree.get("data").has("email"));assertFalse(tree.has("studentUserId"));
            var wrongPurpose=http(hr,"GET",legacyProfilePath(jdbc.queryForObject("SELECT share_id FROM proposal_items WHERE id=?",String.class,f.proposalId())).replace("INTERNSHIP_SELECTION","SCHOOL_REVIEW"),null,id(12),null,true);
            assertEquals(400,wrongPurpose.statusCode());
        } finally {jdbc.update("DELETE FROM school_sharing_policies WHERE school_org_id=? AND term_id=?",id(11),id(21));}
    }
    @Test void fileMetadataAloneNeverOpensCvWithoutTheT1FileProvider() throws Exception {
        var invitation=proposals.invite(as(2),f.proposalId(),key(),new ProposalService.Invite(1L,null));
        proposals.decide(as(6),invitation.invitationId(),key(),new ProposalService.InvitationDecision("ACCEPT",1L,null,null));
        jdbc.update("INSERT INTO school_sharing_policies VALUES(?,?,1,?,UTC_TIMESTAMP(6),?)",id(11),id(21),JSON.writeValueAsString(List.of("cv")),id(2));
        String file=key();
        try {
            long lineage=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM student_consent_lineages WHERE student_user_id=? AND term_id=? AND recipient_org_id=?",Long.class,id(6),id(21),id(12));
            var consent=consents.grant(as(6),key(),new StudentConsentService.GrantConsent(invitation.invitationId(),"INTERNSHIP_SELECTION",List.of("cv"),Instant.now().plusSeconds(3600),"demo-consent-v1",1L,lineage));
            shares.grant(as(2),f.proposalId(),key(),new ProfileShareService.GrantProfileShare(f.proposalId(),consent.consentId(),1L,3L,List.of("cv"),consent.expiresAt()));
            var empty=http(hr,"GET",profilePath(),null,id(12),null,true);assertEquals(200,empty.statusCode(),empty.body());
            assertTrue(JSON.readTree(empty.body()).get("data").get("cv").isNull());
            jdbc.update("INSERT INTO files(id,owner_user_id,organization_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES(?,?,?,?,?,'application/pdf',100,?,'PRIVATE','CLEAN',UTC_TIMESTAMP(6))",file,id(6),id(11),"private-test/"+file,"private-student-cv.pdf","a".repeat(64));
            jdbc.update("UPDATE student_profiles SET cv_file_id=? WHERE user_id=?",file,id(6));
            var denied=http(hr,"GET",profilePath(),null,id(12),null,true);assertEquals(503,denied.statusCode(),denied.body());
            assertTrue(denied.body().contains("DEPENDENCY_UNAVAILABLE"));assertFalse(denied.body().contains("private-test"));assertFalse(denied.body().contains("private-student"));
        } finally {
            jdbc.update("UPDATE student_profiles SET cv_file_id=NULL WHERE user_id=?",id(6));jdbc.update("DELETE FROM files WHERE id=?",file);
            jdbc.update("DELETE FROM school_sharing_policies WHERE school_org_id=? AND term_id=?",id(11),id(21));
        }
    }
    @Test void acceptsOnceWithVersionConflictReplayAndNoPlacementCreation() throws Exception {
        share();int placed=jdbc.queryForObject("SELECT COUNT(*) FROM placements",Integer.class);
        var stale=http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":1,\"decision\":\"ACCEPT\"}",id(12),key(),true);assertEquals(409,stale.statusCode());assertTrue(stale.body().contains("VERSION_CONFLICT"));
        String request="{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",key=key();
        var accepted=http(hr,"PATCH",decisionPath(),request,id(12),key,true);assertEquals(200,accepted.statusCode(),accepted.body());
        assertEquals("ENTERPRISE_ACCEPTED",JSON.readTree(accepted.body()).get("state").asString());
        var replay=http(hr,"PATCH",decisionPath(),request,id(12),key,true);assertEquals(200,replay.statusCode());assertEquals(accepted.body(),replay.body());
        assertEquals(409,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"DECLINE\",\"reasonCode\":\"OTHER\"}",id(12),key,true).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='PROPOSAL_ITEM_DECIDED' AND resource_id=?",Integer.class,f.proposalId()));
        var audit=JSON.readTree(jdbc.queryForObject("SELECT metadata_json FROM audit_logs WHERE action='PROPOSAL_ITEM_DECIDED' AND resource_id=?",String.class,f.proposalId()));
        assertEquals("SHARED",audit.get("fromStatus").asString());assertEquals("ENTERPRISE_ACCEPTED",audit.get("toStatus").asString());
        assertEquals(5,audit.get("version").asInt());assertFalse(audit.has("decision"));
        assertEquals(placed,jdbc.queryForObject("SELECT COUNT(*) FROM placements",Integer.class));
        assertEquals(5,jdbc.queryForObject("SELECT version FROM proposal_items WHERE id=?",Integer.class,f.proposalId()));
    }
    @Test void rejectsOtherEnterpriseAndMissingPermissionsAndCsrf() throws Exception {
        share();String other=key();
        jdbc.update("INSERT INTO organizations(id,name,type,status,created_at) VALUES(?,'Other enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",other);
        jdbc.update("INSERT INTO memberships(id,user_id,organization_id,role,active,created_at) VALUES(?,?,?,'ENTERPRISE',true,UTC_TIMESTAMP(6))",key(),id(4),other);
        assertEquals(404,http(hr,"GET",listPath(),null,other,null,true).statusCode());
        assertEquals(404,http(hr,"GET",profilePath(),null,other,null,true).statusCode());
        assertEquals(404,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",other,key(),true).statusCode());
        assertEquals(403,http(student,"GET",listPath(),null,id(12),null,true).statusCode());
        assertEquals(403,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",id(12),key(),false).statusCode());
        jdbc.update("DELETE FROM role_permissions WHERE role='ENTERPRISE' AND permission_code='proposal:enterprise:decide'");
        try {assertEquals(403,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",id(12),key(),true).statusCode());}
        finally {jdbc.update("INSERT INTO role_permissions VALUES('ENTERPRISE','proposal:enterprise:decide')");}
    }
    @Test void validatesDeclineExpiryAndRollsBackDecisionWhenAuditFails() throws Exception {
        share();String request="{\"expectedVersion\":4,\"decision\":\"DECLINE\",\"reasonCode\":\"SKILL_MISMATCH\"}";
        assertEquals(400,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"DECLINE\"}",id(12),key(),true).statusCode());
        jdbc.update("UPDATE proposal_items SET reservation_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",f.proposalId());
        var expired=http(hr,"PATCH",decisionPath(),request,id(12),key(),true);assertEquals(409,expired.statusCode());assertTrue(expired.body().contains("RESERVATION_EXPIRED"));
        jdbc.update("UPDATE proposal_items SET reservation_until=UTC_TIMESTAMP(6)+INTERVAL 1 HOUR WHERE id=?",f.proposalId());
        String operation=key();
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT week3_audit_failure CHECK(action<>'PROPOSAL_ITEM_DECIDED' OR resource_id<>'"+UUID.fromString(f.proposalId())+"')");
        try {assertEquals(500,http(hr,"PATCH",decisionPath(),request,id(12),operation,true).statusCode());}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK week3_audit_failure");}
        assertEquals("SHARED",jdbc.queryForObject("SELECT state FROM proposal_items WHERE id=?",String.class,f.proposalId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",Integer.class,operation));
        var declined=http(hr,"PATCH",decisionPath(),request,id(12),operation,true);assertEquals(200,declined.statusCode(),declined.body());
        assertEquals("DECLINED",JSON.readTree(declined.body()).get("state").asString());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE resource_id=? AND action='PROPOSAL_ITEM_DECIDED'",Integer.class,f.proposalId()));
        var audit=JSON.readTree(jdbc.queryForObject("SELECT metadata_json FROM audit_logs WHERE action='PROPOSAL_ITEM_DECIDED' AND resource_id=?",String.class,f.proposalId()));
        assertEquals("SHARED",audit.get("fromStatus").asString());assertEquals("DECLINED",audit.get("toStatus").asString());
        assertEquals("SKILL_MISMATCH",audit.get("reasonCode").asString());
    }
    @Test void revokedConsentImmediatelyHidesProfileAndDeniesDecision() throws Exception {
        var shared=share();consents.revoke(as(6),shared.consent().consentId(),key(),new StudentConsentService.Revoke(shared.consent().lineageVersion(),"STUDENT_REVOKED"));
        assertHidden();assertEquals(403,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
        assertEquals(403,http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",id(12),key(),true).statusCode());
    }
    @Test void proposalStateFiltersValidateValuesAndCountOnlyVisibleItemsBeforePagination() throws Exception {
        String schoolPath="/school/cooperations/"+f.cooperation().cooperationId()+"/proposals";
        var reserved=http(school,"GET",schoolPath+"?state=RESERVED&size=1",null,id(11),null,true);
        assertEquals(200,reserved.statusCode(),reserved.body());assertEquals(1,JSON.readTree(reserved.body()).get("totalElements").asInt());
        assertEquals(0,JSON.readTree(http(school,"GET",schoolPath+"?state=SHARED",null,id(11),null,true).body()).get("totalElements").asInt());
        for(String invalid:List.of("ACTIVE","invalid","")) {
            assertEquals(400,http(school,"GET",schoolPath+"?state="+invalid,null,id(11),null,true).statusCode());
            assertEquals(400,http(hr,"GET",listPath()+"?state="+invalid,null,id(12),null,true).statusCode());
        }
        assertEquals(0,JSON.readTree(http(hr,"GET",listPath()+"?state=RESERVED",null,id(12),null,true).body()).get("totalElements").asInt());
        var shared=share();
        // A legacy/inaccessible SHARED row must not alter enterprise counts or page boundaries.
        jdbc.update("INSERT INTO proposal_items(id,batch_id,student_user_id,state,version,reservation_until,profile_snapshot_id,match_run_id,share_id) SELECT ?,batch_id,?,'SHARED',1,reservation_until,profile_snapshot_id,match_run_id,? FROM proposal_items WHERE id=?",key(),id(7),key(),f.proposalId());
        var visible=http(hr,"GET",listPath()+"?state=SHARED&size=1",null,id(12),null,true);
        assertEquals(200,visible.statusCode(),visible.body());var page=JSON.readTree(visible.body());
        assertEquals(1,page.get("totalElements").asInt());assertEquals(1,page.get("totalPages").asInt());
        assertEquals(f.proposalId(),page.get("content").get(0).get("proposalItemId").asString());assertFalse(visible.body().contains(id(7)));
        var next=http(hr,"GET",listPath()+"?state=SHARED&size=1&page=1",null,id(12),null,true);
        assertEquals(0,JSON.readTree(next.body()).get("content").size());assertEquals(1,JSON.readTree(next.body()).get("totalElements").asInt());
        var accepted=http(hr,"PATCH",decisionPath(),"{\"expectedVersion\":4,\"decision\":\"ACCEPT\"}",id(12),key(),true);assertEquals(200,accepted.statusCode(),accepted.body());
        assertEquals(0,JSON.readTree(http(hr,"GET",listPath()+"?state=SHARED",null,id(12),null,true).body()).get("totalElements").asInt());
        assertEquals(1,JSON.readTree(http(hr,"GET",listPath()+"?state=ENTERPRISE_ACCEPTED",null,id(12),null,true).body()).get("totalElements").asInt());
        var schoolAccepted=http(school,"GET",schoolPath+"?state=ENTERPRISE_ACCEPTED",null,id(11),null,true);
        assertEquals(200,schoolAccepted.statusCode(),schoolAccepted.body());assertEquals(1,JSON.readTree(schoolAccepted.body()).get("totalElements").asInt());
        shares.revoke(as(2),shared.share().shareId(),key(),new ProfileShareService.Revoke(1L,"SCHOOL_REVOKED"));
        assertEquals(0,JSON.readTree(http(hr,"GET",listPath()+"?state=ENTERPRISE_ACCEPTED",null,id(12),null,true).body()).get("totalElements").asInt());
    }
    @Test void revokedShareIsFilteredBeforePagination() throws Exception {
        var shared=share();shares.revoke(as(2),shared.share().shareId(),key(),new ProfileShareService.Revoke(1L,"SCHOOL_REVOKED"));
        assertHidden();assertEquals(403,http(hr,"GET",profilePath(),null,id(12),null,true).statusCode());
    }
    OpportunityPort.OpportunityReference preferenceOpportunity(Authentication auth,String org,String id) {
        var context=ActorContext.fromAuthenticatedWorkflow(auth,org);
        return new TransactionTemplate(transactions).execute(status->opportunityPort.readForPreference(context,id));
    }
    @Test void opportunityPortReturnsOpenScheduledAndClosedPublicationMetadata() throws Exception {
        String id=f.cooperation().cooperationId();
        var open=cooperations.publish(as(2),id(11),id,key(),new CooperationService.Publication(
            Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),3L));
        var reference=preferenceOpportunity(as(6),id(11),id);
        assertEquals(open.opportunityId(),reference.opportunityId());
        assertEquals(open.schoolOrgId(),reference.schoolOrgId());
        assertEquals(open.enterpriseOrgId(),reference.enterpriseOrgId());
        assertEquals(open.termId(),reference.termId());
        assertEquals(open.publishedAt(),reference.publishedAt());
        assertEquals(open.closedAt(),reference.closedAt());
        assertEquals(open.version(),reference.version());
        var scheduled=cooperations.publish(as(2),id(11),id,key(),new CooperationService.Publication(
            Instant.now().plusSeconds(60),Instant.now().plusSeconds(3600),open.version()));
        assertEquals(scheduled.publishedAt(),preferenceOpportunity(as(6),null,id).publishedAt());
        var closed=cooperations.publish(as(2),id(11),id,key(),new CooperationService.Publication(
            Instant.now().minusSeconds(3600),Instant.now().minusSeconds(60),scheduled.version()));
        var closedReference=preferenceOpportunity(as(6),id(11),id);
        assertEquals(closed.closedAt(),closedReference.closedAt());
        assertEquals(closed.version(),closedReference.version());
        assertTrue(closedReference.closedAt().isBefore(Instant.now()));
        assertEquals(404,http(student,"GET","/opportunities/"+id,null,null,null,true).statusCode());
    }
    @Test void opportunityPortRejectsUnpublishedUnknownAndUnapprovedRecords() {
        var studentAuth=as(6);String id=f.cooperation().cooperationId();
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(studentAuth,id(11),id)).status);
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(studentAuth,id(11),key())).status);
        cooperations.publish(as(2),id(11),id,key(),new CooperationService.Publication(
            Instant.now().minusSeconds(3600),Instant.now().minusSeconds(60),3L));
        jdbc.update("UPDATE cooperations SET state='SUBMITTED' WHERE id=?",id);
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(studentAuth,id(11),id)).status);
    }
    @Test void opportunityPortRejectsForeignScopeAndStudentsOutsideTheTerm() {
        String id=f.cooperation().cooperationId();
        cooperations.publish(as(2),id(11),id,key(),new CooperationService.Publication(
            Instant.now().minusSeconds(3600),Instant.now().minusSeconds(60),3L));
        var studentAuth=as(6);
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(studentAuth,id(12),id)).status);
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(as(4),null,id)).status);
        String otherTerm=key();
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES(?,?,?,UTC_DATE(),DATE_ADD(UTC_DATE(),INTERVAL 6 MONTH),'ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
            otherTerm,id(11),key());
        jdbc.update("UPDATE cooperations SET term_id=? WHERE id=?",otherTerm,id);
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(studentAuth,id(11),id)).status);
    }
    @Test void opportunityPortRejectsDeletedDemandAndMissingActor() {
        String id=f.cooperation().cooperationId();
        cooperations.publish(as(2),id(11),id,key(),new CooperationService.Publication(
            Instant.now().minusSeconds(3600),Instant.now().minusSeconds(60),3L));
        jdbc.update("UPDATE demands SET soft_deleted_at=UTC_TIMESTAMP(6) WHERE id=?",f.cooperation().demandId());
        assertEquals(404,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->preferenceOpportunity(as(6),id(11),id)).status);
        assertEquals(401,assertThrows(vn.utbridora.auth.operations.OperationFailure.class,
            ()->new TransactionTemplate(transactions).execute(status->opportunityPort.readForPreference(null,id))).status);
    }
    @Test void publicationUsesApprovedCooperationIdentityWindowAndStudentTermScope() throws Exception {
        String id=f.cooperation().cooperationId(),publication="/school/cooperations/"+id+"/publication",detail="/opportunities/"+id;
        assertEquals(404,http(student,"GET",detail,null,null,null,true).statusCode());
        var window=new CooperationService.Publication(Instant.now().minusSeconds(60),Instant.now().plusSeconds(3600),3L);
        String body=JSON.writeValueAsString(window),key=key();
        assertEquals(404,http(hr,"PUT",publication,body,id(12),key,true).statusCode());
        var published=http(school,"PUT",publication,body,id(11),key,true);assertEquals(200,published.statusCode(),published.body());
        var publicationResult=JSON.readValue(published.body(),CooperationService.OpportunityView.class);
        assertEquals(id,publicationResult.opportunityId());assertEquals(input.title(),publicationResult.title());
        assertEquals(published.body(),http(school,"PUT",publication,body,id(11),key,true).body());
        var opportunity=http(student,"GET",detail,null,null,null,true);assertEquals(200,opportunity.statusCode(),opportunity.body());
        assertEquals(id,JSON.readTree(opportunity.body()).get("opportunityId").asString());
        assertEquals(404,http(hr,"GET",detail,null,null,null,true).statusCode());
        var listed=http(student,"GET","/opportunities?termId="+id(21)+"&size=1",null,null,null,true);assertEquals(200,listed.statusCode(),listed.body());
        assertEquals(1,JSON.readTree(listed.body()).get("totalElements").asInt());
        assertEquals(0,JSON.readTree(http(student,"GET","/opportunities?termId="+id(21)+"&majorCode=NO_SUCH_MAJOR",null,null,null,true).body()).get("totalElements").asInt());
        assertEquals(404,http(student,"GET","/opportunities?termId="+key(),null,null,null,true).statusCode());
        assertEquals(409,http(school,"PUT",publication,body,id(11),key(),true).statusCode());
        var invalid=new CooperationService.Publication(window.closedAt(),window.publishedAt(),4L);
        assertEquals(422,http(school,"PUT",publication,JSON.writeValueAsString(invalid),id(11),key(),true).statusCode());
        var future=new CooperationService.Publication(Instant.now().plusSeconds(60),Instant.now().plusSeconds(3600),4L);
        assertEquals(200,http(school,"PUT",publication,JSON.writeValueAsString(future),id(11),key(),true).statusCode());
        assertEquals(404,http(student,"GET",detail,null,null,null,true).statusCode());
        var closed=new CooperationService.Publication(Instant.now().minusSeconds(3600),Instant.now().minusSeconds(60),5L);
        assertEquals(200,http(school,"PUT",publication,JSON.writeValueAsString(closed),id(11),key(),true).statusCode());
        assertEquals(404,http(student,"GET",detail,null,null,null,true).statusCode());
        jdbc.update("UPDATE cooperations SET state='SUBMITTED' WHERE id=?",id);
        assertEquals(409,http(school,"PUT",publication,JSON.writeValueAsString(new CooperationService.Publication(window.publishedAt(),window.closedAt(),6L)),id(11),key(),true).statusCode());
    }
    @Test void portalOrganizationNamesAreLimitedToCurrentMemberships() throws Exception {
        var enterpriseReply=http(hr,"GET","/cooperation/organizations?organizationId="+id(11),null,id(11),null,false);
        assertEquals(200,enterpriseReply.statusCode(),enterpriseReply.body());
        var enterpriseRows=JSON.readTree(enterpriseReply.body());assertEquals(1,enterpriseRows.size());
        assertEquals(id(12),enterpriseRows.get(0).get("id").asString());
        assertEquals(jdbc.queryForObject("SELECT name FROM organizations WHERE id=?",String.class,id(12)),enterpriseRows.get(0).get("name").asString());
        assertFalse(enterpriseRows.get(0).has("status"));
        var schoolReply=http(school,"GET","/cooperation/organizations",null,null,null,false);
        assertEquals(200,schoolReply.statusCode(),schoolReply.body());
        var schoolRows=JSON.readTree(schoolReply.body());assertEquals(1,schoolRows.size());
        assertEquals(id(11),schoolRows.get(0).get("id").asString());
        assertEquals(jdbc.queryForObject("SELECT name FROM organizations WHERE id=?",String.class,id(11)),schoolRows.get(0).get("name").asString());
        assertEquals(0,JSON.readTree(http(student,"GET","/cooperation/organizations",null,null,null,false).body()).size());
        var anonymous=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/cooperation/organizations")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(401,anonymous.statusCode());
    }
    @Test void browserUsesRealSchoolStudentEnterpriseSharingAndPlacementHandoff() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("t5.week3.browser"),"Enable -Dt5.week3.browser=true for the real-browser workflow");
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- week3-runtime.spec.ts"):new ProcessBuilder("npm","run","test:e2e","--","week3-runtime.spec.ts");
        builder.directory(new java.io.File("web-demo"));
        builder.environment().put("API_PROXY_TARGET","http://localhost:"+port);
        builder.environment().put("E2E_WEEK3","true");builder.environment().put("E2E_PASSWORD","Synthetic-week3-test-password-123");
        builder.environment().put("E2E_COOPERATION",f.cooperation().cooperationId());builder.environment().put("E2E_PROPOSAL",f.proposalId());
        builder.environment().put("E2E_REPORT_FILE","../target/week3-browser-results.xml");
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/week3-browser.log"));
        var process=builder.start();
        if(!process.waitFor(240,java.util.concurrent.TimeUnit.SECONDS)) {process.descendants().forEach(child->child.destroyForcibly());process.destroyForcibly();fail("Browser timeout; see target/week3-browser.log");}
        assertEquals(0,process.exitValue(),"See target/week3-browser.log");
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT state FROM placements WHERE proposal_item_id=?",String.class,f.proposalId()));
    }
    Session login(String email) throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        var before=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/login")).header("Content-Type","application/json").header(before.get("headerName").asString(),before.get("token").asString()).POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",email,"password","Synthetic-week3-test-password-123")))).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode(),response.body());
        var csrf=JSON.readTree(client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
        return new Session(client,csrf.get("headerName").asString(),csrf.get("token").asString());
    }
    HttpResponse<String> http(Session session,String method,String path,String body,String org,String key,boolean csrf) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path));
        if(org!=null)request.header("X-Organization-Id",org);if(key!=null)request.header("Idempotency-Key",key);
        if(csrf)request.header(session.header(),session.token());if(body!=null)request.header("Content-Type","application/json");
        return session.client().send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
