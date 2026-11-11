package vn.utbridora.auth.files;

import java.time.Instant;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;

public interface FileResourceAuthorizer {
    FileAuthorization authorize(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant at);
}
