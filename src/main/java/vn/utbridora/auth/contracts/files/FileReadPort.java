package vn.utbridora.auth.contracts.files;

import java.time.Instant;
import vn.utbridora.auth.contracts.ActorContext;

/** CP-FILE1. T1 supplies the provider; no metadata-only or synthetic CLEAN fallback grants access. */
public interface FileReadPort {
    record ResourceRef(String resourceType,String resourceId) {}
    record FileDto(String fileId,ResourceRef resource,String purpose,String originalName,String contentType,
                   long sizeBytes,String sha256,String scanStatus,Instant createdAt,Instant deletedAt) {}
    FileDto requireCleanBound(String fileId,ResourceRef resource,String purpose,ActorContext actor);
}
