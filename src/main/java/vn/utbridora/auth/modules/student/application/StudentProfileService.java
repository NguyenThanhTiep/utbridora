package vn.utbridora.auth.modules.student.application;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;
import vn.utbridora.auth.modules.student.domain.StudentProfile;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.organization.MembershipRepository;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

/**
 * BC11 own profile (Contract Pack v1.0 line 778): the owner comes from the principal and the school from an
 * active STUDENT membership in a VERIFIED university; PUT replaces the whole profile under a version check.
 */
@Service
public class StudentProfileService {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final TypeReference<List<SelfSkillView>> SKILLS=new TypeReference<>(){};
    private static final TypeReference<List<CustomSkillView>> CUSTOM_SKILLS=new TypeReference<>(){};
    private static final TypeReference<List<DateRange>> PERIODS=new TypeReference<>(){};
    private static final TypeReference<List<String>> CODES=new TypeReference<>(){};
    private static final TypeReference<List<Project>> PROJECTS=new TypeReference<>(){};
    private static final TypeReference<List<Certificate>> CERTIFICATES=new TypeReference<>(){};
    private final StudentProfileRepository profiles;
    private final MembershipRepository memberships;
    private final AuthorizationPolicy policy;
    private final AuditService audit;

    public StudentProfileService(StudentProfileRepository profiles,MembershipRepository memberships,
                                 AuthorizationPolicy policy,AuditService audit) {
        this.profiles=profiles;this.memberships=memberships;this.policy=policy;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public SelfProfileView read(Authentication auth,String termId) {
        String school=schoolOf(auth,"student_profile:self:read");
        if(!ProfileInputParser.isUuid(termId)) throw new StudentProfileException(400,"INVALID_REQUEST","Mã học kỳ chưa hợp lệ.",
            List.of(new FieldErrorView("termId","INVALID_REQUEST","Phải là UUID.")));
        // BC11 does not define the missing-profile case; 404 per C.0 and DEV02's proposal, pending the leader.
        return profiles.findByStudentUserIdAndTermId(actor(auth),termId.toLowerCase(Locale.ROOT))
            .filter(profile->profile.schoolOrgId.equals(school)).map(this::view)
            .orElseThrow(StudentProfileService::notFound);
    }

    @Transactional
    public SelfProfileView save(Authentication auth,String idempotencyKey,String body) {
        String school=schoolOf(auth,"student_profile:self:write");
        // Interim: the key is required and validated; replay storage (C.0 v1.0 lines 92-94) waits for DEV01's decision.
        if(!ProfileInputParser.isUuid(idempotencyKey)) throw new StudentProfileException(400,"INVALID_REQUEST","Thiếu hoặc sai Idempotency-Key.",
            List.of(new FieldErrorView("Idempotency-Key","INVALID_REQUEST","Phải là UUID.")));
        ProfileInput input=new ProfileInputParser().parse(body);
        String actor=actor(auth);
        StudentProfile current=profiles.lockByStudentAndTerm(actor,input.termId()).orElse(null);
        if(current!=null && !current.schoolOrgId.equals(school)) throw notFound();
        // First save uses expectedProfileVersion 0 (proposal pending the leader); later saves must match exactly.
        long version=current==null?0:current.profileVersion;
        if(input.expectedProfileVersion()!=version) throw StudentProfileException.of(409,"PROFILE_STALE","Hồ sơ đã thay đổi, vui lòng tải lại.");
        SelfProfileData before=current==null?null:view(current).profile();

        // Students only edit self-declared data; SCHOOL_VERIFIED is never accepted from the client (line 778).
        List<SelfSkillView> skills=input.skills().stream()
            .map(s->new SelfSkillView(s.skillCode(),s.level(),s.yearsPractice(),"SELF_DECLARED",s.note())).toList();
        Set<String> known=before==null?Set.of():ids(before.customSkills(),CustomSkillView::customSkillId), seen=new HashSet<>();
        List<CustomSkillView> customSkills=new ArrayList<>();
        for(int i=0;i<input.customSkills().size();i++) {
            CustomSkillInput s=input.customSkills().get(i);
            String id=keepOrAssign(s.customSkillId(),known,seen,"customSkills["+i+"].customSkillId");
            customSkills.add(new CustomSkillView(id,s.name(),s.categoryCode(),s.level(),s.yearsPractice(),s.note()));
        }
        known=before==null?Set.of():ids(before.projects(),Project::projectId); seen=new HashSet<>();
        List<Project> projects=new ArrayList<>();
        for(int i=0;i<input.projects().size();i++) {
            Project p=input.projects().get(i);
            projects.add(new Project(keepOrAssign(p.projectId(),known,seen,"projects["+i+"].projectId"),
                p.title(),p.description(),p.role(),p.skillCodes(),p.url(),p.evidenceFileIds()));
        }
        known=before==null?Set.of():ids(before.certificates(),Certificate::certificateId); seen=new HashSet<>();
        List<Certificate> certificates=new ArrayList<>();
        for(int i=0;i<input.certificates().size();i++) {
            Certificate c=input.certificates().get(i);
            certificates.add(new Certificate(keepOrAssign(c.certificateId(),known,seen,"certificates["+i+"].certificateId"),
                c.title(),c.issuer(),c.issuedOn(),c.expiresOn(),c.fileId()));
        }
        // BC11 v1.0 line 782: a major change must keep every declared record; deletions use a separate same-major request.
        if(before!=null && !Objects.equals(before.majorCode(),input.majorCode())
            && !keepsDeclaredRecords(before,skills,customSkills,projects,certificates,input))
            throw StudentProfileException.of(422,"MAJOR_CHANGE_WOULD_DROP_DATA","Đổi ngành phải giữ lại kỹ năng, dự án, chứng chỉ và minh chứng đã khai.");
        rejectNewFileReferences(before,input,projects,certificates);

        StudentProfile target=current!=null?current:new StudentProfile();
        if(current==null) { target.studentUserId=actor; target.schoolOrgId=school; target.termId=input.termId(); }
        target.majorCode=input.majorCode(); target.phone=input.phone(); target.cvFileId=input.cvFileId();
        target.skillsDeclared=input.skillsDeclared(); target.availabilityDeclared=input.availabilityDeclared();
        target.skills=toJson(skills); target.customSkills=toJson(customSkills); target.availablePeriods=toJson(input.availablePeriods());
        target.orientationCodes=toJson(input.orientationCodes()); target.subjectCodes=toJson(input.subjectCodes());
        target.projects=toJson(projects); target.certificates=toJson(certificates);
        target.profileVersion=version+1;
        StudentProfile saved=profiles.saveAndFlush(target);
        audit.writeScoped(actor,"STUDENT_PROFILE_UPDATED","student_profile",saved.id,"SUCCESS",school,null);
        return view(saved);
    }

    private String schoolOf(Authentication auth,String permission) {
        String actor=actor(auth);
        List<String> schools=actor==null?List.of():memberships.findByUserIdAndActiveTrue(actor).stream()
            .filter(m->m.role==Role.STUDENT && m.organizationId!=null).map(m->m.organizationId).distinct()
            .filter(org->policy.hasOrganization(auth,org,Role.STUDENT,permission)).toList();
        if(schools.isEmpty()) {
            audit.securityDenied(actor,"AUTHORIZATION_DENIED");
            throw StudentProfileException.of(403,"ACCESS_DENIED","Bạn không có quyền với hồ sơ sinh viên.");
        }
        // Interim: one VERIFIED school per student until T5 TermReference can map a termId to its school.
        if(schools.size()>1) throw StudentProfileException.of(422,"STUDENT_TERM_MISMATCH","Tài khoản thuộc nhiều trường; phiên bản này chỉ hỗ trợ một trường.");
        return schools.get(0);
    }
    /** Interim until the T1 file service and T2-09: only file references already in the profile may be kept. */
    private static void rejectNewFileReferences(SelfProfileData before,ProfileInput input,List<Project> projects,List<Certificate> certificates) {
        Set<String> known=before==null?Set.of():fileRefs(before.cvFileId(),before.projects(),before.certificates());
        List<FieldErrorView> errors=new ArrayList<>();
        String message="Chưa hỗ trợ gắn tệp mới; chỉ giữ tệp đã có trong hồ sơ.";
        if(input.cvFileId()!=null && !known.contains(input.cvFileId())) errors.add(new FieldErrorView("cvFileId","BUSINESS_VALIDATION",message));
        for(int i=0;i<projects.size();i++) for(int j=0;j<projects.get(i).evidenceFileIds().size();j++)
            if(!known.contains(projects.get(i).evidenceFileIds().get(j)))
                errors.add(new FieldErrorView("projects["+i+"].evidenceFileIds["+j+"]","BUSINESS_VALIDATION",message));
        for(int i=0;i<certificates.size();i++) {
            String fileId=certificates.get(i).fileId();
            if(fileId!=null && !known.contains(fileId)) errors.add(new FieldErrorView("certificates["+i+"].fileId","BUSINESS_VALIDATION",message));
        }
        if(!errors.isEmpty()) throw new StudentProfileException(422,"BUSINESS_VALIDATION","Chưa thể gắn tệp mới vào hồ sơ.",errors);
    }
    private static boolean keepsDeclaredRecords(SelfProfileData before,List<SelfSkillView> skills,List<CustomSkillView> customSkills,
                                                List<Project> projects,List<Certificate> certificates,ProfileInput input) {
        return ids(skills,SelfSkillView::skillCode).containsAll(ids(before.skills(),SelfSkillView::skillCode))
            && ids(customSkills,CustomSkillView::customSkillId).containsAll(ids(before.customSkills(),CustomSkillView::customSkillId))
            && ids(projects,Project::projectId).containsAll(ids(before.projects(),Project::projectId))
            && ids(certificates,Certificate::certificateId).containsAll(ids(before.certificates(),Certificate::certificateId))
            && fileRefs(input.cvFileId(),projects,certificates).containsAll(fileRefs(before.cvFileId(),before.projects(),before.certificates()))
            && input.orientationCodes().containsAll(before.orientationCodes()) && input.subjectCodes().containsAll(before.subjectCodes());
    }
    private static String keepOrAssign(String id,Set<String> known,Set<String> seen,String field) {
        if(id==null) id=UUID.randomUUID().toString();
        else if(!known.contains(id)) throw notFound(); // BC11 v1.0 line 780: an ID outside this profile is 404.
        if(!seen.add(id)) throw new StudentProfileException(400,"SCHEMA_VALIDATION","Dữ liệu hồ sơ chưa đúng định dạng.",
            List.of(new FieldErrorView(field,"SCHEMA_VALIDATION","Giá trị bị trùng.")));
        return id;
    }
    private static Set<String> fileRefs(String cvFileId,List<Project> projects,List<Certificate> certificates) {
        Set<String> refs=new HashSet<>();
        if(cvFileId!=null) refs.add(cvFileId);
        projects.forEach(p->refs.addAll(p.evidenceFileIds()));
        certificates.stream().map(Certificate::fileId).filter(Objects::nonNull).forEach(refs::add);
        return refs;
    }
    private static <T> Set<String> ids(List<T> items,Function<T,String> id) { return items.stream().map(id).collect(Collectors.toSet()); }
    private SelfProfileView view(StudentProfile p) {
        SelfProfileData data=new SelfProfileData(p.termId,p.majorCode,p.skillsDeclared,fromJson(p.skills,SKILLS),
            fromJson(p.customSkills,CUSTOM_SKILLS),p.availabilityDeclared,fromJson(p.availablePeriods,PERIODS),
            fromJson(p.orientationCodes,CODES),fromJson(p.subjectCodes,CODES),fromJson(p.projects,PROJECTS),
            fromJson(p.certificates,CERTIFICATES),p.cvFileId,p.phone);
        return new SelfProfileView(p.studentUserId,p.schoolOrgId,p.profileVersion,data,InterimCompleteness.evaluate(data));
    }
    private static <T> List<T> fromJson(String json,TypeReference<List<T>> type) { return JSON.readValue(json,type); }
    private static String toJson(Object value) { return JSON.writeValueAsString(value); }
    private static String actor(Authentication auth) { return auth!=null && auth.getPrincipal() instanceof AppPrincipal p ? p.userId() : null; }
    private static StudentProfileException notFound() { return StudentProfileException.of(404,"RESOURCE_NOT_FOUND","Không tìm thấy hồ sơ."); }
}
