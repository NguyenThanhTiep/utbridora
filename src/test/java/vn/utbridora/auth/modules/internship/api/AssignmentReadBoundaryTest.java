package vn.utbridora.auth.modules.internship.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException.Kind;
import vn.utbridora.auth.modules.internship.infrastructure.*;
import vn.utbridora.auth.tokens.MentorInvitationService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP error envelope regression: denied authority must never escape as a generic 500. */
class AssignmentReadBoundaryTest {
    @ParameterizedTest @EnumSource(value=Kind.class,names={"DENIED","NOT_FOUND","INVALID_OPERATION"})
    void managementReadMapsDomainFailure(Kind kind) throws Exception {
        var service=mock(AssignmentManagementReadService.class);
        when(service.assignments(any(),anyString(),anyString(),anyString(),anyBoolean(),anyInt(),anyInt())).thenThrow(new WorkspaceRuleException(kind));
        var mvc=MockMvcBuilders.standaloneSetup(new AssignmentManagementReadController(service)).setControllerAdvice(new WorkspaceErrors()).build();
        int status=kind==Kind.DENIED?403:kind==Kind.NOT_FOUND?404:422;
        String code=kind==Kind.DENIED?"ACCESS_DENIED":kind==Kind.NOT_FOUND?"RESOURCE_NOT_FOUND":"BUSINESS_VALIDATION";
        mvc.perform(get("/api/v1/t3/assignment-management/assignments").param("schoolOrgId","school").param("enterpriseOrgId","enterprise").param("termId","term"))
            .andExpect(status().is(status)).andExpect(jsonPath("$.errorCode").value(code)).andExpect(jsonPath("$.requestId").isNotEmpty());
    }
    @Test void invitationControllerAlsoMapsSelectedAuthorityDenial() throws Exception {
        var invitations=mock(MentorInvitationService.class);
        when(invitations.sent(any(),anyString(),anyString(),anyInt(),anyInt())).thenThrow(new WorkspaceRuleException(Kind.DENIED));
        var mvc=MockMvcBuilders.standaloneSetup(new WorkspaceAssignmentController(mock(WorkspaceAssignmentService.class),invitations)).setControllerAdvice(new WorkspaceErrors()).build();
        mvc.perform(get("/api/v1/t3/mentor-invitations/sent").param("enterpriseOrgId","enterprise").param("termId","term"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}
