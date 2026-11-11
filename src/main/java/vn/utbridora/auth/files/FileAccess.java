package vn.utbridora.auth.files;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.FileContracts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

@Service
public class FileAccess implements FileResourceAuthorizer {
    private final JdbcTemplate jdbc;
    private final Map<ResourceType,FileResourcePolicy> policies;
    public FileAccess(JdbcTemplate jdbc,List<FileResourcePolicy> providers) {
        this.jdbc=jdbc;
        var map=new EnumMap<ResourceType,FileResourcePolicy>(ResourceType.class);
        for(var policy:providers) if(map.putIfAbsent(policy.resourceType(),policy)!=null)
            throw new IllegalStateException("Duplicate file resource policy");
        policies=Map.copyOf(map);
    }
    @Override @Transactional
    public FileAuthorization authorize(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,Instant ignoredCallerTime) {
        // The server clock is authoritative; callers cannot revive expired permissions with an old timestamp.
        try {return require(actor,resource,purpose,action).authorization();}
        catch(vn.utbridora.auth.operations.OperationFailure denied) {
            if(denied.status==403 || denied.status==404) return FileAuthorization.deny();
            throw denied;
        }
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public FileResourcePolicy.Grant require(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action) {
        if(actor==null || !actor.authentication().isAuthenticated()) throw failure(401,"SESSION_EXPIRED");
        if(jdbc.queryForList("SELECT id FROM users WHERE id=? AND status='ACTIVE' AND security_version=? AND (locked_until IS NULL OR locked_until<=UTC_TIMESTAMP(6)) FOR SHARE",
                String.class,actor.actorId(),actor.currentSecurityVersion()).isEmpty()) throw failure(401,"SESSION_EXPIRED");
        String permission=action==Action.UPLOAD?"file:upload":"file:download";
        if(jdbc.queryForList("SELECT m.id FROM memberships m JOIN role_permissions p ON p.role=m.role WHERE m.user_id=? AND m.active=true AND p.permission_code=? FOR SHARE",
                String.class,actor.actorId(),permission).isEmpty())
            throw action==Action.UPLOAD?failure(403,"ACCESS_DENIED"):failure(404,"RESOURCE_NOT_FOUND");
        var policy=policies.get(resource.resourceType());
        if(policy==null) throw failure(404,"RESOURCE_NOT_FOUND");
        var grant=policy.check(actor,resource,purpose,action,Instant.now());
        if(grant==null || !grant.authorization().allowed() || grant.authorization().resourceVersion()==null
            || grant.authorization().validUntil()!=null && !Instant.now().isBefore(grant.authorization().validUntil())) throw failure(404,"RESOURCE_NOT_FOUND");
        return grant;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public FileResourcePolicy.Grant requireRead(ActorContext actor,ResourceRef resource,FilePurpose purpose,Action action,String fileId,String owner) {
        var grant=require(actor,resource,purpose,action);
        if(!policies.get(resource.resourceType()).mayReadFile(actor,fileId,owner,resource,purpose)) throw failure(404,"RESOURCE_NOT_FOUND");
        return grant;
    }
}
