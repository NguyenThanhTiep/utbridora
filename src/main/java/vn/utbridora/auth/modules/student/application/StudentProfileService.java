package vn.utbridora.auth.modules.student.application;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository.ProfileRow;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository.TermRow;
import vn.utbridora.auth.organization.MembershipRepository;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;

/**
 * BC11 own profile (Contract Pack v1.0 line 778): the owner comes from the principal, the school from the
 * verified student-term link (StudentTermScopePort) and the STUDENT membership there must be active in a
 * VERIFIED university. One aggregate per student; major, availability and academic selections are per term
 * (K.2 line 1847). PUT replaces the whole profile under a version check.
 */
@Service
public class StudentProfileService {
    private static final String READ="student_profile:self:read", WRITE="student_profile:self:write";
    private final StudentProfileRepository profiles;
    private final MembershipRepository memberships;
    private final AuthorizationPolicy policy;
    private final AuditService audit;
    private final StudentTermScopePort terms;
    private final OperationLedger operations;
    private final ProfileReferenceValidator references;
    private final AcademicCatalogService catalogs;
    private final vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort taxonomy;
    private final vn.utbridora.auth.modules.matching.port.CompletenessPort completeness;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    public StudentProfileService(StudentProfileRepository profiles,MembershipRepository memberships,
                                 AuthorizationPolicy policy,AuditService audit,StudentTermScopePort terms,OperationLedger operations,
                                 ProfileReferenceValidator references,AcademicCatalogService catalogs,
                                 vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort taxonomy,
                                 vn.utbridora.auth.modules.matching.port.CompletenessPort completeness) {
        this.profiles=profiles;this.memberships=memberships;this.policy=policy;this.audit=audit;this.terms=terms;
        this.operations=operations;
        this.references=references;
        this.catalogs=catalogs;this.taxonomy=taxonomy;
        this.completeness=completeness;
    }

    @Transactional(readOnly=true)
    public SelfProfileView read(Authentication auth,String termId) {
        requireStudent(auth,READ);
        if(!ProfileInputParser.isUuid(termId)) throw new StudentProfileException(400,"INVALID_REQUEST","Mã học kỳ chưa hợp lệ.",
            List.of(new FieldErrorView("termId","INVALID_REQUEST","Phải là UUID.")));
        String term=termId.toLowerCase(Locale.ROOT), school=schoolOf(auth,term,READ);
        // 404 only while the student's aggregate does not exist; a new term of an
        // existing profile returns that term's empty part with the current profileVersion.
        ProfileRow profile=profiles.findByUser(actor(auth)).orElseThrow(StudentProfileService::notFound);
        return view(profile,term,school);
    }

    @Transactional
    public SelfProfileView save(Authentication auth,String idempotencyKey,String body) {
        requireStudent(auth,WRITE);
        if(!ProfileInputParser.isUuid(idempotencyKey)) throw new StudentProfileException(400,"INVALID_REQUEST","Thiếu hoặc sai Idempotency-Key.",
            List.of(new FieldErrorView("Idempotency-Key","INVALID_REQUEST","Phải là UUID.")));
        ProfileInput input=new ProfileInputParser().parse(body);
        String actor=actor(auth), school=schoolOf(auth,input.termId(),WRITE);
        return operations.execute(new OperationLedger.Command(actor,school,"STUDENT_PROFILE_PUT",idempotencyKey,input,200),
            SelfProfileView.class,()->saveAuthorized(actor,school,input),
            result->profiles.findByUser(actor).orElseThrow(StudentProfileService::notFound).id());
    }

    private SelfProfileView saveAuthorized(String actor,String school,ProfileInput input) {
        references.validate(school,input);
        ProfileRow current=profiles.lockByUser(actor).orElse(null);
        // First save uses expectedProfileVersion 0; later saves must match exactly.
        long version=current==null?0:current.version();
        if(input.expectedProfileVersion()!=version) throw StudentProfileException.of(409,"PROFILE_STALE","Hồ sơ đã thay đổi, vui lòng tải lại.");
        SelfProfileData before=current==null?null:view(current,input.termId(),school).profile();

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

        ProfileRow next=new ProfileRow(current==null?UUID.randomUUID().toString():current.id(),actor,input.phone(),input.cvFileId(),
            input.skillsDeclared(),version+1);
        if(current==null) profiles.insert(next); else profiles.update(next,version);
        profiles.saveTerm(next.id(),input.termId(),new TermRow(school,input.majorCode(),input.availabilityDeclared()));
        profiles.replaceSkills(next.id(),skills);
        profiles.replaceCustomSkills(next.id(),customSkills);
        profiles.replaceProjects(next.id(),projects);
        profiles.replaceCertificates(next.id(),certificates);
        profiles.replacePeriods(next.id(),input.termId(),input.availablePeriods());
        profiles.replaceCodes(next.id(),input.termId(),"ORIENTATION",input.orientationCodes());
        profiles.replaceCodes(next.id(),input.termId(),"SUBJECT",input.subjectCodes());
        try {
            audit.writeScoped(actor,"STUDENT_PROFILE_UPDATED","student_profile",next.id(),"SUCCESS",school,null);
            entityManager.flush();
        } catch(RuntimeException failure) {
            throw StudentProfileException.of(500,"AUDIT_WRITE_FAILED","Không thể ghi nhật ký; thao tác đã được hủy.");
        }
        return view(next,input.termId(),school);
    }

    /** Coarse gate before the body is read: an active STUDENT membership in some VERIFIED university. */
    private void requireStudent(Authentication auth,String permission) {
        String actor=actor(auth);
        boolean student=actor!=null && memberships.findByUserIdAndActiveTrue(actor).stream()
            .anyMatch(m->m.role==Role.STUDENT && m.organizationId!=null && policy.hasOrganization(auth,m.organizationId,Role.STUDENT,permission));
        if(!student) throw denied(actor);
    }
    /** Review P1-2: the school comes from the verified term link (BC21, K.2 line 1847), never from the first membership. */
    private String schoolOf(Authentication auth,String termId,String permission) {
        String actor=actor(auth);
        String school=terms.schoolOfLinkedTerm(actor,termId).orElseThrow(StudentProfileService::notFound);
        if(!policy.hasOrganization(auth,school,Role.STUDENT,permission)) throw denied(actor);
        return school;
    }
    private StudentProfileException denied(String actor) {
        audit.securityDenied(actor,"AUTHORIZATION_DENIED");
        return StudentProfileException.of(403,"ACCESS_DENIED","Bạn không có quyền với hồ sơ sinh viên.");
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
    /** Shared student data plus the requested term's part; a term without a row yet reads as undeclared. */
    private SelfProfileView view(ProfileRow profile,String termId,String school) {
        TermRow term=profiles.findTerm(profile.id(),termId).orElse(null);
        if(term!=null && !term.schoolOrgId().equals(school)) throw notFound();
        SelfProfileData data=new SelfProfileData(termId,term==null?null:term.majorCode(),profile.skillsDeclared(),
            profiles.skills(profile.id()),profiles.customSkills(profile.id()),term!=null && term.availabilityDeclared(),
            profiles.periods(profile.id(),termId),profiles.codes(profile.id(),termId,"ORIENTATION"),profiles.codes(profile.id(),termId,"SUBJECT"),
            profiles.projects(profile.id()),profiles.certificates(profile.id()),profile.cvFileId(),profile.phone());
        boolean studentTermPresent=terms.schoolOfLinkedTerm(profile.userId(),termId).filter(school::equals).isPresent();
        var catalog=catalogs.forProfile(school,termId);
        boolean majorValid=catalog.entries().get(AcademicCatalogService.Kind.MAJOR).stream().anyMatch(i->i.code().equals(data.majorCode()) && i.status().equals("ACTIVE"));
        var codes=data.skills().stream().map(SelfSkillView::skillCode).collect(Collectors.toSet());
        var subject=(AppPrincipal)policy.currentSubject(profile.userId()).orElseThrow(StudentProfileService::notFound).getPrincipal();
        var refs=taxonomy.read(subject,codes,Set.of());
        boolean skillsValid=refs.activeSkillCodes().containsAll(codes);
        return new SelfProfileView(profile.userId(),school,profile.version(),data,
            completeness.evaluate(data,studentTermPresent,majorValid,skillsValid));
    }
    private static String actor(Authentication auth) { return auth!=null && auth.getPrincipal() instanceof AppPrincipal p ? p.userId() : null; }
    private static StudentProfileException notFound() { return StudentProfileException.of(404,"RESOURCE_NOT_FOUND","Không tìm thấy hồ sơ."); }
}
