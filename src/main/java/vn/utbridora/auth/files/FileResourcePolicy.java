package vn.utbridora.auth.files;

import java.time.Instant;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;

/** Owner-module adapter. Missing adapters deny; no ADMIN or filename-based fallback. */
public interface FileResourcePolicy {
    ResourceType resourceType();
    Grant check(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant now);
    record Grant(FileAuthorization authorization,String organizationId,boolean neutralName) {
        public static Grant deny() {return new Grant(FileAuthorization.deny(),null,false);}
        public static Grant owner(long version,String organizationId,boolean neutralName) {
            return new Grant(FileAuthorization.owner(version),organizationId,neutralName);
        }
    }
    /** T1-06 serves owners. Shared readers need an explicit current consent/resource attachment adapter. */
    default boolean mayReadFile(ActorContext actor,String fileId,String uploadedBy,ResourceRef resource,FilePurpose purpose) {
        return actor.actorId().equals(uploadedBy);
    }
}
