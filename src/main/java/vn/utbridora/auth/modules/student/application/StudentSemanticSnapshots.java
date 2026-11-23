package vn.utbridora.auth.modules.student.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.modules.student.port.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.DecisionProfileRef;
import vn.utbridora.auth.modules.student.port.StudentProfileProjectionPort.*;
import vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
public class StudentSemanticSnapshots implements StudentSemanticSnapshotPort {
    private final JdbcTemplate jdbc;private final StudentProfileSnapshotPort strict;private final StudentProfileProjectionPort projection;
    private final StudentProfileRepository profiles;private final AdvisoryAuthority authority;private final AcademicCatalogService catalogs;private final TaxonomyReferencePort taxonomy;
    public StudentSemanticSnapshots(JdbcTemplate jdbc,StudentProfileSnapshotPort strict,StudentProfileProjectionPort projection,StudentProfileRepository profiles,AdvisoryAuthority authority,AcademicCatalogService catalogs,TaxonomyReferencePort taxonomy) {
        this.jdbc=jdbc;this.strict=strict;this.projection=projection;this.profiles=profiles;this.authority=authority;this.catalogs=catalogs;this.taxonomy=taxonomy;
    }
    public SetRef capture(Authentication auth,String school,String term,String datasetId,String tax,List<DecisionProfileRef> refs,Instant until) {
        authority.require(auth,school,term,true);
        var dataset=jdbc.query("SELECT data_json FROM student_snapshot_sets WHERE id=?",(r,n)->JSON.readValue(r.getString(1),StudentProfileSnapshotPort.SnapshotSet.class),uuid(datasetId)).stream().findFirst().orElseThrow(StudentSemanticSnapshots::stale);
        if(!dataset.schoolOrgId().equals(school) || !dataset.termId().equals(term) || !dataset.taxonomyVersion().equals(tax)) throw stale();
        String academic=dataset.academicCatalogVersion();
        if(refs.size()>5000 || refs.stream().map(DecisionProfileRef::studentUserId).distinct().count()!=refs.size()) throw failure(409,"AI_ADVISORY_LIMIT_EXCEEDED");
        if(!catalogCurrent(auth,school,term,academic,tax)) throw stale();
        String id=UUID.randomUUID().toString();Instant now=Instant.now();var entries=new ArrayList<Entry>();long capturedBytes=0;
        for(var ref:refs.stream().sorted(Comparator.comparing(DecisionProfileRef::studentUserId)).toList()) {
            var original=strict.readSnapshot(auth,ref.profileSnapshotId());
            if(!original.studentUserId().equals(ref.studentUserId()) || !original.schoolOrgId().equals(school) || !original.termId().equals(term)
                || original.profileVersion()!=ref.profileVersion() || !original.relevantHash().equals(ref.relevantHash())) throw stale();
            var row=profiles.lockByUser(ref.studentUserId()).orElseThrow(StudentSemanticSnapshots::stale);
            if(row.version()!=ref.profileVersion() || !linked(ref.studentUserId(),school,term)) throw stale();
            var value=projection.readAllowed(ActorContext.fromAuthenticatedWorkflow(auth,school),new ProfileAccessPort.Query(ref.studentUserId(),term,null,List.of("skills","projects"),"SCHOOL_AI_ADVISORY"));
            if(value.profileVersion()!=ref.profileVersion() || !value.allowedFields().containsAll(List.of("skills","projects"))) throw failure(404,"RESOURCE_NOT_FOUND");
            var skills=new ArrayList<Skill>();
            for(var s:value.data().skills()) {
                if(s instanceof CanonicalSkill c) {var v=c.skill();skills.add(new Skill("canonical:"+v.skillCode(),"CANONICAL",v.skillCode(),null,v.level(),decimal(v.yearsPractice()),clean(v.note()),v.source()));}
                else if(s instanceof CustomSkill c) {var v=c.skill();skills.add(new Skill("custom:"+v.customSkillId(),"CUSTOM",clean(v.name()),v.categoryCode(),v.level(),decimal(v.yearsPractice()),clean(v.note()),"SELF_DECLARED"));}
            }
            skills.sort(Comparator.comparing(Skill::kind).thenComparing(Skill::codeOrName).thenComparing(Skill::sourceId));
            var projects=value.data().projects().stream().map(v->new Project(v.projectId(),clean(v.title()),clean(v.description()),clean(v.role()),v.skillCodes().stream().sorted().toList(),"SELF_DECLARED")).sorted(Comparator.comparing(Project::sourceId)).toList();
            if(skills.size()>200 || projects.size()>100) throw failure(409,"AI_ADVISORY_LIMIT_EXCEEDED");
            boolean content=projects.stream().anyMatch(p->!p.title().isEmpty() || !p.description().isEmpty() || !p.role().isEmpty()) || skills.stream().anyMatch(s->!s.note().isEmpty() || s.kind().equals("CUSTOM"));
            String hash=OperationLedger.payloadHash(Arrays.asList("student-semantic-v1","SCHOOL_AI_ADVISORY",List.of("skills","projects"),ref.profileVersion(),skills,projects));
            var entry=new Entry(ref.studentUserId(),ref.profileSnapshotId(),ref.profileVersion(),ref.relevantHash(),UUID.randomUUID().toString(),hash,content?"AVAILABLE":"NO_CONTENT",List.copyOf(skills),projects);
            capturedBytes+=JSON.writeValueAsBytes(entry).length;if(capturedBytes>32L*1024*1024) throw failure(409,"AI_ADVISORY_LIMIT_EXCEEDED");entries.add(entry);
        }
        String hash=OperationLedger.payloadHash(entries.stream().map(e->List.of(e.studentUserId(),e.semanticSourceHash())).toList());
        var set=new SetRef(id,hash,List.of("skills","projects"),now,until,entries.size(),school,term,academic,tax);
        jdbc.update("INSERT INTO student_semantic_sets(id,data_json,expires_at) VALUES(?,?,?)",id,JSON.writeValueAsString(set),utc(until));
        for(var e:entries) jdbc.update("INSERT INTO student_semantic_snapshots(id,set_id,student_user_id,data_json) VALUES(?,?,?,?)",e.semanticSnapshotId(),id,e.studentUserId(),JSON.writeValueAsString(e));
        return set;
    }
    public SetRef read(Authentication auth,String id) {var s=set(id);authority.requireEither(auth,s.school(),s.term());return s;}
    public List<Entry> page(Authentication auth,String id,String after,int size) {
        read(auth,id);if(size<1 || size>100) throw failure(400,"INVALID_PAGINATION");
        return jdbc.query("SELECT data_json FROM student_semantic_snapshots WHERE set_id=? AND (? IS NULL OR student_user_id>?) ORDER BY student_user_id LIMIT ?",(r,n)->JSON.readValue(r.getString(1),Entry.class),id,after,after,size);
    }
    public boolean current(Authentication auth,String id) {
        var s=read(auth,id);if(!Instant.now().isBefore(s.validUntil()) || !catalogCurrent(auth,s.school(),s.term(),s.academicVersion(),s.taxonomyVersion())) return false;
        String cursor=null;int count=0;
        do {var rows=page(auth,id,cursor,100);if(rows.isEmpty()) break;
            for(var e:rows) {var p=profiles.lockByUser(e.studentUserId()).orElse(null);if(p==null || p.version()!=e.profileVersion() || !linked(e.studentUserId(),s.school(),s.term())) return false;count++;}
            cursor=rows.getLast().studentUserId();
        } while(true);
        return count==s.count();
    }
    public void purge(String id) {
        var due=jdbc.queryForList("SELECT id FROM student_semantic_sets WHERE id=? AND expires_at<=UTC_TIMESTAMP(6) FOR UPDATE",String.class,id);
        if(due.isEmpty()) return;
        jdbc.update("DELETE FROM student_semantic_snapshots WHERE set_id=?",id);jdbc.update("DELETE FROM student_semantic_sets WHERE id=?",id);
    }
    private SetRef set(String id) {return jdbc.query("SELECT data_json FROM student_semantic_sets WHERE id=?",(r,n)->JSON.readValue(r.getString(1),SetRef.class),uuid(id)).stream().findFirst().orElseThrow(StudentSemanticSnapshots::stale);}
    private boolean linked(String user,String school,String term) {return !jdbc.queryForList("SELECT e.user_id FROM student_term_eligibilities e JOIN users u ON u.id=e.user_id JOIN memberships m ON m.user_id=e.user_id AND m.organization_id=e.school_org_id AND m.role='STUDENT' AND m.active=true WHERE e.user_id=? AND e.school_org_id=? AND e.term_id=? AND u.status='ACTIVE' FOR SHARE",String.class,user,school,term).isEmpty();}
    private boolean catalogCurrent(Authentication auth,String school,String term,String academic,String tax) {return catalogs.forProfile(school,term).version().equals(academic) && taxonomy.read((AppPrincipal)auth.getPrincipal(),Set.of(),Set.of()).taxonomyVersion().equals(tax);}
    private static vn.utbridora.auth.operations.OperationFailure stale() {return failure(409,"AI_ADVISORY_SOURCE_STALE");}
    private static String clean(String s) {return s==null?"":java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFC).replaceAll("(?U)\\s+"," ").strip();}
    private static String decimal(java.math.BigDecimal value) {
        var v=value.stripTrailingZeros();long length=(long)v.precision()+Math.max(0L,-(long)v.scale())+Math.max(0L,(long)v.scale()-v.precision())+2;
        if(length>8192) throw failure(409,"AI_ADVISORY_LIMIT_EXCEEDED");return v.toPlainString();
    }
}
