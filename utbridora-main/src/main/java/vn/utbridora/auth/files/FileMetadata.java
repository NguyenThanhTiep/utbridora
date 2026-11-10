package vn.utbridora.auth.files;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Only metadata. Storage adapter and antivirus workflow are deliberately not implemented. */
@Entity @Table(name="files")
public class FileMetadata {
    public enum Classification { PRIVATE, ORGANIZATION, SHARED }
    public enum ScanStatus { PENDING, CLEAN, REJECTED }
    @Id @Column(length=36) public String id;
    @Column(name="owner_user_id",nullable=false,length=36) public String ownerUserId;
    @Column(name="organization_id",length=36) public String organizationId;
    @Column(name="storage_key",nullable=false,length=512) public String storageKey;
    @Column(name="original_name",nullable=false,length=255) public String originalName;
    @Column(name="content_type",nullable=false,length=150) public String contentType;
    @Column(name="size_bytes",nullable=false) public long sizeBytes;
    @Column(nullable=false,length=64) public String sha256;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(nullable=false,length=32) public Classification classification;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Column(name="scan_status",nullable=false,length=32) public ScanStatus scanStatus=ScanStatus.PENDING;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="deleted_at") public Instant deletedAt;
    @PrePersist void onCreate(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();}
}
