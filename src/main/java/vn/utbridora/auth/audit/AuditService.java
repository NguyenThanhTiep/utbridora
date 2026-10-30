package vn.utbridora.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Strict allow-list fields only. No request body, token, credentials, or raw query string in audit. */
@Service
public class AuditService {
    private final AuditRepository repository;
    public AuditService(AuditRepository repository){this.repository=repository;}
    @Transactional
    public void write(String actor,String action,String resourceType,String resourceId,String result){
        writeScoped(actor,action,resourceType,resourceId,result,null,null);
    }
    @Transactional
    public void writeScoped(String actor,String action,String resourceType,String resourceId,
                            String result,String organizationId,String reasonCode) {
        store(actor,action,resourceType,resourceId,result,organizationId,reasonCode);
    }
    /** Denial events survive rollback of the failing business transaction. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void securityDenied(String actor,String reasonCode) {
        store(actor,"ACCESS_DENIED","http_request",null,"DENIED",null,reasonCode);
    }
    private void store(String actor,String action,String resourceType,String resourceId,
                       String result,String organizationId,String reasonCode) {
        AuditAction.valueOf(action);
        if(resourceType==null || !resourceType.matches("[a-z][a-z0-9_]{0,79}")
            || !java.util.Set.of("SUCCESS","FAILURE","DENIED").contains(result)
            || reasonCode!=null && !reasonCode.matches("[A-Z0-9_]{3,64}"))
            throw new IllegalArgumentException("Invalid audit metadata");
        validateId(actor);validateId(resourceId);validateId(organizationId);
        AuditLog event=new AuditLog();event.actorId=actor;event.action=action;
        event.resourceType=resourceType;event.resourceId=resourceId;event.result=result;
        event.organizationId=organizationId;event.reasonCode=reasonCode;
        event.requestId=org.slf4j.MDC.get("requestId");
        var attributes=RequestContextHolder.getRequestAttributes();
        if(attributes instanceof ServletRequestAttributes web) {
            HttpServletRequest req=web.getRequest();
            Object id=req.getAttribute("bridora.request_id");
            if(id instanceof String s) event.requestId=s;
        }
        if(event.requestId==null) event.requestId=java.util.UUID.randomUUID().toString();
        repository.save(event);
    }
    private void validateId(String id) {
        if(id!=null && !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Audit IDs must be UUID values");
    }
}
