package vn.utbridora.auth.organization;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="organizations")
public class Organization {
    public enum Type { UNIVERSITY, ENTERPRISE }
    public enum Status { PENDING, VERIFIED, SUSPENDED }
    @Id @Column(length=36) public String id;
    @Column(nullable=false) public String name;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false) public Type type;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false) public Status status;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @PrePersist void onCreate() { if(id==null) id=UUID.randomUUID().toString(); createdAt=Instant.now(); }
}
