package vn.utbridora.auth.modules.student.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.modules.student.persistence.StudentProfileRepository;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort;
import vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class StudentProfileSnapshots implements StudentProfileSnapshotPort {
    private final JdbcTemplate jdbc;private final StudentProfileRepository profiles;
    private final SchoolDelegationAccess scope;private final AcademicCatalogService catalogs;
    private final TaxonomyReferencePort taxonomy;private final OperationLedger ledger;
    public StudentProfileSnapshots(JdbcTemplate jdbc,StudentProfileRepository profiles,SchoolDelegationAccess scope,AcademicCatalogService catalogs,TaxonomyReferencePort taxonomy,OperationLedger ledger) {
        this.jdbc=jdbc;this.profiles=profiles;this.scope=scope;this.catalogs=catalogs;this.taxonomy=taxonomy;this.ledger=ledger;
    }
    @Override @Transactional
    public SnapshotSet captureForMatching(Authentication auth,String termId,String academicVersion,String taxonomyVersion,String key) {
        String term=uuid(termId),school=school(term);scope.require(auth,school,term,"matching:run:execute");
        return ledger.execute(new OperationLedger.Command(actor(auth),school,"STUDENT_SNAPSHOT_CAPTURE",key,Map.of("termId",term,"academicVersion",academicVersion,"taxonomyVersion",taxonomyVersion),201),SnapshotSet.class,()->{
            var catalog=catalogs.forProfile(school,term);
            var tax=taxonomy.read((AppPrincipal)auth.getPrincipal(),Set.of(),Set.of());
            if(!catalog.version().equals(academicVersion) || !tax.taxonomyVersion().equals(taxonomyVersion)) throw failure(409,"CATALOG_CHANGED");
            // Include all currently linked students. Do not filter by completeness or approval.
            List<String> users=jdbc.queryForList("SELECT e.user_id FROM student_term_eligibilities e JOIN users u ON u.id=e.user_id JOIN memberships m ON m.user_id=e.user_id AND m.organization_id=e.school_org_id AND m.role='STUDENT' AND m.active=true WHERE e.term_id=? AND e.school_org_id=? AND u.status='ACTIVE' ORDER BY e.user_id FOR SHARE",String.class,term,school);
            String dataset=UUID.randomUUID().toString();Instant now=Instant.now();var snapshots=new ArrayList<ProfileSnapshot>();
            for(String student:users) snapshots.add(current(student,school,term,academicVersion,taxonomyVersion,now));
            String hash=OperationLedger.payloadHash(snapshots.stream().map(p->List.of(p.studentUserId(),p.profileVersion(),p.relevantHash())).toList());
            var result=new SnapshotSet(dataset,school,term,hash,academicVersion,taxonomyVersion,now,snapshots.size());
            jdbc.update("INSERT INTO student_snapshot_sets(id,school_org_id,term_id,data_json) VALUES(?,?,?,?)",dataset,school,term,JSON.writeValueAsString(result));
            for(var p:snapshots) jdbc.update("INSERT INTO student_profile_snapshots(id,dataset_id,user_id,data_json) VALUES(?,?,?,?)",p.profileSnapshotId(),dataset,p.studentUserId(),JSON.writeValueAsString(p));
            return result;
        },SnapshotSet::datasetSnapshotId);
    }
    @Override @Transactional public SnapshotPage readPage(Authentication auth,String datasetId,String cursor,int size) {
        uuid(datasetId);if(size<1 || size>100) throw failure(400,"INVALID_PAGINATION");
        var set=set(datasetId);scope.require(auth,set.schoolOrgId(),set.termId(),"matching:run:execute");
        if(cursor!=null) uuid(cursor);
        var rows=jdbc.query("SELECT data_json FROM student_profile_snapshots WHERE dataset_id=? AND (? IS NULL OR user_id>?) ORDER BY user_id LIMIT ?",
            (r,n)->JSON.readValue(r.getString(1),ProfileSnapshot.class),datasetId,cursor,cursor,size+1);
        boolean more=rows.size()>size;var items=rows.stream().limit(size).toList();
        return new SnapshotPage(datasetId,set.profileSnapshotVersion(),items,more?items.getLast().studentUserId():null);
    }
    @Override @Transactional public ProfileSnapshot readSnapshot(Authentication auth,String id) {
        var snapshot=jdbc.query("SELECT data_json FROM student_profile_snapshots WHERE id=?",(r,n)->JSON.readValue(r.getString(1),ProfileSnapshot.class),uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        scope.require(auth,snapshot.schoolOrgId(),snapshot.termId(),"matching:school:detail_read");return snapshot;
    }
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public void assertCurrentForDecision(Authentication auth,List<DecisionProfileRef> refs) {
        if(refs==null || refs.isEmpty() || refs.size()>5000 || refs.stream().map(DecisionProfileRef::studentUserId).distinct().count()!=refs.size()) throw failure(400,"SCHEMA_VALIDATION");
        for(var ref:refs.stream().sorted(Comparator.comparing(DecisionProfileRef::studentUserId)).toList()) {
            var snapshot=jdbc.query("SELECT data_json FROM student_profile_snapshots WHERE id=?",(r,n)->JSON.readValue(r.getString(1),ProfileSnapshot.class),uuid(ref.profileSnapshotId())).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
            scope.require(auth,snapshot.schoolOrgId(),snapshot.termId(),"matching:school:nominate");
            if(!snapshot.studentUserId().equals(ref.studentUserId()) || snapshot.profileVersion()!=ref.profileVersion() || !snapshot.relevantHash().equals(ref.relevantHash())) throw failure(409,"PROFILE_STALE");
            if(!current(ref.studentUserId(),snapshot.schoolOrgId(),snapshot.termId(),snapshot.academicCatalogVersion(),snapshot.taxonomyVersion(),Instant.now()).relevantHash().equals(ref.relevantHash())) throw failure(409,"PROFILE_STALE");
        }
    }
    public SnapshotSet set(String id) {
        return jdbc.query("SELECT data_json FROM student_snapshot_sets WHERE id=?",(r,n)->JSON.readValue(r.getString(1),SnapshotSet.class),id).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    private ProfileSnapshot current(String student,String school,String term,String academic,String tax,Instant now) {
        var row=profiles.lockByUser(student).orElse(null);
        String major=null;boolean declared=false,availability=false;long version=0;java.math.BigDecimal gpa=null;
        List<SkillValue> skills=List.of();List<vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange> periods=List.of();
        if(row!=null) {
            version=row.version();declared=row.skillsDeclared();gpa=row.gpa();var per=profiles.findTerm(row.id(),term).orElse(null);
            if(per!=null) {major=per.majorCode();availability=per.availabilityDeclared();}
            skills=profiles.skills(row.id()).stream().map(s->new SkillValue(s.skillCode(),s.level(),s.yearsPractice().stripTrailingZeros(),s.source())).sorted(Comparator.comparing(SkillValue::skillCode)).toList();
            periods=profiles.periods(row.id(),term).stream().sorted(Comparator.comparing(v->v.startDate()+v.endDate())).toList();
        }
        // liên quan đến việc thêm lọc svien theo gpa là MVP: GPA nằm trong fingerprint để thay đổi GPA làm kết quả matching stale.
        var relevant=new TreeMap<String,Object>();relevant.put("majorCode",major);relevant.put("termId",term);relevant.put("gpa",gpa);relevant.put("skillsDeclared",declared);relevant.put("skills",skills);relevant.put("availabilityDeclared",availability);relevant.put("availablePeriods",periods);
        return new ProfileSnapshot(UUID.randomUUID().toString(),student,school,term,major,declared,skills,availability,periods,version,OperationLedger.payloadHash(relevant),"demo-v1",academic,tax,now,gpa);
    }
    private String school(String term) {return jdbc.queryForList("SELECT school_org_id FROM terms WHERE id=?",String.class,term).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));}
}
