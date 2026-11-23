package vn.utbridora.auth.modules.matching.advisory;

import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.operations.OperationErrors;
import vn.utbridora.auth.modules.matching.api.AdvisoryController;
import vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdvisoryControllerTest {
    final String id="a17e2026-1121-4000-8000-000000000001";AdvisoryService service;MockMvc mvc;
    @BeforeEach void setup() {service=mock(AdvisoryService.class);mvc=MockMvcBuilders.standaloneSetup(new AdvisoryController(service)).setControllerAdvice(new OperationErrors(mock(AuditService.class)),new vn.utbridora.auth.modules.matching.api.AdvisoryErrors()).build();}
    @Test void postReturnsStaticAckLocationAndNoStore() throws Exception {
        when(service.create(isNull(),eq(id),eq(id),any())).thenReturn(new Accepted(id,id,Instant.parse("2026-11-21T00:00:00Z")));
        mvc.perform(post("/api/v1/matching/runs/"+id+"/advisories").header("Idempotency-Key",id).contentType("application/json").content("{\"expectedDemandVersion\":1,\"expectedCriteriaVersion\":1,\"expectedProfileSnapshotVersion\":\""+"a".repeat(64)+"\"}"))
            .andExpect(status().isAccepted()).andExpect(header().string("Location","/api/v1/matching/advisories/"+id)).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.advisoryId").value(id)).andExpect(jsonPath("$.state").doesNotExist());
    }
    @Test void unknownBodyFieldsCannotSupplyScoresOrActor() throws Exception {
        mvc.perform(post("/api/v1/matching/runs/"+id+"/advisories").header("Idempotency-Key",id).contentType("application/json").content("{\"expectedDemandVersion\":1,\"expectedCriteriaVersion\":1,\"expectedProfileSnapshotVersion\":\"hash\",\"actorId\":\"attacker\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("SCHEMA_VALIDATION"));verifyNoInteractions(service);
    }
    @Test void duplicatePagingAndCustomSortAreRejected() throws Exception {
        mvc.perform(get("/api/v1/matching/advisories/"+id+"/items").param("page","0","1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/matching/advisories/"+id+"/items").param("sort","semanticScore")).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void coercedVersionIsRejected() throws Exception {
        mvc.perform(post("/api/v1/matching/runs/"+id+"/advisories").header("Idempotency-Key",id).contentType("application/json").content("{\"expectedDemandVersion\":\"1\",\"expectedCriteriaVersion\":1,\"expectedProfileSnapshotVersion\":\"hash\"}"))
            .andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void databaseAndTransactionFailuresUseSafeClosedErrorEnvelope() throws Exception {
        for(var failure:List.of(new org.springframework.dao.DataAccessResourceFailureException("PRIVATE_SQL_DETAIL"),new org.springframework.transaction.CannotCreateTransactionException("PRIVATE_DATABASE_URL"))) {
            when(service.metadata(isNull(),eq(id))).thenThrow(failure);
            mvc.perform(get("/api/v1/matching/advisories/"+id)).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE"))));
        }
    }
}
