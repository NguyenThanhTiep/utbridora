package vn.utbridora.auth.modules.cooperation.application;

import java.time.*;
import java.sql.ResultSet;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.modules.cooperation.integration.*;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DemandSemanticSnapshotsTest {
    final String id="a17e2026-1121-4000-8000-000000000001";JdbcTemplate jdbc;DemandRepository demands;DemandReferencePort references;AdvisoryAuthority authority;DemandSemanticSnapshots service;Authentication auth;
    DemandRepository.StoredDemand demand;long cooperationVersion;
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        jdbc=mock(JdbcTemplate.class);demands=mock(DemandRepository.class);references=mock(DemandReferencePort.class);authority=mock(AdvisoryAuthority.class);auth=mock(Authentication.class);cooperationVersion=1;
        var input=new DemandInput(id,id,"Thực tập Java",2,"Xây API và SQL",null,null,Instant.now().plusSeconds(86400),new RequiredCriteria(List.of(),List.of(),new DateRange(LocalDate.of(2026,1,1),LocalDate.of(2030,1,1))),List.of(),"academic-v1","tax-v1");
        demand=new DemandRepository.StoredDemand(id,id,"READY",2,1,"a".repeat(64),input,Instant.now(),Instant.now(),null,id);
        when(demands.findForSchool(id)).thenAnswer(i->Optional.of(demand));
        var source=new DemandSemanticSnapshotPort.Snapshot(id,id,id,id,2,1,"a".repeat(64),input.title(),input.description(),"b".repeat(64),Instant.now(),Instant.now().plusSeconds(1000));
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(i->{
            if(i.getArgument(0,String.class).contains("FROM cooperations")) {var row=mock(ResultSet.class);when(row.getString(1)).thenReturn(id);when(row.getLong(2)).thenReturn(cooperationVersion);when(row.getString(3)).thenReturn("APPROVED");return List.of(i.getArgument(1,RowMapper.class).mapRow(row,0));}
            return List.of(source);
        });
        when(jdbc.queryForList(anyString(),eq(String.class),any(Object[].class))).thenReturn(List.of(JSON.writeValueAsString(input)));
        when(jdbc.queryForObject(anyString(),eq(String.class),any(Object[].class))).thenReturn("{\"id\":\""+id+"\",\"version\":1,\"state\":\"APPROVED\"}");
        service=new DemandSemanticSnapshots(jdbc,demands,references,authority);
    }
    @Test void captureUsesExactRevisionAndReturnsOnlyDemandText() {var s=service.capture(auth,id,2,1,"a".repeat(64),Instant.now().plusSeconds(1000));assertEquals("Xây API và SQL",s.description());assertEquals(2,s.demandVersion());verify(authority).require(auth,id,id,true);}
    @Test void changedDemandVersionCannotBeAttachedToOldRun() {assertEquals("AI_ADVISORY_SOURCE_STALE",assertThrows(OperationFailure.class,()->service.capture(auth,id,1,1,"a".repeat(64),Instant.now().plusSeconds(1000))).code);verify(jdbc,never()).update(anyString(),any(Object[].class));}
    @Test void changedCooperationInvalidatesEvenWhenDemandVersionIsStable() {assertTrue(service.current(auth,id));cooperationVersion=2;assertFalse(service.current(auth,id));}
    @Test void referenceDependencyOutageIsUnavailableNotReusableCachedSource() {doThrow(new DemandFailure(503,"DEPENDENCY_UNAVAILABLE","private-detail")).when(references).validate(anyString(),any());var e=assertThrows(OperationFailure.class,()->service.current(auth,id));assertEquals(503,e.status);assertEquals("DEPENDENCY_UNAVAILABLE",e.code);assertFalse(e.getMessage().contains("private-detail"));}
}
