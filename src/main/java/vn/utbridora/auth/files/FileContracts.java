package vn.utbridora.auth.files;

import java.time.Instant;

/** CP-FILE1 public values. Storage paths and authorization context never enter FileDto. */
public final class FileContracts {
    private FileContracts() {}
    public enum ResourceType { STUDENT_PROFILE, TASK_ASSIGNMENT, WEEKLY_REPORT, EVALUATION, ENTERPRISE_VERIFICATION, IMPORT_JOB, COHORT }
    public enum FilePurpose { CV, EVIDENCE, SUBMISSION, WEEKLY_REPORT, VERIFICATION, ROSTER_IMPORT, PLACEMENT_IMPORT }
    public enum Action { UPLOAD, READ_METADATA, DOWNLOAD }
    public record ResourceRef(ResourceType resourceType,String resourceId) {}
    public record FileDto(String fileId,ResourceRef resource,FilePurpose purpose,String originalName,String contentType,
                          long sizeBytes,String sha256,FileMetadata.ScanStatus scanStatus,Instant createdAt,Instant deletedAt) {}
    public record FileAuthorization(boolean allowed,String reasonCode,Long resourceVersion,Long consentVersion,
                                    Long shareVersion,Instant validUntil) {
        public static FileAuthorization deny() {return new FileAuthorization(false,"RESOURCE_NOT_FOUND",null,null,null,null);}
        public static FileAuthorization owner(long version) {return new FileAuthorization(true,"OWNER_ACCESS",version,null,null,null);}
    }
}
