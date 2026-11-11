package vn.utbridora.auth.modules.internship.api;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.modules.internship.application.SubmissionService;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.security.*;
import vn.utbridora.auth.user.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Real SecurityConfig/filter chain + MVC; owner repository/service mocked. Not DB/E2E evidence. */
class SubmissionSecurityTest {
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,SubmissionController.class,SubmissionReviewController.class,SubmissionErrors.class,OperationErrors.class})
    static class Fixture {
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean UserDetailsService userDetails(){return mock(UserDetailsService.class);}
        @Bean AuditService audit(){return mock(AuditService.class);}
        @Bean SubmissionService submissions(){return mock(SubmissionService.class);}
    }
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private SubmissionService service;
    private UserAccount current;
    private Authentication actor;
    private final String id=new UUID(0,1).toString(),key=new UUID(0,2).toString();
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Fixture.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(new RequestIdFilter()).apply(springSecurity()).build();
        service=context.getBean(SubmissionService.class);current=new UserAccount();current.id=id;current.status=AccountStatus.ACTIVE;current.securityVersion=1;
        when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(current));
        actor=UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(id,"synthetic@example.test",null,1,List.of()),null,List.of());
    }
    @AfterEach void close(){context.close();}
    private String path(String kind){return kind.equals("review")?"/api/v1/t3/submissions/"+id+"/reviews":"/api/v1/t3/assignments/"+id+"/submissions";}
    private String body(String kind){return kind.equals("review")?"{\"expectedAssignmentVersion\":3,\"verdict\":\"ACCEPT\",\"comment\":\"ok\"}":"{\"expectedAssignmentVersion\":2,\"fileIds\":[],\"url\":\"https://example.test/v1\",\"note\":null}";}
    private void error(MvcResult r,int status,String code) throws Exception {
        assertEquals(status,r.getResponse().getStatus());var node=JsonMapper.builder().build().readTree(r.getResponse().getContentAsString());
        assertEquals(6,node.size());assertEquals(code,node.get("errorCode").asString());assertEquals(r.getResponse().getHeader("X-Request-ID"),node.get("requestId").asString());
    }
    @ParameterizedTest @ValueSource(strings={"review","submit"})
    void anonymousCannotReachMutationEvenWithCsrf(String kind) throws Exception {
        error(mvc.perform(post(path(kind)).servletPath(path(kind)).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body(kind))).andReturn(),401,"SESSION_EXPIRED");
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"review","submit"})
    void authenticatedMutationRequiresCsrf(String kind) throws Exception {
        error(mvc.perform(post(path(kind)).servletPath(path(kind)).with(authentication(actor)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body(kind))).andReturn(),403,"CSRF_INVALID");
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"review","submit"})
    void revokedSecurityVersionIsCheckedByTheRealAccountFilter(String kind) throws Exception {
        current.securityVersion=2;
        error(mvc.perform(post(path(kind)).servletPath(path(kind)).with(authentication(actor)).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body(kind))).andReturn(),401,"SESSION_EXPIRED");
        verifyNoInteractions(service);
    }
    @Test void historyIsNotAnonymousPublicData() throws Exception {
        var path=path("submit");error(mvc.perform(get(path).servletPath(path)).andReturn(),401,"SESSION_EXPIRED");verifyNoInteractions(service);
    }
    @Test void validSecurityContextStillCannotBypassTheBusinessPermission() throws Exception {
        when(service.review(any(),any(),anyString(),any())).thenThrow(WorkflowValues.failure(403,"ACCESS_DENIED"));
        var path=path("review");error(mvc.perform(post(path).servletPath(path).with(authentication(actor)).with(csrf()).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body("review"))).andReturn(),403,"ACCESS_DENIED");
        verify(service).review(any(),any(),eq(key),any());
    }
}
