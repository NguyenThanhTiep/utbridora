package vn.utbridora.auth.user;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="account_status_history")
public class AccountStatusHistory {
    @Id @Column(length=36) public String id;
    @Column(name="user_id",nullable=false,length=36) public String userId;
    @Column(name="actor_id",nullable=false,length=36) public String actorId;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(name="from_status",nullable=false) public AccountStatus fromStatus;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(name="to_status",nullable=false) public AccountStatus toStatus;
    @Column(name="reason_code",nullable=false,length=64) public String reasonCode;
    @Column(name="changed_at",nullable=false) public Instant changedAt;
    @PrePersist void onCreate(){if(id==null)id=UUID.randomUUID().toString();if(changedAt==null)changedAt=Instant.now();}
}
