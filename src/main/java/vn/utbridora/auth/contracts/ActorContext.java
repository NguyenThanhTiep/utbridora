package vn.utbridora.auth.contracts;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.security.AppPrincipal;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Server-only USER context. Providers still re-read current authority; there is no implicit SYSTEM bypass. */
public final class ActorContext {
    private final Authentication authentication;
    private final String organizationId,requestId,operationId;
    private ActorContext(Authentication auth,String org) {
        actor(auth);this.authentication=auth;this.organizationId=org==null?null:uuid(org);
        String request=MDC.get("requestId");
        this.requestId=request==null?UUID.randomUUID().toString():uuid(request);
        this.operationId=UUID.randomUUID().toString();
    }
    public static ActorContext fromAuthenticatedWorkflow(Authentication auth,String organizationId) {return new ActorContext(auth,organizationId);}
    public Authentication authentication() {return authentication;}
    public String organizationId() {return organizationId;}
    public String actorId() {return actor(authentication);}
    public String actorKind() {return "USER";}
    public String requestId() {return requestId;}
    public String operationId() {return operationId;}
    public long currentSecurityVersion() {return ((AppPrincipal)authentication.getPrincipal()).securityVersion();}
}
