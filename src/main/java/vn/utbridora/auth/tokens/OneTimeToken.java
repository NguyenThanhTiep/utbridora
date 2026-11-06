package vn.utbridora.auth.tokens;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import vn.utbridora.auth.user.Role;

@Entity @Table(name="one_time_tokens")
public class OneTimeToken {
    public enum Purpose { INVITATION, PASSWORD_RESET }
    @Id @Column(length=36) public String id;
    @Column(name="token_hash", nullable=false, unique=true, length=64) public String tokenHash;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false) public Purpose purpose;
    @Column(name="user_id",length=36) public String userId;
    @Column(nullable=false) public String email;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(name="invite_role") public Role inviteRole;
    @Column(name="organization_id",length=36) public String organizationId;
    @Column(name="expires_at",nullable=false) public Instant expiresAt;
    @Column(name="consumed_at") public Instant consumedAt;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @PrePersist void onCreate(){ if(id==null) id=UUID.randomUUID().toString(); createdAt=Instant.now(); }
}
