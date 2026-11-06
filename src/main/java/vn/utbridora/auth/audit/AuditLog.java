package vn.utbridora.auth.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="audit_logs")
public class AuditLog {
    @Id @Column(length=36) public String id;
    @Column(name="actor_id",length=36) public String actorId;
    @Column(nullable=false,length=90) public String action;
    @Column(name="resource_type",nullable=false,length=80) public String resourceType;
    @Column(name="resource_id",length=36) public String resourceId;
    @Column(nullable=false,length=20) public String result;
    @Column(name="organization_id",length=36) public String organizationId;
    @Column(name="request_id",length=80) public String requestId;
    @Column(name="reason_code",length=64) public String reasonCode;
    @Column(nullable=false,length=32) public String source="APPLICATION";
    @Column(name="event_version",nullable=false) public short eventVersion=1;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @PrePersist void onCreate(){if(id==null) id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();}
}
