package vn.utbridora.auth.audit;

import java.time.Instant;

public record AuditLogView(String id,String actorId,String action,String resourceType,String resourceId,
                           String result,String organizationId,String requestId,String reasonCode,Instant occurredAt) {
    public static AuditLogView from(AuditLog e){
        return new AuditLogView(e.id,e.actorId,e.action,e.resourceType,e.resourceId,
            e.result,e.organizationId,e.requestId,e.reasonCode,e.createdAt);
    }
}
