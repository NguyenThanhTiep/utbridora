package vn.utbridora.auth.organization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import vn.utbridora.auth.user.Role;

@Entity @Table(name="memberships")
public class Membership {
    @Id @Column(length=36) public String id;
    @Column(name="user_id", nullable=false, length=36) public String userId;
    @Column(name="organization_id", length=36) public String organizationId;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false) public Role role;
    @Column(nullable=false) public boolean active=true;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at") public Instant updatedAt;
    @PrePersist void onCreate(){ if(id==null) id=UUID.randomUUID().toString(); createdAt=Instant.now(); }
}
