package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.api.*;
import vn.utbridora.auth.operations.OperationErrors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Actual HTTP -> parser -> commands -> ledger boundary -> service -> domain -> fake store.
 * Reuses the existing DEV06 fixture. No production routes/context or database are activated. */
class Dev06HttpWorkflowTest extends Dev06WorkflowFixture {
    private final JsonMapper json = JsonMapper.builder().build();
    private MockMvc mvc;
    @BeforeEach void setupHttp() {
        mvc = standaloneSetup(new WeeklyReportController(reports, reportCommands), new TaskCommentController(comments, commentCommands))
                .setControllerAdvice(new OperationErrors(mock(AuditService.class)), new Week3HttpErrors()).build();
    }
    private String send(MockHttpServletRequestBuilder request, Authentication actor, UUID key, Object body, int status) throws Exception {
        return mvc.perform(request.principal(actor).header("Idempotency-Key", key).contentType("application/json")
                .content(json.writeValueAsString(body))).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
    }
    @Test void studentAndMentorCompleteTwoReportRevisionsWithoutOverwritingFirstReviewOrTaskDiscussion() throws Exception {
        var discussion = send(post("/api/v1/t3/assignments/{id}/comments", assignmentId), studentActor, UUID.randomUUID(), Map.of("text", "Task discussion"), 201);
        var created = json.readTree(send(post("/api/v1/t3/reports"), studentActor, UUID.randomUUID(), sampleReport(), 201));
        String id = created.get("reportId").asText();
        var submitted = json.readTree(send(post("/api/v1/t3/reports/{id}/submit", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 1), 200));
        assertEquals("SUBMITTED", submitted.get("state").asText());
        send(post("/api/v1/t3/reports/{id}/reviews", id), mentorActor, UUID.randomUUID(), Map.of("expectedVersion", 2, "comment", "First feedback"), 200);
        var original = store.roots.get(UUID.fromString(id)).revision(1);
        send(post("/api/v1/t3/reports/{id}/revisions", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 3, "reasonCode", "CORRECTION"), 201);
        send(patch("/api/v1/t3/reports/{id}", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 4,
                "completedWork", "Updated work", "difficulties", "", "nextPlan", "Next plan", "evidenceFileIds", List.of()), 200);
        send(post("/api/v1/t3/reports/{id}/submit", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 5), 200);
        send(post("/api/v1/t3/reports/{id}/reviews", id), mentorActor, UUID.randomUUID(), Map.of("expectedVersion", 6, "comment", "Second feedback"), 200);
        var latest = store.roots.get(UUID.fromString(id));
        assertEquals(original, latest.revision(1)); assertEquals("First feedback", latest.revision(1).review().comment());
        assertEquals("Second feedback", latest.revision(2).review().comment());
        mvc.perform(get("/api/v1/t3/reports/{id}", id).principal(studentActor).param("revisionNo", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3)).andExpect(jsonPath("$.state").value("REVIEWED"));
        mvc.perform(get("/api/v1/t3/reports").principal(studentActor).param("caseId", caseId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].revisionNo").value(2))
                .andExpect(jsonPath("$.content[0].version").value(7));
        mvc.perform(get("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(mentorActor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].commentId").value(json.readTree(discussion).get("commentId").asText()));
        assertEquals(1, store.comments.size()); assertEquals(8, store.events.size());
    }
    @Test void auditFailureOverHttpRollsBackRootAndLedgerAndRetryReplaysExactlyOnce() throws Exception {
        var key = UUID.randomUUID(); store.failAudit = true;
        send(post("/api/v1/t3/reports"), studentActor, key, sampleReport(), 503);
        assertTrue(store.roots.isEmpty()); assertTrue(store.cache.isEmpty()); assertTrue(store.events.isEmpty());
        store.failAudit = false;
        var created = send(post("/api/v1/t3/reports"), studentActor, key, sampleReport(), 201);
        assertEquals(json.readTree(created), json.readTree(send(post("/api/v1/t3/reports"), studentActor, key, sampleReport(), 201)));
        assertEquals(1, store.roots.size()); assertEquals(1, store.events.size()); assertEquals(1, store.cache.size());
    }
    @Test void wrongActorStaleVersionAndNotCleanFileAreRejectedThroughRealBusinessLogic() throws Exception {
        var created = json.readTree(send(post("/api/v1/t3/reports"), studentActor, UUID.randomUUID(), sampleReport(), 201));
        var id = UUID.fromString(created.get("reportId").asText());
        send(post("/api/v1/t3/reports/{id}/submit", id), auth(outsider), UUID.randomUUID(), Map.of("expectedVersion", 1), 404);
        send(post("/api/v1/t3/reports/{id}/submit", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 0), 409);
        var pending = files.add(id, "PENDING");
        send(patch("/api/v1/t3/reports/{id}", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 1,
                "completedWork", "Work", "difficulties", "", "nextPlan", "Plan", "evidenceFileIds", List.of(pending)), 200);
        send(post("/api/v1/t3/reports/{id}/submit", id), studentActor, UUID.randomUUID(), Map.of("expectedVersion", 2), 409);
        assertEquals("DRAFT", store.roots.get(id).current().state().name()); assertEquals(2, store.roots.get(id).version());
        mvc.perform(get("/api/v1/t3/reports/{id}", id).principal(auth(outsider))).andExpect(status().isNotFound());
    }
}
