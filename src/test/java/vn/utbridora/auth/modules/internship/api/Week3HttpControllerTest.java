package vn.utbridora.auth.modules.internship.api;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.modules.internship.infrastructure.*;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.security.AppPrincipal;
import static vn.utbridora.auth.modules.internship.api.ReportDtos.*;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Real MVC dispatch/parser/serialization with mocked service boundaries. No application, JDBC,
 * Flyway or production chain. CSRF test uses the real Spring filter/repository in isolation. */
class Week3HttpControllerTest {
    private final WeeklyReportService reports = mock(WeeklyReportService.class);
    private final WeeklyReportCommands reportCommands = mock(WeeklyReportCommands.class);
    private final TaskCommentService comments = mock(TaskCommentService.class);
    private final TaskCommentCommands commentCommands = mock(TaskCommentCommands.class);
    private final AuditService audit = mock(AuditService.class);
    private final UUID user = UUID.randomUUID(), caseId = UUID.randomUUID(), reportId = UUID.randomUUID(),
            assignmentId = UUID.randomUUID(), key = UUID.randomUUID();
    private final Authentication actor = UsernamePasswordAuthenticationToken.authenticated(
            new AppPrincipal(user.toString(), "unit@example.test", null, 4, List.of()), null, List.of());
    private final JsonMapper json = JsonMapper.builder().build();
    private final ReportInput input = new ReportInput(caseId, LocalDate.parse("2026-11-02"), "Work", "", "Plan", List.of());
    private final WeeklyReport root = WeeklyReport.create(reportId, caseId, input.weekStart(), LocalDate.parse("2026-11-04"),
            LocalDate.parse("2026-12-31"), input.content());
    private final ReportDto dto = ReportDto.from(root, root.current());
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = standaloneSetup(new WeeklyReportController(reports, reportCommands), new TaskCommentController(comments, commentCommands))
                .setControllerAdvice(new OperationErrors(audit), new Week3HttpErrors()).build();
    }
    private String json(Object value) { return json.writeValueAsString(value); }
    private MockHttpServletRequestBuilder mutate(MockHttpServletRequestBuilder request, Object body) {
        return request.principal(actor).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json(body));
    }
    @Test void createReportUsesClosedParserAndReturns201() throws Exception {
        when(reportCommands.create(actor, key, input)).thenReturn(dto);
        mvc.perform(mutate(post("/api/v1/t3/reports"), input)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").value(reportId.toString())).andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.dueAt").value("2026-11-10T16:59:59Z"));
        verify(reportCommands).create(actor, key, input); verifyNoInteractions(reports);
    }
    @Test void updateSubmitReviewAndRevisionKeepExactContractRoutesDtosAndStatus() throws Exception {
        var update = new UpdateReport(1L, "Edited", "", "Plan", List.of());
        when(reportCommands.update(actor, reportId, key, update)).thenReturn(dto);
        mvc.perform(mutate(patch("/api/v1/t3/reports/{id}", reportId), update)).andExpect(status().isOk());
        var submit = new SubmitReport(1L);
        when(reportCommands.submit(actor, reportId, key, submit)).thenReturn(dto);
        mvc.perform(mutate(post("/api/v1/t3/reports/{id}/submit", reportId), submit)).andExpect(status().isOk());
        var review = new ReportReview(1L, "Feedback");
        when(reportCommands.review(actor, reportId, key, review)).thenReturn(dto);
        mvc.perform(mutate(post("/api/v1/t3/reports/{id}/reviews", reportId), review)).andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").doesNotExist()).andExpect(jsonPath("$.reviewerUserId").doesNotExist());
        var revision = new CreateReportRevision(1L, "CORRECTION");
        when(reportCommands.revision(actor, reportId, key, revision)).thenReturn(dto);
        mvc.perform(mutate(post("/api/v1/t3/reports/{id}/revisions", reportId), revision)).andExpect(status().isCreated());
        verify(reportCommands).update(actor, reportId, key, update); verify(reportCommands).submit(actor, reportId, key, submit);
        verify(reportCommands).review(actor, reportId, key, review); verify(reportCommands).revision(actor, reportId, key, revision);
    }
    @Test void reportCurrentRevisionAndListAreReadThroughService() throws Exception {
        when(reports.read(actor, reportId, new ReportReadQuery(null))).thenReturn(dto);
        when(reports.read(actor, reportId, new ReportReadQuery(1))).thenReturn(dto);
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor).param("revisionNo", "1")).andExpect(status().isOk());
        var query = new ReportQuery(caseId, WeeklyReport.State.DRAFT, null, null);
        when(reports.list(actor, query)).thenReturn(new ReportPage(List.of(dto), 0, 25, 1, 1));
        mvc.perform(get("/api/v1/t3/reports").principal(actor).param("caseId", caseId.toString()).param("state", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        verify(reports).read(actor, reportId, new ReportReadQuery(null)); verify(reports).read(actor, reportId, new ReportReadQuery(1));
        verify(reports).list(actor, query); verifyNoInteractions(reportCommands);
    }
    @Test void commentCreateAndListUseExistingDtoAndPagination() throws Exception {
        var comment = new CommentDto(UUID.randomUUID(), assignmentId, user, "Discussion", Instant.parse("2026-11-10T00:00:00Z"));
        when(commentCommands.create(actor, assignmentId, key, new CommentInput("Discussion"))).thenReturn(comment);
        mvc.perform(mutate(post("/api/v1/t3/assignments/{id}/comments", assignmentId), Map.of("text", " Discussion ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.authorUserId").value(user.toString()));
        when(comments.list(actor, assignmentId, 0, 25)).thenReturn(new TaskCommentPorts.CommentPage(List.of(comment), 0, 25, 1, 1));
        mvc.perform(get("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].assignmentId").value(assignmentId.toString()));
        verify(commentCommands).create(actor, assignmentId, key, new CommentInput("Discussion"));
        verify(comments).list(actor, assignmentId, 0, 25);
    }
    @ParameterizedTest @ValueSource(strings = {"", "bad", "00000000-0-0-0-000000000001", " 00000000-0000-4000-8000-000000000001"})
    void malformedIdempotencyKeyDoesNotReachService(String invalid) throws Exception {
        mvc.perform(post("/api/v1/t3/reports").principal(actor).header("Idempotency-Key", invalid)
                .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        verifyNoInteractions(reportCommands, reports);
    }
    @Test void missingKeyIsRejectedForBothResources() throws Exception {
        mvc.perform(post("/api/v1/t3/reports").principal(actor).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(actor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Hi\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reportCommands, commentCommands);
    }
    @ParameterizedTest @ValueSource(strings = {"{\"expectedVersion\":1,\"lateFlag\":false}", "{\"expectedVersion\":\"1\"}",
            "{\"expectedVersion\":1,\"expectedVersion\":2}", "{\"expectedVersion\":1} {}", "[]", "null"})
    void forgedOrMalformedReportBodyIsRejectedBeforeMutation(String body) throws Exception {
        mvc.perform(post("/api/v1/t3/reports/{id}/submit", reportId).principal(actor).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SCHEMA_VALIDATION"));
        verifyNoInteractions(reportCommands);
    }
    @ParameterizedTest @ValueSource(strings = {"{\"text\":\"Hi\",\"authorUserId\":\"forged\"}",
            "{\"text\":\"Hi\",\"verdict\":\"ACCEPT\"}", "{\"text\":\"Hi\",\"reportId\":\"other\"}", "{\"text\":\"\"}"})
    void taskCommentCannotBecomeSubmissionReviewOrReportReview(String body) throws Exception {
        mvc.perform(post("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(actor).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(commentCommands);
    }
    @Test void unauthenticatedRequestsAreDeniedBeforeServices() throws Exception {
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/t3/reports").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/t3/assignments/{id}/comments", assignmentId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reports, reportCommands, comments);
    }
    @Test void duplicateAndUnknownQueriesCannotSelectAnotherOwnerOrRevision() throws Exception {
        mvc.perform(get("/api/v1/t3/reports").principal(actor).param("caseId", caseId.toString(), UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor).param("revisionNo", "1", "2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/t3/reports").principal(actor).param("caseId", caseId.toString()).param("actorId", user.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(actor).param("page", "0", "1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(actor).param("caseId", caseId.toString()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports, comments);
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "a", "2147483648", "1.5"})
    void malformedPaginationIsRejected(String page) throws Exception {
        mvc.perform(get("/api/v1/t3/assignments/{id}/comments", assignmentId).principal(actor).param("page", page))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(comments);
    }
    @Test void invalidRevisionStateAndPathCannotReachReadService() throws Exception {
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor).param("revisionNo", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/t3/reports").principal(actor).param("caseId", caseId.toString()).param("state", "ACCEPT"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/t3/reports/1-1-1-1-1").principal(actor)).andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }
    @Test void sharedErrorsPreserveDeniedAuditRequestIdAndDependencyFailureWithoutLeakingPayload() throws Exception {
        var requestId = UUID.randomUUID().toString();
        when(reports.read(actor, reportId, new ReportReadQuery(null))).thenThrow(WorkflowValues.failure(404, "RESOURCE_NOT_FOUND"));
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor).requestAttr("bridora.request_id", requestId))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value(requestId)).andExpect(jsonPath("$.completedWork").doesNotExist());
        verify(audit).securityDenied(any(), eq("RESOURCE_NOT_FOUND"));
        doThrow(WorkflowValues.failure(503, "DEPENDENCY_UNAVAILABLE")).when(reports).read(actor, reportId, new ReportReadQuery(null));
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor)).andExpect(status().isServiceUnavailable());
    }
    @Test void missingBodyAndWorkspaceConflictUseScopedErrorEnvelope() throws Exception {
        mvc.perform(post("/api/v1/t3/reports").principal(actor).header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isArray());
        when(reports.read(actor, reportId, new ReportReadQuery(null))).thenThrow(new WorkspaceRuleException(WorkspaceRuleException.Kind.VERSION_CONFLICT));
        mvc.perform(get("/api/v1/t3/reports/{id}", reportId).principal(actor)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"));
    }
    @Test void realCsrfFilterRejectsMissingAndInvalidTokenBeforeCommands() throws Exception {
        var repository = new HttpSessionCsrfTokenRepository();
        var secured = standaloneSetup(new WeeklyReportController(reports, reportCommands))
                .setControllerAdvice(new OperationErrors(audit), new Week3HttpErrors()).addFilters(new CsrfFilter(repository)).build();
        secured.perform(mutate(post("/api/v1/t3/reports"), input)).andExpect(status().isForbidden());
        var request = new MockHttpServletRequest(); var session = new MockHttpSession(); request.setSession(session);
        var token = repository.generateToken(request); repository.saveToken(token, request, new MockHttpServletResponse());
        // Match Spring Security's default XOR/BREACH token handling; do not disable masking.
        new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> token);
        var masked = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        secured.perform(mutate(post("/api/v1/t3/reports"), input).session(session).header(token.getHeaderName(), "bad"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportCommands);
        when(reportCommands.create(actor, key, input)).thenReturn(dto);
        secured.perform(mutate(post("/api/v1/t3/reports"), input).session(session).header(token.getHeaderName(), masked.getToken()))
                .andExpect(status().isCreated());
    }
    @Test void defaultOffGateRegistersNoHttpAdaptersAndExplicitActivationWithoutRealProvidersFails() {
        var runner = new ApplicationContextRunner().withUserConfiguration(WeeklyReportController.class, TaskCommentController.class, Week3HttpErrors.class);
        runner.run(context -> {
            assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(WeeklyReportController.class).isEmpty());
            assertTrue(context.getBeansOfType(TaskCommentController.class).isEmpty()); assertTrue(context.getBeansOfType(Week3HttpErrors.class).isEmpty());
        });
        runner.withPropertyValues("app.t3.week3.http.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
    }
}
