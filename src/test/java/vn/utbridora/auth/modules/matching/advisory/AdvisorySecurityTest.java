package vn.utbridora.auth.modules.matching.advisory;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import vn.utbridora.auth.audit.*;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.security.*;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.modules.matching.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Real default-deny security chain and account/CSRF filter; service owner is mocked. */
class AdvisorySecurityTest {
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,AdvisoryController.class,OperationErrors.class})
    static class Fixture {
        @Bean UserRepository users(){return mock(UserRepository.class);}
        @Bean UserDetailsService userDetails(){return mock(UserDetailsService.class);}
        @Bean AuditService audit(){return mock(AuditService.class);}
        @Bean AdvisoryService advisory(){return mock(AdvisoryService.class);}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;AdvisoryService service;UserAccount current;Authentication actor;
    String id="a17e2026-1121-4000-8000-000000000001";
    String meta(){return "/api/v1/matching/advisories/"+id;}
    String create(){return "/api/v1/matching/runs/"+id+"/advisories";}
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(Fixture.class);context.refresh();
        service=context.getBean(AdvisoryService.class);current=new UserAccount();current.id=id;current.status=AccountStatus.ACTIVE;current.securityVersion=1;
        when(context.getBean(UserRepository.class).findById(id)).thenReturn(Optional.of(current));
        var p=new AppPrincipal(id,"synthetic@example.test",null,1,List.of());actor=UsernamePasswordAuthenticationToken.authenticated(p,null,List.of());
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(new AdvisoryNoStoreFilter()).apply(springSecurity()).build();
    }
    @AfterEach void close(){context.close();}
    @Test void metadataIsNotAnonymous() throws Exception {mvc.perform(get(meta()).servletPath(meta())).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED")).andExpect(header().string("Cache-Control","no-store"));verifyNoInteractions(service);}
    @Test void missingCsrfCannotCreateJob() throws Exception {mvc.perform(post(create()).servletPath(create()).with(authentication(actor)).header("Idempotency-Key",id).contentType("application/json").content("{}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"));verifyNoInteractions(service);}
    @Test void authenticatedNewRouteReachesBusinessGateNotDefaultDeny() throws Exception {when(service.metadata(any(),eq(id))).thenThrow(WorkflowValues.failure(503,"AI_ADVISORY_DISABLED"));mvc.perform(get(meta()).servletPath(meta()).with(authentication(actor))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("AI_ADVISORY_DISABLED"));verify(service).metadata(any(),eq(id));}
    @Test void revokedAccountSecurityVersionStopsBeforeController() throws Exception {current.securityVersion=2;mvc.perform(get(meta()).servletPath(meta()).with(authentication(actor))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("SESSION_EXPIRED"));verifyNoInteractions(service);}
    @Test void unsupportedMethodDoesNotGainAccess() throws Exception {mvc.perform(delete(meta()).servletPath(meta()).with(authentication(actor)).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(service);}
}
