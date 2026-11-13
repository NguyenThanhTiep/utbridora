package vn.utbridora.auth.files;

import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;

/** Throws the shared OperationFailure on denial, invalid binding or unavailable content. */
public interface FileReadPort {
    FileDto requireCleanBound(String fileId,ResourceRef resource,FilePurpose purpose,ActorContext actor);
}
