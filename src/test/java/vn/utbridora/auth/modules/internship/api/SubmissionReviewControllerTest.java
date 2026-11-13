package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.security.AppPrincipal;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real MVC mapping with mocked application service; not MySQL or QA evidence. */
class SubmissionReviewControllerTest {
    private final UUID submission=new UUID(0,1),review=new UUID(0,2),mentor=new UUID(0,3);
    private final String key=new UUID(0,4).toString();
    private final Authentication auth=UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(mentor.toString(),"synthetic@example.test",null,1,List.of()),null,List.of());
    private final SubmissionService service=mock(SubmissionService.class);
    private final AuditService audit=mock(AuditService.class);
    private MockMvc mvc;
    private static final String BODY="{\"expectedAssignmentVersion\":3,\"verdict\":\"ACCEPT\",\"comment\":\"Accepted\"}";
    @ParameterizedTest @EnumSource(ReviewVerdict.class)
    void getReturnsStoredReviewWithoutMutationHeaders(ReviewVerdict verdict) throws Exception {
        var stored = new ReviewDto(review, submission, 1, verdict, "Stored feedback", mentor,
                Instant.parse("2026-11-10T00:00:00Z"), 4L);
        when(service.readReview(auth, submission)).thenReturn(stored);
        var result = mvc.perform(get("/api/v1/t3/submissions/" + submission + "/reviews").principal(auth)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        var node = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertEquals(8, node.size());
        assertEquals("Stored feedback", node.get("comment").asString());
        assertEquals(verdict.name(), node.get("verdict").asString());
        assertEquals(review.toString(), node.get("reviewId").asString());
        verify(service).readReview(auth, submission);
        verifyNoMoreInteractions(service);
    }

    @Test void getUnreviewedReturns204WithEmptyBody() throws Exception {
        var result = mvc.perform(get("/api/v1/t3/submissions/" + submission + "/reviews").principal(auth)).andReturn();
        assertEquals(204, result.getResponse().getStatus());
        assertEquals("", result.getResponse().getContentAsString());
    }

    @Test void getRejectsInvalidIdAndUnavailableService() throws Exception {
        error(mvc.perform(get("/api/v1/t3/submissions/invalid/reviews").principal(auth)).andReturn(), 400, "SCHEMA_VALIDATION");
        verifyNoInteractions(service);
        mvc = mvc(null);
        error(mvc.perform(get("/api/v1/t3/submissions/" + submission + "/reviews").principal(auth)).andReturn(), 503, "DEPENDENCY_UNAVAILABLE");
    }

    @Test void getUsesExistingHiddenResourceErrorEnvelope() throws Exception {
        when(service.readReview(auth, submission)).thenThrow(failure(404, "RESOURCE_NOT_FOUND"));
        error(mvc.perform(get("/api/v1/t3/submissions/" + submission + "/reviews").principal(auth)).andReturn(), 404, "RESOURCE_NOT_FOUND");
    }

    @BeforeEach void setup() {SecurityContextHolder.getContext().setAuthentication(auth);mvc=mvc(service);}
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    private MockMvc mvc(SubmissionService supplied) {
        var beans=new StaticListableBeanFactory();if(supplied!=null)beans.addBean("service",supplied);
        var errors=new OperationErrors(audit);
        return MockMvcBuilders.standaloneSetup(new SubmissionReviewController(beans.getBeanProvider(SubmissionService.class)))
                .setControllerAdvice(errors,new SubmissionErrors(errors)).addFilters(new RequestIdFilter()).build();
    }
    private MvcResult postReview(String id,String operationKey,String body) throws Exception {
        var request=post("/api/v1/t3/submissions/"+id+"/reviews").principal(auth).contentType(MediaType.APPLICATION_JSON).content(body);
        if(operationKey!=null)request.header("Idempotency-Key",operationKey);
        return mvc.perform(request).andReturn();
    }
    private void error(MvcResult result,int status,String code) throws Exception {
        assertEquals(status,result.getResponse().getStatus());var node=JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertEquals(6,node.size());assertEquals(code,node.get("errorCode").asString());assertEquals(code,node.get("error").asString());
        assertEquals(result.getResponse().getHeader("X-Request-ID"),node.get("requestId").asString());
    }
    @ParameterizedTest @EnumSource(ReviewVerdict.class)
    void bothVerdictsReturn201WithExactContractShape(ReviewVerdict verdict) throws Exception {
        var output=new ReviewDto(review,submission,2,verdict,"Reviewed",mentor,Instant.parse("2026-11-10T00:00:00Z"),6L);
        when(service.review(eq(auth),eq(submission),eq(key),any())).thenReturn(output);
        var result=postReview(submission.toString(),key,"{\"expectedAssignmentVersion\":5,\"verdict\":\""+verdict+"\",\"comment\":\" Reviewed \"}");
        assertEquals(201,result.getResponse().getStatus());var node=JsonMapper.builder().build().readTree(result.getResponse().getContentAsString());
        assertEquals(8,node.size());assertEquals(2,node.get("reviewedVersionNo").asInt());assertEquals(verdict.name(),node.get("verdict").asString());
        assertEquals(6,node.get("assignmentVersion").asLong());assertEquals(submission.toString(),node.get("submissionId").asString());
        verify(service).review(eq(auth),eq(submission),eq(key),eq(new ReviewInput(5L,verdict,"Reviewed")));
    }
    @ParameterizedTest @ValueSource(strings={"{}","[]","null","{","{\"expectedAssignmentVersion\":3,\"verdict\":\"ACCEPT\"}",
        "{\"expectedAssignmentVersion\":3,\"verdict\":\"ACCEPT\",\"comment\":\" \"}",
        "{\"expectedAssignmentVersion\":\"3\",\"verdict\":\"ACCEPT\",\"comment\":\"ok\"}",
        "{\"expectedAssignmentVersion\":3,\"verdict\":\"UNKNOWN\",\"comment\":\"ok\"}",
        "{\"expectedAssignmentVersion\":3,\"verdict\":\"ACCEPT\",\"comment\":\"ok\",\"state\":\"COMPLETED\"}"})
    void invalidJsonNeverReachesTheApplication(String body) throws Exception {
        error(postReview(submission.toString(),key,body),400,"SCHEMA_VALIDATION");verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"bad-key","","1-1-1-1-1"})
    void invalidOperationKeyNeverReachesTheApplication(String invalid) throws Exception {
        error(postReview(submission.toString(),invalid,BODY),400,"INVALID_REQUEST");verifyNoInteractions(service);
    }
    @Test void missingHeaderAndMalformedIdUseExistingErrorEnvelope() throws Exception {
        error(postReview(submission.toString(),null,BODY),400,"INVALID_REQUEST");
        error(postReview("invalid",key,BODY),400,"SCHEMA_VALIDATION");verifyNoInteractions(service);
    }
    @Test void missingServiceIs503RatherThanAStubSuccess() throws Exception {
        mvc=mvc(null);error(postReview(submission.toString(),key,BODY),503,"DEPENDENCY_UNAVAILABLE");
    }
    static java.util.stream.Stream<Arguments> failures() {
        return java.util.stream.Stream.of(Arguments.of(401,"SESSION_EXPIRED"),Arguments.of(403,"ACCESS_DENIED"),Arguments.of(404,"RESOURCE_NOT_FOUND"),
            Arguments.of(409,"STALE_SUBMISSION"),Arguments.of(409,"VERSION_CONFLICT"),Arguments.of(409,"STATE_CONFLICT"),
            Arguments.of(409,"IDEMPOTENCY_KEY_REUSED"),Arguments.of(503,"DEPENDENCY_UNAVAILABLE"),Arguments.of(500,"AUDIT_WRITE_FAILED"));
    }
    @ParameterizedTest @MethodSource("failures")
    void businessFailuresKeepCanonicalStatusAndCode(int status,String code) throws Exception {
        when(service.review(any(),any(),anyString(),any())).thenThrow(failure(status,code));
        error(postReview(submission.toString(),key,BODY),status,code);
    }
    @Test void inProgressPreservesRetryAfter() throws Exception {
        when(service.review(any(),any(),anyString(),any())).thenThrow(new OperationFailure(409,"REQUEST_IN_PROGRESS","Busy",1));
        var result=postReview(submission.toString(),key,BODY);error(result,409,"REQUEST_IN_PROGRESS");assertEquals("1",result.getResponse().getHeader("Retry-After"));
    }
}
