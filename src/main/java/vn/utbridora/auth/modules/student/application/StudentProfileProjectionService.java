package vn.utbridora.auth.modules.student.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.files.FileReadPort;
import vn.utbridora.auth.contracts.files.FileReadPort.FileDto;
import vn.utbridora.auth.contracts.files.FileReadPort.ResourceRef;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.modules.student.port.StudentProfileProjectionPort;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class StudentProfileProjectionService implements StudentProfileProjectionPort {
    private final StudentProfileRepository profiles;
    private final JdbcTemplate jdbc;
    private final ProfileAccessPort access;
    private final AuditService audit;
    private final org.springframework.beans.factory.ObjectProvider<FileReadPort> files;
    public StudentProfileProjectionService(StudentProfileRepository profiles,JdbcTemplate jdbc,ProfileAccessPort access,AuditService audit,
        org.springframework.beans.factory.ObjectProvider<FileReadPort> files) {
        this.profiles=profiles;this.jdbc=jdbc;this.access=access;this.audit=audit;this.files=files;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public String schoolOf(ActorContext context,String student,String term) {
        actor(context.authentication());
        return jdbc.queryForList("SELECT school_org_id FROM student_term_eligibilities WHERE user_id=? AND term_id=? FOR SHARE",String.class,uuid(student),uuid(term))
            .stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public Projection readAllowed(ActorContext context,ProfileAccessPort.Query query) {
        // Callers never supply an allow flag or a reusable decision from the browser.
        var decision=access.decide(context,query);
        if(!decision.allowed() || decision.allowedFields().isEmpty() || !query.requestedFields().containsAll(decision.allowedFields()))
            throw failure(403,"ACCESS_DENIED");
        var allowed=decision.allowedFields();
        String school=schoolOf(context,query.studentUserId(),query.termId());
        var profile=profiles.lockByUser(query.studentUserId()).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        var term=profiles.findTerm(profile.id(),query.termId()).orElse(null);
        var skills=new ArrayList<Skill>();
        if(allowed.contains("skills")) {
            profiles.skills(profile.id()).forEach(s->skills.add(new CanonicalSkill(s)));
            profiles.customSkills(profile.id()).forEach(s->skills.add(new CustomSkill(s)));
        }
        var projects=allowed.contains("projects")?profiles.projects(profile.id()):List.<vn.utbridora.auth.modules.student.api.StudentProfileDtos.Project>of();
        var certificates=allowed.contains("certificates")?profiles.certificates(profile.id()):List.<vn.utbridora.auth.modules.student.api.StudentProfileDtos.Certificate>of();
        var resource=new ResourceRef("STUDENT_PROFILE",query.studentUserId());
        FileDto cv=allowed.contains("cv") && profile.cvFileId()!=null?cleanFile(profile.cvFileId(),resource,"CV",context):null;
        var evidence=new ArrayList<FileDto>();
        if(allowed.contains("evidence")) for(String id:profiles.projects(profile.id()).stream().flatMap(p->p.evidenceFileIds().stream()).distinct().sorted().toList())
            evidence.add(cleanFile(id,resource,"EVIDENCE",context));
        String displayName=allowed.contains("displayName")?jdbc.queryForObject("SELECT display_name FROM users WHERE id=?",String.class,query.studentUserId()):null;
        String email=allowed.contains("email")?jdbc.queryForObject("SELECT email FROM users WHERE id=?",String.class,query.studentUserId()):null;
        String studentCode=allowed.contains("studentCode")?jdbc.queryForObject("SELECT student_code FROM student_rosters WHERE user_id=? AND school_org_id=?",String.class,query.studentUserId(),school):null;
        var data=new Data(allowed,displayName,studentCode,term==null?null:term.majorCode(),List.copyOf(skills),projects,certificates,cv,List.copyOf(evidence),email,allowed.contains("phone")?profile.phone():null);
        var metadata=new HashMap<String,Object>();metadata.put("accessPurpose",query.purpose());metadata.put("fieldCodes",allowed);
        if(decision.consentVersion()!=null) metadata.put("consentVersion",decision.consentVersion());
        if(decision.shareVersion()!=null) metadata.put("shareVersion",decision.shareVersion());
        audit.workflow(context.actorId(),"STUDENT_PROFILE_ACCESSED",profile.id(),school,metadata);
        return new Projection(query.studentUserId(),profile.version(),allowed,decision.validUntil(),data);
    }
    private FileDto cleanFile(String id,ResourceRef resource,String purpose,ActorContext actor) {
        var provider=files.getIfAvailable();if(provider==null) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        var file=provider.requireCleanBound(id,resource,purpose,actor);
        if(file==null || !id.equals(file.fileId()) || !resource.equals(file.resource()) || !purpose.equals(file.purpose())
            || !"CLEAN".equals(file.scanStatus()) || file.deletedAt()!=null) throw failure(403,"ACCESS_DENIED");
        return file;
    }
}
