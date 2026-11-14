package vn.utbridora.auth.files;

import java.io.*;
import java.nio.channels.SeekableByteChannel;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class FileService implements FileReadPort {
    private final JdbcTemplate jdbc;
    private final FileAccess access;
    private final FileStorage storage;
    private final OperationLedger ledger;
    private final AuditService audit;
    public FileService(JdbcTemplate jdbc,FileAccess access,FileStorage storage,OperationLedger ledger,AuditService audit) {
        this.jdbc=jdbc;this.access=access;this.storage=storage;this.ledger=ledger;this.audit=audit;
    }
    private record Stored(FileDto dto,String owner,String organization,String storageKey) {}
    public record Download(FileDto file,SeekableByteChannel channel,long start,long length,boolean partial) implements AutoCloseable {
        @Override public void close() {PrivateFileStorage.close(channel);}
    }
    @Transactional
    public FileDto upload(Authentication authentication,String key,ResourceRef input,FilePurpose purpose,MultipartFile file) {
        var resource=reference(input);var policy=FileUploadPolicy.forResource(resource,purpose);uuid(key);
        var actor=ActorContext.fromAuthenticatedWorkflow(authentication,null);
        var grant=access.require(actor,resource,purpose,Action.UPLOAD);
        if(file==null || file.isEmpty()) throw failure(422,"BUSINESS_VALIDATION");
        if(file.getSize()>policy.maximumBytes()) throw failure(413,"FILE_TOO_LARGE");
        String name=FileUploadPolicy.name(file.getOriginalFilename()),type=file.getContentType();
        try(var inputStream=file.getInputStream();var staged=storage.stage(inputStream,policy.maximumBytes())) {
            FileUploadPolicy.content(staged,policy,type,name,purpose);
            var payload=Map.of("resource",resource,"purpose",purpose,"originalName",name,"contentType",type,"sizeBytes",staged.size(),"sha256",staged.sha256());
            return ledger.execute(new OperationLedger.Command(actor.actorId(),grant.organizationId(),"FILE_UPLOAD",key,payload,202),FileDto.class,()->{
                String id=UUID.randomUUID().toString(),storageKey=storage.promote(staged);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if(status==STATUS_ROLLED_BACK) storage.delete(storageKey);
                        // UNKNOWN may have committed in MySQL; retain the private object for reconciliation.
                    }
                });
                Instant now=Instant.now().truncatedTo(ChronoUnit.MICROS);var time=LocalDateTime.ofInstant(now,ZoneOffset.UTC);
                jdbc.update("INSERT INTO files(id,owner_user_id,organization_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES (?,?,?,?,?,?,?,?,'PRIVATE','PENDING',?)",
                    id,actor.actorId(),grant.organizationId(),storageKey,name,type,staged.size(),staged.sha256(),time);
                jdbc.update("INSERT INTO file_bindings(id,file_id,binding_kind,purpose,resource_id,created_at) VALUES (?,?,?,?,?,?)",
                    UUID.randomUUID().toString(),id,resource.resourceType().name(),purpose.name(),resource.resourceId(),time);
                audit.workflow(actor.actorId(),"FILE_UPLOADED",id,grant.organizationId(),Map.of("purpose",purpose.name(),"contentType",type,"sizeBytes",staged.size(),"scanStatus","PENDING"));
                return publicDto(new FileDto(id,resource,purpose,name,type,staged.size(),staged.sha256(),FileMetadata.ScanStatus.PENDING,now,null),grant);
            },FileDto::fileId);
        } catch(IOException error) {throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
    }
    @Transactional
    public FileDto metadata(Authentication authentication,String fileId) {
        var actor=ActorContext.fromAuthenticatedWorkflow(authentication,null);var file=stored(fileId);
        var grant=authorized(actor,file,Action.READ_METADATA);return publicDto(file.dto(),grant);
    }
    @Override @Transactional
    public FileDto requireCleanBound(String fileId,ResourceRef expected,FilePurpose purpose,ActorContext actor) {
        var resource=reference(expected);FileUploadPolicy.forResource(resource,purpose);
        var file=stored(fileId);var grant=authorized(actor,file,Action.READ_METADATA);
        if(!file.dto().resource().equals(resource) || file.dto().purpose()!=purpose) throw failure(422,"FILE_BINDING_INVALID");
        requireClean(file);
        try(var channel=storage.open(file.storageKey(),file.dto().sizeBytes(),file.dto().sha256())) {return publicDto(file.dto(),grant);}
        catch(IOException error) {throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
    }
    @Transactional
    public Download download(Authentication authentication,String fileId,String rangeHeader) {
        var actor=ActorContext.fromAuthenticatedWorkflow(authentication,null);var file=stored(fileId);
        var grant=authorized(actor,file,Action.DOWNLOAD);requireClean(file);
        var range=FileByteRange.parse(rangeHeader,file.dto().sizeBytes());
        var channel=storage.open(file.storageKey(),file.dto().sizeBytes(),file.dto().sha256());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {if(status!=STATUS_COMMITTED) PrivateFileStorage.close(channel);}
        });
        try {
            channel.position(range.start());
            var metadata=new HashMap<String,Object>();metadata.put("purpose",file.dto().purpose().name());
            metadata.put("resourceType",file.dto().resource().resourceType().name());metadata.put("resourceId",file.dto().resource().resourceId());metadata.put("rangeRequest",range.partial());
            if(grant.authorization().consentVersion()!=null) metadata.put("consentVersion",grant.authorization().consentVersion());
            if(grant.authorization().shareVersion()!=null) metadata.put("shareVersion",grant.authorization().shareVersion());
            audit.workflow(actor.actorId(),"FILE_DOWNLOADED",file.dto().fileId(),grant.organizationId(),metadata);
            return new Download(publicDto(file.dto(),grant),channel,range.start(),range.length(),range.partial());
        } catch(IOException error) {PrivateFileStorage.close(channel);throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
        catch(RuntimeException error) {PrivateFileStorage.close(channel);throw error;}
    }
    private FileResourcePolicy.Grant authorized(ActorContext actor,Stored file,Action action) {
        var grant=access.requireRead(actor,file.dto().resource(),file.dto().purpose(),action,file.dto().fileId(),file.owner());
        if(file.organization()!=null && !Objects.equals(file.organization(),grant.organizationId())) throw failure(404,"RESOURCE_NOT_FOUND");
        return grant;
    }
    private Stored stored(String fileId) {
        return jdbc.query("SELECT f.*,b.binding_kind,b.resource_id,b.purpose FROM files f JOIN file_bindings b ON b.file_id=f.id WHERE f.id=? AND f.deleted_at IS NULL FOR SHARE",(r,n)->new Stored(
            new FileDto(r.getString("id"),new ResourceRef(ResourceType.valueOf(r.getString("binding_kind")),r.getString("resource_id")),FilePurpose.valueOf(r.getString("purpose")),
                r.getString("original_name"),r.getString("content_type"),r.getLong("size_bytes"),r.getString("sha256"),FileMetadata.ScanStatus.valueOf(r.getString("scan_status")),
                r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),null),r.getString("owner_user_id"),r.getString("organization_id"),r.getString("storage_key")),uuid(fileId))
            .stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private static ResourceRef reference(ResourceRef input) {
        if(input==null || input.resourceType()==null) throw failure(400,"SCHEMA_VALIDATION");
        return new ResourceRef(input.resourceType(),uuid(input.resourceId()));
    }
    private static void requireClean(Stored file) {if(file.dto().scanStatus()!=FileMetadata.ScanStatus.CLEAN) throw failure(409,"FILE_NOT_CLEAN");}
    private static FileDto publicDto(FileDto file,FileResourcePolicy.Grant grant) {
        return new FileDto(file.fileId(),file.resource(),file.purpose(),grant.neutralName()?file.fileId()+"."+FileUploadPolicy.extension(file.contentType()):file.originalName(),
            file.contentType(),file.sizeBytes(),file.sha256(),file.scanStatus(),file.createdAt(),file.deletedAt());
    }
}
