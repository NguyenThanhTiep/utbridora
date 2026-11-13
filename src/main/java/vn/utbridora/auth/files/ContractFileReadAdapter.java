package vn.utbridora.auth.files;

import org.springframework.stereotype.Component;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;

/** Adapt the T5 consumer value types to the one T1 file owner. No second storage/authorization service. */
@Component
public class ContractFileReadAdapter implements vn.utbridora.auth.contracts.files.FileReadPort {
    private final FileReadPort owner;
    public ContractFileReadAdapter(FileReadPort owner) {this.owner=owner;}
    @Override public FileDto requireCleanBound(String fileId,ResourceRef resource,String purpose,ActorContext actor) {
        final FileContracts.ResourceRef ref;
        final FilePurpose kind;
        try {ref=new FileContracts.ResourceRef(ResourceType.valueOf(resource.resourceType()),resource.resourceId());kind=FilePurpose.valueOf(purpose);}
        catch(IllegalArgumentException invalid) {throw vn.utbridora.auth.operations.WorkflowValues.failure(400,"SCHEMA_VALIDATION");}
        var f=owner.requireCleanBound(fileId,ref,kind,actor);
        return new FileDto(f.fileId(),new ResourceRef(f.resource().resourceType().name(),f.resource().resourceId()),f.purpose().name(),
            f.originalName(),f.contentType(),f.sizeBytes(),f.sha256(),f.scanStatus().name(),f.createdAt(),f.deletedAt());
    }
}
