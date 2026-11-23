package vn.utbridora.auth.modules.student.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.modules.student.port.*;
import vn.utbridora.auth.modules.student.port.StudentProfileProjectionPort.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos;
import vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.operations.OperationFailure;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentSemanticSnapshotsTest {
    final String id="a17e2026-1121-4000-8000-000000000001";
    JdbcTemplate jdbc;StudentProfileRepository profiles;StudentProfileProjectionPort projection;StudentSemanticSnapshots service;
    org.springframework.security.core.Authentication auth;AtomicReference<String> written;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        jdbc=mock(JdbcTemplate.class);profiles=mock(StudentProfileRepository.class);projection=mock(StudentProfileProjectionPort.class);var strict=mock(StudentProfileSnapshotPort.class);
        var authority=mock(AdvisoryAuthority.class);var catalog=mock(AcademicCatalogService.class);var tax=mock(TaxonomyReferencePort.class);
        auth=UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(id,"private@example.test",null,1,List.of()),null,List.of());
        var dataset=new SnapshotSet(id,id,id,"b".repeat(64),"academic-v1","tax-v1",Instant.now(),1);
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenReturn(List.of(dataset));
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class))).thenReturn(List.of(id));
        when(catalog.forProfile(id,id)).thenReturn(new AcademicCatalogService.Snapshot(id,id,"academic-v1",Map.of()));
        when(tax.read(any(),anySet(),anySet())).thenReturn(new TaxonomyReferencePort.References("tax-v1",Set.of(),Set.of()));
        when(strict.readSnapshot(auth,id)).thenReturn(new ProfileSnapshot(id,id,id,id,"IT",true,List.of(),true,List.of(),1,"a".repeat(64),"c","academic-v1","tax-v1",Instant.now()));
        when(profiles.lockByUser(id)).thenReturn(Optional.of(new StudentProfileRepository.ProfileRow(id,id,"PRIVATE_PHONE","PRIVATE_FILE",true,1)));
        when(projection.readAllowed(any(),any())).thenReturn(projection(BigDecimal.ONE,List.of("skills","projects")));
        written=new AtomicReference<>();when(jdbc.update(anyString(),any(Object[].class))).thenAnswer(i->{if(i.getArgument(0,String.class).startsWith("INSERT INTO student_semantic_snapshots"))written.set(i.getArgument(4,String.class));return 1;});
        service=new StudentSemanticSnapshots(jdbc,strict,projection,profiles,authority,catalog,tax);
    }
    Projection projection(BigDecimal years,List<String> allowed) {
        var skills=List.<Skill>of(new CanonicalSkill(new StudentProfileDtos.SelfSkillView("JAVA",3,years,"SELF_DECLARED","API")));
        var projects=List.of(new StudentProfileDtos.Project(id,"Dự án Java","Xây API","Backend",List.of("JAVA"),"https://private.example.test",List.of("PRIVATE_EVIDENCE")));
        return new Projection(id,1,allowed,Instant.now().plusSeconds(1000),new Data(allowed,"PRIVATE_NAME","PRIVATE_STUDENT_CODE",null,skills,projects,List.of(),null,List.of(),"PRIVATE_EMAIL","PRIVATE_PHONE"));
    }
    List<DecisionProfileRef> refs(){return List.of(new DecisionProfileRef(id,id,1,"a".repeat(64)));}
    @Test void filteredSnapshotExcludesIdentityUrlsAndFilesButPreservesSource() {
        var result=service.capture(auth,id,id,id,"tax-v1",refs(),Instant.now().plusSeconds(1000));assertEquals(1,result.count());
        String text=written.get();assertTrue(text.contains("Dự án Java"));assertTrue(text.contains("SELF_DECLARED"));
        for(String secret:List.of("PRIVATE_NAME","PRIVATE_PHONE","PRIVATE_STUDENT_CODE","PRIVATE_EMAIL","PRIVATE_EVIDENCE","private.example.test"))assertFalse(text.contains(secret));
    }
    @Test void fullVersionChangeBlocksProjectReadEvenIfRelevantHashMatches() {
        when(profiles.lockByUser(id)).thenReturn(Optional.of(new StudentProfileRepository.ProfileRow(id,id,null,null,true,2)));
        assertEquals("AI_ADVISORY_SOURCE_STALE",assertThrows(OperationFailure.class,()->service.capture(auth,id,id,id,"tax-v1",refs(),Instant.now().plusSeconds(1000))).code);verifyNoInteractions(projection);
    }
    @Test void incompleteFieldProjectionDoesNotCreateScoresFromForbiddenSource() {
        when(projection.readAllowed(any(),any())).thenReturn(projection(BigDecimal.ONE,List.of("skills")));
        assertEquals(404,assertThrows(OperationFailure.class,()->service.capture(auth,id,id,id,"tax-v1",refs(),Instant.now().plusSeconds(1000))).status);assertNull(written.get());
    }
    @Test void hugeDecimalExponentIsBoundedBeforePlainTextExpansion() {
        when(projection.readAllowed(any(),any())).thenReturn(projection(new BigDecimal("1e100000000"),List.of("skills","projects")));
        assertEquals("AI_ADVISORY_LIMIT_EXCEEDED",assertThrows(OperationFailure.class,()->service.capture(auth,id,id,id,"tax-v1",refs(),Instant.now().plusSeconds(1000))).code);assertNull(written.get());
    }
}
