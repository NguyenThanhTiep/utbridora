package vn.utbridora.auth.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "users")
public class UserAccount {
    @Id @Column(length=36) public String id;
    @Column(nullable=false, unique=true, length=254) public String email;
    @Column(name="password_hash", nullable=false) public String passwordHash;
    @Column(name="display_name", nullable=false) public String displayName;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false) public AccountStatus status;
    @Column(name="security_version", nullable=false) public long securityVersion;
    @Column(name="failed_attempts", nullable=false) public int failedAttempts;
    @Column(name="locked_until") public Instant lockedUntil;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;
    @PrePersist void onCreate(){ if(id == null) id=UUID.randomUUID().toString(); createdAt=Instant.now(); updatedAt=createdAt; }
    @PreUpdate void onUpdate(){ updatedAt=Instant.now(); }
}
