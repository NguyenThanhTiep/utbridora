package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.integration.DemandReferencePort;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemandServiceTest {
    final String org=UUID.randomUUID().toString(), id=UUID.randomUUID().toString(), key=UUID.randomUUID().toString();
    DemandRepository repository; DemandReferencePort references; AuthorizationPolicy policy;
    UserRepository users; AuditService audit; DemandService service; Authentication auth; UserAccount user;
    DemandInput input;
    vn.utbridora.auth.operations.OperationLedger operations;
    @BeforeEach void setup() {
        repository=mock(DemandRepository.class); references=mock(DemandReferencePort.class);
        policy=mock(AuthorizationPolicy.class); users=mock(UserRepository.class); audit=mock(AuditService.class);
        user=new UserAccount(); user.id=UUID.randomUUID().toString(); user.status=AccountStatus.ACTIVE;
        auth=UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(user.id,"test@example.test","",0,List.of()),null,List.of());
        when(users.findByIdForUpdate(user.id)).thenReturn(Optional.of(user));
        when(policy.hasOrganization(auth,org,Role.ENTERPRISE,"cooperation:enterprise:create")).thenReturn(true);
        operations=mock(vn.utbridora.auth.operations.OperationLedger.class);
        service=new DemandService(repository,references,policy,users,audit,operations,true);
        ReflectionTestUtils.setField(service,"entityManager",mock(EntityManager.class));
        input=new DemandInput(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"Internship",5,"Description",null,null,
            Instant.parse("2027-01-01T00:00:00Z"),new RequiredCriteria(List.of(),List.of(),
            new DateRange(java.time.LocalDate.parse("2027-02-01"),java.time.LocalDate.parse("2027-05-01"))),List.of(),"v1","v1");
        when(operations.execute(any(),eq(DemandDto.class),any(),any()))
            .thenAnswer(call->call.getArgument(2,java.util.function.Supplier.class).get());
    }
    @Test void unauthenticatedAccessIsRejectedBeforePersistence() {
        assertEquals(401,assertThrows(DemandFailure.class,()->service.list(null,org,null,null,false,0,20)).status());
        verifyNoInteractions(repository,references);
    }
    @Test void wrongOrganizationIsDenied() {
        when(policy.hasOrganization(auth,org,Role.ENTERPRISE,"cooperation:enterprise:create")).thenReturn(false);
        assertEquals("ACCESS_DENIED",assertThrows(DemandFailure.class,()->service.create(auth,org,key,input)).code());
        verifyNoInteractions(repository,references);
    }
    @Test void revokedSessionIsDenied() {
        user.securityVersion=1;
        assertEquals(401,assertThrows(DemandFailure.class,()->service.create(auth,org,key,input)).status());
        verifyNoInteractions(repository,references);
    }
    @Test void disabledRolloutBlocksEvenAuthorizedUser() {
        service=new DemandService(repository,references,policy,users,audit,operations,false);
        assertEquals("FEATURE_DISABLED",assertThrows(DemandFailure.class,()->service.create(auth,org,key,input)).code());
        verifyNoInteractions(repository,references);
    }
    @Test void createsDraftAndRevisionWithEnterpriseScope() {
        var result=service.create(auth,org,key,input);
        assertEquals(org,result.enterpriseOrgId()); assertEquals("DRAFT",result.state());
        assertEquals(1,result.version()); assertEquals(1,result.criteriaVersion());
        verify(references).validate(org,input);
        verify(repository).addRevision(any(),any(),eq(user.id),eq("CREATE"));
    }
    @Test void replayReturnsOriginalResponseWithoutCreatingAgain() {
        var result=new DemandDto(id,org,"DRAFT",1,1,"VALID",input,null,null);
        doReturn(result).when(operations).execute(any(),eq(DemandDto.class),any(),any());
        assertEquals(result,service.create(auth,org,key,input));
        verify(repository,never()).insert(any()); verifyNoInteractions(references,audit);
    }
    @Test void staleEditDoesNotWriteRevision() {
        existing();
        assertEquals("VERSION_CONFLICT",assertThrows(DemandFailure.class,()->service.update(auth,org,id,key,new UpdateDemand(0L,input))).code());
        verify(repository,never()).update(any(),anyLong()); verifyNoInteractions(references,audit);
    }
    @Test void unchangedCriteriaEditIncrementsOnlyDemandVersion() {
        existing(); when(repository.update(any(),eq(1L))).thenReturn(1);
        var result=service.update(auth,org,id,key,new UpdateDemand(1L,input));
        assertEquals(2,result.version()); assertEquals(1,result.criteriaVersion());
        verify(repository).addRevision(any(),any(),eq(user.id),eq("UPDATE"));
    }
    @Test void missingReferencesPreventDraftInsert() {
        doThrow(new DemandFailure(503,"DEPENDENCY_UNAVAILABLE","Unavailable")).when(references).validate(org,input);
        assertEquals("DEPENDENCY_UNAVAILABLE",assertThrows(DemandFailure.class,()->service.create(auth,org,key,input)).code());
        verify(repository,never()).insert(any()); verifyNoInteractions(audit);
    }
    private void existing() {
        when(repository.findLocked(id,org)).thenReturn(Optional.of(new StoredDemand(id,org,"DRAFT",1,1,
            DemandJson.criteriaHash(input),input,Instant.now(),Instant.now(),null)));
    }
}
