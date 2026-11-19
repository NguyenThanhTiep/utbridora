package vn.utbridora.auth.onboarding;

import java.time.Instant;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.operations.OperationFailure;

/** The multipart preview is the only upload entrypoint. Current delegated staff may read staging. */
@Component
public class StudentImportFilePolicy implements FileResourcePolicy {
    private final StudentImportService imports;
    public StudentImportFilePolicy(StudentImportService imports) {this.imports=imports;}
    public ResourceType resourceType() {return ResourceType.IMPORT_JOB;}
    public Grant check(ActorContext actor,ResourceRef ref,FilePurpose purpose,Action action,Instant now) {
        if(purpose!=FilePurpose.ROSTER_IMPORT || action==Action.UPLOAD) return Grant.deny();
        try {var job=imports.authorized(actor.authentication(),ref.resourceId());return Grant.owner(job.version(),job.school(),false);}
        catch(OperationFailure denied) {if(denied.status==403 || denied.status==404) return Grant.deny();throw denied;}
    }
    @Override public boolean mayReadFile(ActorContext actor,String file,String uploader,ResourceRef ref,FilePurpose purpose) {return true;}
}
