package vn.utbridora.auth.modules.internship.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.api.ApiErrorHandler;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.audit.RequestIdFilter;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.modules.internship.application.WorkspacePorts;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.OperationErrors;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.security.AppPrincipal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Standalone HTTP mapping tests with mocked application ports. The real security filter chain,
 * JDBC, migration and ledger persistence are NOT started; these tests are not integration evidence.
 */
class SubmissionControllerTest {
    private static final UUID OWNER = id(1), ASSIGNMENT = id(2), SUBMISSION = id(3);
    private static final String PATH = "/api/v1/t3/assignments/" + ASSIGNMENT + "/submissions";
    private static final String KEY = id(4).toString();
    private static final Instant NOW = Instant.parse("2026-11-10T04:00:00Z");
    private static final String BODY = "{\"expectedAssignmentVersion\":2,\"fileIds\":[],\"url\":\"https://example.test/v1\",\"note\":null}";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final SubmissionService service = mock(SubmissionService.class);
    private final AuditService audit = mock(AuditService.class);
    private final Authentication auth = UsernamePasswordAuthenticationToken.authenticated(
            new AppPrincipal(OWNER.toString(), "synthetic@example.test", null, 1, List.of()), null, List.of());
    private MockMvc mvc;

    @BeforeEach void setup() {
        SecurityContextHolder.getContext().setAuthentication(auth);
        mvc = mvc(service);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    private static UUID id(long value) { return new UUID(0, value); }
    private static SubmissionDto submission() {
        return new SubmissionDto(SUBMISSION, ASSIGNMENT, 1, List.of(), "https://example.test/v1", null, NOW, false);
    }
    private MockMvc mvc(SubmissionService supplied) {
        var beans = new StaticListableBeanFactory();
        if (supplied != null) beans.addBean("submissions", supplied);
        var errors = new OperationErrors(audit);
        return MockMvcBuilders.standaloneSetup(new SubmissionController(beans.getBeanProvider(SubmissionService.class)))
                .setControllerAdvice(errors, new SubmissionErrors(errors), new ApiErrorHandler())
                .addFilters(new RequestIdFilter()).build();
    }
    private MvcResult submit(String body) throws Exception {
        return mvc.perform(post(PATH).principal(auth).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }
    private JsonNode body(MvcResult result) throws Exception { return JSON.readTree(result.getResponse().getContentAsString()); }
    private void error(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        var json = body(result);
        assertEquals(6, json.size());
        assertEquals(code, json.get("errorCode").asString());assertEquals(code, json.get("error").asString());
        assertEquals(result.getResponse().getHeader("X-Request-ID"), json.get("requestId").asString());
        assertDoesNotThrow(() -> UUID.fromString(json.get("requestId").asString()));
        assertTrue(json.get("fieldErrors").isArray());assertEquals(0, json.get("fieldErrors").size());
        assertTrue(json.has("retryAfterSeconds"));assertFalse(json.get("message").asString().isBlank());
    }

    @Test void postReturns201AndExactSubmissionWireShapeIncludingNullableNote() throws Exception {
        when(service.submitUrlV1(eq(auth), eq(ASSIGNMENT), eq(KEY), any())).thenReturn(submission());
        var result = submit(BODY);
        assertEquals(201, result.getResponse().getStatus());
        var json = body(result);
        assertEquals(8, json.size());assertEquals(SUBMISSION.toString(), json.get("submissionId").asString());
        assertEquals(ASSIGNMENT.toString(), json.get("assignmentId").asString());assertEquals(1, json.get("versionNo").asInt());
        assertEquals(0, json.get("fileIds").size());assertEquals("https://example.test/v1", json.get("url").asString());
        assertTrue(json.get("note").isNull());assertFalse(json.get("lateFlag").asBoolean());
        assertEquals(NOW, Instant.parse(json.get("submittedAt").asString()));
        verify(service).submitUrlV1(auth, ASSIGNMENT, KEY, new SubmissionInput(2L, List.of(), "https://example.test/v1", null));
    }

    @Test void getMapsTheExistingPageEnvelopeAndDefaults() throws Exception {
        when(service.history(auth, ASSIGNMENT, 0, 25)).thenReturn(new SubmissionPorts.HistoryPage(List.of(submission()), 26));
        var result = mvc.perform(get(PATH).principal(auth)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var json = body(result);
        assertEquals(5, json.size());assertEquals(0, json.get("page").asInt());assertEquals(25, json.get("size").asInt());
        assertEquals(26L, json.get("totalElements").asLong());assertEquals(2, json.get("totalPages").asInt());
        assertEquals(1, json.get("content").size());
        assertTrue(json.get("content").get(0).get("note").isNull());
    }

    @Test void getForwardsPaginationAndKeepsStoredVersionOrder() throws Exception {
        var old = new SubmissionDto(id(20), ASSIGNMENT, 1, List.of(), "https://example.test/old", "Old", NOW, false);
        var current = new SubmissionDto(SUBMISSION, ASSIGNMENT, 2, List.of(), "https://example.test/new", "New", NOW, true);
        when(service.history(auth, ASSIGNMENT, 1, 2)).thenReturn(new SubmissionPorts.HistoryPage(List.of(current, old), 4));
        var result = mvc.perform(get(PATH).principal(auth).param("page", "1").param("size", "2")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var json = body(result);
        assertEquals(2, json.get("content").get(0).get("versionNo").asInt());
        assertEquals("https://example.test/old", json.get("content").get(1).get("url").asString());
        verify(service).history(auth, ASSIGNMENT, 1, 2);
    }

    @Test void emptyHistoryHasZeroPagesAndDoesNotInventAnArtifact() throws Exception {
        when(service.history(auth, ASSIGNMENT, 0, 25)).thenReturn(new SubmissionPorts.HistoryPage(List.of(), 0));
        var result = mvc.perform(get(PATH).principal(auth)).andReturn();
        assertEquals(200, result.getResponse().getStatus());assertEquals(0, body(result).get("totalPages").asInt());
        assertEquals(0, body(result).get("content").size());
    }

    @Test void pageCountCannotOverflowTheHttpInteger() throws Exception {
        when(service.history(auth, ASSIGNMENT, 0, 1)).thenReturn(new SubmissionPorts.HistoryPage(List.of(), Long.MAX_VALUE));
        error(mvc.perform(get(PATH).principal(auth).param("size", "1")).andReturn(), 500, "INTERNAL_ERROR");
    }

    @ParameterizedTest @ValueSource(strings = {
        "{", "null", "[]", "true", "{}", "{} {}",
        "{\"expectedAssignmentVersion\":2,\"fileIds\":[],\"url\":\"https://example.test\"}",
        "{\"expectedAssignmentVersion\":2,\"fileIds\":[],\"url\":null,\"note\":null,\"state\":\"SUBMITTED\"}"
    })
    void malformedMissingAndUnknownJsonFieldsAre400BeforeService(String input) throws Exception {
        error(submit(input), 400, "SCHEMA_VALIDATION");verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"\"2\"", "2.0", "2e0", "null", "true", "-1", "9007199254740992"})
    void strictVersionParsingRejectsCoercionAndOutOfRangeInput(String version) throws Exception {
        error(submit(BODY.replace("\"expectedAssignmentVersion\":2", "\"expectedAssignmentVersion\":" + version)),
                400, "SCHEMA_VALIDATION");verifyNoInteractions(service);
    }

    @Test void nullableFieldsMustStillBePresentAndDoNotAcceptCoercedScalars() throws Exception {
        for (String value : List.of("42", "false", "[]", "{}")) {
            error(submit(BODY.replace("\"note\":null", "\"note\":" + value)), 400, "SCHEMA_VALIDATION");
        }
        error(submit(BODY.replace("\"note\":null", "\"note\":null,\"note\":\"duplicate\"")), 400, "SCHEMA_VALIDATION");
        error(submit(BODY + " {}"), 400, "SCHEMA_VALIDATION");verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"http://example.test", "https://user:private@example.test", "https:example.test", ""})
    void invalidUrlReturnsSafeSchemaErrorWithoutEchoingInput(String url) throws Exception {
        var result = submit(BODY.replace("https://example.test/v1", url));
        error(result, 400, "SCHEMA_VALIDATION");
        assertFalse(result.getResponse().getContentAsString().contains("private"));verifyNoInteractions(service);
    }

    @Test void artifactXorReturns422AndNeverInvokesService() throws Exception {
        error(submit(BODY.replace("\"https://example.test/v1\"", "null")), 422, "SUBMISSION_ARTIFACT_INVALID");
        error(submit(BODY.replace("\"fileIds\":[]", "\"fileIds\":[\"" + id(30) + "\"]")), 422, "SUBMISSION_ARTIFACT_INVALID");
        verifyNoInteractions(service);
    }

    @Test void aValidFileRequestIsDelegatedForFailClosedDependencyHandlingNotClaimedClean() throws Exception {
        when(service.submitUrlV1(any(), any(), anyString(), any())).thenThrow(failure(503, "DEPENDENCY_UNAVAILABLE"));
        error(submit(BODY.replace("\"fileIds\":[]", "\"fileIds\":[\"" + id(30) + "\"]")
                .replace("\"https://example.test/v1\"", "null")), 503, "DEPENDENCY_UNAVAILABLE");
    }

    @Test void unauthenticatedRequestsDoNotReachServiceEvenWithoutSecurityFilters() throws Exception {
        error(mvc.perform(post(PATH).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn(),
                401, "SESSION_EXPIRED");
        error(mvc.perform(get(PATH)).andReturn(), 401, "SESSION_EXPIRED");verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"", "bad", "1-1-1-1-1"})
    void invalidKeyIs400BeforeService(String key) throws Exception {
        error(mvc.perform(post(PATH).principal(auth).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn(), 400, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    @Test void missingHeaderOrBodyIsMappedToTheContractEnvelope() throws Exception {
        error(mvc.perform(post(PATH).principal(auth).contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn(), 400, "INVALID_REQUEST");
        error(mvc.perform(post(PATH).principal(auth).header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)).andReturn(),
                400, "INVALID_REQUEST");verifyNoInteractions(service);
    }

    @Test void invalidAssignmentIdsAreNotCoercedByUuidFromString() throws Exception {
        String path = "/api/v1/t3/assignments/1-1-1-1-1/submissions";
        error(mvc.perform(get(path).principal(auth)).andReturn(), 400, "SCHEMA_VALIDATION");
        error(mvc.perform(post(path).principal(auth).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn(), 400, "SCHEMA_VALIDATION");verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"page=-1", "page=10001", "size=0", "size=101"})
    void outOfRangePaginationIs400(String query) throws Exception {
        var pair = query.split("=");
        error(mvc.perform(get(PATH).principal(auth).param(pair[0], pair[1])).andReturn(), 400, "INVALID_PAGINATION");
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"no-number", "1.5", "2147483648"})
    void malformedPaginationHasSafeErrorMapping(String value) throws Exception {
        error(mvc.perform(get(PATH).principal(auth).param("page", value)).andReturn(), 400, "INVALID_REQUEST");
        verifyNoInteractions(service);
    }

    static Stream<Arguments> applicationFailures() {
        return Stream.of(Arguments.of(403, "ACCESS_DENIED"), Arguments.of(404, "RESOURCE_NOT_FOUND"),
                Arguments.of(409, "VERSION_CONFLICT"), Arguments.of(409, "STATE_CONFLICT"),
                Arguments.of(409, "IDEMPOTENCY_KEY_REUSED"), Arguments.of(409, "IDEMPOTENCY_RESPONSE_EXPIRED"),
                Arguments.of(422, "SUBMISSION_LATE"), Arguments.of(503, "DEPENDENCY_UNAVAILABLE"),
                Arguments.of(500, "AUDIT_WRITE_FAILED"));
    }
    @ParameterizedTest @MethodSource("applicationFailures")
    void preservesApplicationStatusAndErrorCode(int status, String code) throws Exception {
        when(service.submitUrlV1(any(), any(), anyString(), any())).thenThrow(failure(status, code));
        error(submit(BODY), status, code);
        if (status == 403 || status == 404) verify(audit).securityDenied(OWNER.toString(), code);
    }

    @Test void preservesRetryAfterAndUsesServerRequestIdInsteadOfBrowserHeader() throws Exception {
        when(service.submitUrlV1(any(), any(), anyString(), any()))
                .thenThrow(new OperationFailure(409, "REQUEST_IN_PROGRESS", "Retry safely", 1));
        var result = mvc.perform(post(PATH).principal(auth).header("Idempotency-Key", KEY).header("X-Request-ID", id(99).toString())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn();
        error(result, 409, "REQUEST_IN_PROGRESS");assertEquals("1", result.getResponse().getHeader("Retry-After"));
        assertEquals(1, body(result).get("retryAfterSeconds").asInt());
        assertNotEquals(id(99).toString(), body(result).get("requestId").asString());
    }

    static Stream<Arguments> domainFailures() {
        return Stream.of(Arguments.of(WorkspaceRuleException.Kind.STATE_CONFLICT, 409, "STATE_CONFLICT"),
                Arguments.of(WorkspaceRuleException.Kind.SCOPE_MISMATCH, 404, "RESOURCE_NOT_FOUND"),
                Arguments.of(WorkspaceRuleException.Kind.UNAVAILABLE, 503, "DEPENDENCY_UNAVAILABLE"));
    }
    @ParameterizedTest @MethodSource("domainFailures")
    void reusesWorkspaceDomainMappingAndConcealsForeignScope(WorkspaceRuleException.Kind kind, int status, String code) throws Exception {
        when(service.history(auth, ASSIGNMENT, 0, 25)).thenThrow(new WorkspaceRuleException(kind));
        error(mvc.perform(get(PATH).principal(auth)).andReturn(), status, code);
    }

    @Test void realApplicationBoundaryConcealsAnotherStudentBeforeAnyMutation() throws Exception {
        var session = mock(SubmissionPorts.Session.class);
        var access = mock(SubmissionPorts.Access.class);var cases = mock(WorkspacePorts.CaseChecks.class);
        var cohort = new Cohort(id(40), new WorkspaceScope(id(41), id(42), id(43)), "C", "Cohort", 1);
        var group = new InternshipGroup(id(44), cohort.id(), "G", List.of(id(45)), List.of(), 1);
        var assignment = new AssignmentDto(ASSIGNMENT, id(45), id(46), AssignmentState.IN_PROGRESS, 2L, null);
        var task = new InternshipTask(new TaskDto(id(47), group.id(), "Task", "Description", NOW, TaskPriority.NORMAL, true, 1L, List.of(assignment)));
        when(session.assignment(ASSIGNMENT)).thenReturn(new SubmissionPorts.AssignmentContext(cohort, group, task, assignment));
        var work = new SubmissionPorts.UnitOfWork() {
            public <T> T execute(Function<SubmissionPorts.Session, T> operation) { return operation.apply(session); }
        };
        mvc = mvc(new SubmissionService(work, access, cases, Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID));
        error(submit(BODY), 404, "RESOURCE_NOT_FOUND");
        verifyNoInteractions(access, cases);verify(session, never()).idempotent(any(), any());
        verify(session, never()).appendFirst(any(), any());
    }

    @Test void missingRuntimeServiceIs503ForBothRoutesButMalformedJsonStillFailsValidation() throws Exception {
        mvc = mvc(null);
        error(submit(BODY), 503, "DEPENDENCY_UNAVAILABLE");
        error(mvc.perform(get(PATH).principal(auth)).andReturn(), 503, "DEPENDENCY_UNAVAILABLE");
        error(submit("{"), 400, "SCHEMA_VALIDATION");
        verifyNoInteractions(service);
    }

    @Test void unexpectedErrorsAreSafeAndConcurrencyKeepsVersionConflictMapping() throws Exception {
        when(service.submitUrlV1(any(), any(), anyString(), any())).thenThrow(new IllegalStateException("SQL/private/secret"));
        var unexpected = submit(BODY);error(unexpected, 500, "INTERNAL_ERROR");
        assertFalse(unexpected.getResponse().getContentAsString().contains("SQL/private/secret"));
        doThrow(new org.springframework.dao.ConcurrencyFailureException("SQL/private/secret"))
                .when(service).submitUrlV1(any(), any(), anyString(), any());
        error(submit(BODY), 409, "VERSION_CONFLICT");
    }
}
