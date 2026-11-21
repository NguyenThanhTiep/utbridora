package vn.utbridora.auth.modules.student.application;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.onboarding.AccountProvisioningPort;
import vn.utbridora.auth.contracts.onboarding.AccountProvisioningPort.RosterActivationProof;
import vn.utbridora.auth.modules.student.port.StudentRosterProvisionPort;
import vn.utbridora.auth.student.port.TermReferencePort;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class StudentRosterProvisionService implements StudentRosterProvisionPort {
    private final JdbcTemplate jdbc;
    private final TermReferencePort terms;
    private final AcademicCatalogService catalogs;
    private final AccountProvisioningPort accounts;
    private final SchoolDelegationAccess access;
    public StudentRosterProvisionService(JdbcTemplate jdbc,TermReferencePort terms,AcademicCatalogService catalogs,
        AccountProvisioningPort accounts,SchoolDelegationAccess access) {
        this.jdbc=jdbc;this.terms=terms;this.catalogs=catalogs;this.accounts=accounts;this.access=access;
    }
    private record Roster(String id,String school,String code,String user,String name,String email,long version) {}
    private Optional<Roster> roster(String school,String code) {
        return jdbc.query("SELECT id,school_org_id,student_code,user_id,full_name,email,version FROM student_rosters WHERE school_org_id=? AND student_code=? FOR UPDATE",(r,n)->new Roster(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7)),school,code).stream().findFirst();
    }
    @Override public RosterProvisionResult inspectRoster(ActorContext actor,EnsureRoster in) {
        access.require(actor.authentication(),uuid(in.schoolOrgId()),uuid(in.termId()),"schoolRosterImport:manage");
        var account=accounts.inspectForOnboarding(actor,in.emailCanonical(),in.schoolOrgId(),in.termId());
        var r=roster(in.schoolOrgId(),in.studentCode()).orElse(null);
        if(r!=null && (!r.email().equals(in.emailCanonical()) || !r.name().equals(in.fullName()))) return review(r,"ROSTER_METADATA_CONFLICT");
        if(account.userId()!=null) {
            if(!Set.of("ACTIVE","PENDING_ACTIVATION").contains(account.status())) return review(r,"ACCOUNT_BLOCKED");
            if(r==null || !account.userId().equals(r.user())) return review(r,"IDENTITY_CONFLICT");
            if("ACTIVE".equals(account.status()) && !account.hasRequiredMembership()) return review(r,"MEMBERSHIP_REVOKED");
        } else if(r!=null && r.user()!=null) return review(r,"IDENTITY_CONFLICT");
        if(account.userId()==null && !jdbc.queryForList("SELECT id FROM student_rosters WHERE email=? AND (school_org_id<>? OR student_code<>?) FOR UPDATE",String.class,in.emailCanonical(),in.schoolOrgId(),in.studentCode()).isEmpty()) return review(null,"IDENTITY_CONFLICT");
        if(r!=null) {
            var metadata=jdbc.queryForList("SELECT major_code,cohort_code,class_code FROM student_roster_terms WHERE roster_id=? AND term_id=? FOR UPDATE",r.id(),in.termId());
            if(!metadata.isEmpty() && (!Objects.equals(metadata.getFirst().get("major_code"),in.majorCode()) || !Objects.equals(metadata.getFirst().get("cohort_code"),in.cohortCode()) || !Objects.equals(metadata.getFirst().get("class_code"),in.classCode()))) return review(r,"ROSTER_METADATA_CONFLICT");
        }
        return new RosterProvisionResult(r==null?null:r.id(),r==null?0:r.version(),"ACTIVE".equals(account.status())?"ALREADY_LINKED":r==null?"CREATED_PENDING":"REUSED_PENDING",r==null?null:r.user(),"OK");
    }
    @Override public RosterProvisionResult ensureRoster(ActorContext actor,EnsureRoster in) {
        uuid(in.schoolOrgId());uuid(in.termId());uuid(in.operationId());
        if(in.studentCode()==null || !in.studentCode().equals(in.studentCode().trim()) || in.studentCode().isEmpty() || in.studentCode().codePointCount(0,in.studentCode().length())>32
            || in.emailCanonical()==null || !in.emailCanonical().equals(in.emailCanonical().trim().toLowerCase(Locale.ROOT))
            || !in.emailCanonical().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || in.emailCanonical().length()>254
            || in.fullName()==null || in.fullName().codePointCount(0,in.fullName().length())<2 || in.fullName().codePointCount(0,in.fullName().length())>150) throw failure(422,"PERMANENT_VALIDATION");
        access.require(actor.authentication(),in.schoolOrgId(),in.termId(),"schoolRosterImport:manage");
        var term=terms.findById(in.termId()).orElseThrow(()->failure(422,"REFERENCE_DATA_CHANGED"));
        if(!in.schoolOrgId().equals(term.schoolOrgId()) || term.status()!=TermReferencePort.Status.ACTIVE) throw failure(422,"REFERENCE_DATA_CHANGED");
        var catalog=catalogs.forProfile(in.schoolOrgId(),in.termId());
        if(!catalog.version().equals(in.expectedCatalogVersion()) || catalog.entries().get(AcademicCatalogService.Kind.MAJOR).stream().noneMatch(e->e.code().equals(in.majorCode()) && e.status().equals("ACTIVE"))) throw failure(422,"REFERENCE_DATA_CHANGED");
        var inspection=inspectRoster(actor,in);
        if(inspection.status().equals("NEEDS_REVIEW")) return inspection;
        // T1 owns identity/membership decisions; T2 only consumes this trusted reference.
        var account=accounts.inspectForOnboarding(actor,in.emailCanonical(),in.schoolOrgId(),in.termId());
        var existing=roster(in.schoolOrgId(),in.studentCode()).orElse(null);
        if(existing!=null && (!existing.email().equals(in.emailCanonical()) || !existing.name().equals(in.fullName()))) return review(existing,"ROSTER_METADATA_CONFLICT");
        if(account.userId()!=null) {
            if(!Set.of("ACTIVE","PENDING_ACTIVATION").contains(account.status())) return review(existing,"ACCOUNT_BLOCKED");
            if(existing==null || !account.userId().equals(existing.user())) return review(existing,"IDENTITY_CONFLICT");
            if("ACTIVE".equals(account.status()) && !account.hasRequiredMembership()) return review(existing,"MEMBERSHIP_REVOKED");
        } else if(existing!=null && existing.user()!=null) return review(existing,"IDENTITY_CONFLICT");
        boolean created=existing==null;
        if(created) {
            String id=UUID.randomUUID().toString();
            jdbc.update("INSERT INTO student_rosters(id,school_org_id,student_code,full_name,email,major_code,term_code,created_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",id,in.schoolOrgId(),in.studentCode(),in.fullName(),in.emailCanonical(),in.majorCode(),term.termCode());
            existing=roster(in.schoolOrgId(),in.studentCode()).orElseThrow();
        }
        var metadata=jdbc.queryForList("SELECT major_code,cohort_code,class_code FROM student_roster_terms WHERE roster_id=? AND term_id=? FOR UPDATE",existing.id(),in.termId());
        if(!metadata.isEmpty() && (!Objects.equals(metadata.getFirst().get("major_code"),in.majorCode()) || !Objects.equals(metadata.getFirst().get("cohort_code"),in.cohortCode()) || !Objects.equals(metadata.getFirst().get("class_code"),in.classCode()))) return review(existing,"ROSTER_METADATA_CONFLICT");
        if(metadata.isEmpty()) {
            jdbc.update("INSERT INTO student_roster_terms(roster_id,term_id,major_code,catalog_version,cohort_code,class_code) VALUES(?,?,?,?,?,?)",existing.id(),in.termId(),in.majorCode(),in.expectedCatalogVersion(),in.cohortCode(),in.classCode());
            if(!created) jdbc.update("UPDATE student_rosters SET version=version+1 WHERE id=?",existing.id());
        }
        existing=roster(in.schoolOrgId(),in.studentCode()).orElseThrow();
        boolean active="ACTIVE".equals(account.status());
        if(active) materialize(existing,account.userId());
        return new RosterProvisionResult(existing.id(),existing.version(),active?"ALREADY_LINKED":created?"CREATED_PENDING":"REUSED_PENDING",existing.user(),"OK");
    }
    private RosterProvisionResult review(Roster r,String reason) {return new RosterProvisionResult(r==null?null:r.id(),r==null?0:r.version(),"NEEDS_REVIEW",null,reason);}
    @Override public RosterForActivation readForActivation(RosterActivationProof proof,String id) {
        String email=accounts.requireRosterActivation(proof,id);
        var rows=jdbc.query("SELECT id,school_org_id,student_code,user_id,full_name,email,version FROM student_rosters WHERE id=? FOR UPDATE",(r,n)->new Roster(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7)),uuid(id));
        if(rows.size()!=1 || !rows.getFirst().email().equals(email)) throw failure(409,"ACTIVATION_NOT_PENDING");
        var r=rows.getFirst();return new RosterForActivation(r.id(),r.version(),r.school(),r.code(),r.email(),r.user(),termIds(r.id()));
    }
    @Override public BindResult bindActivatedUser(RosterActivationProof proof,BindActivatedUser in) {
        var ref=readForActivation(proof,in.rosterId());uuid(in.userId());uuid(in.operationId());
        if(ref.rosterVersion()!=in.expectedRosterVersion() || ref.userId()!=null && !ref.userId().equals(in.userId())) throw failure(409,"STATE_CONFLICT");
        // Only the T1-validated identity matching this roster email can be bound.
        accounts.requireActivatedRosterIdentity(proof,ref.rosterId(),in.userId(),ref.schoolOrgId());
        if(ref.userId()==null) jdbc.update("UPDATE student_rosters SET user_id=?,version=version+1 WHERE id=?",in.userId(),ref.rosterId());
        var r=roster(ref.schoolOrgId(),ref.studentCode()).orElseThrow();boolean created=materialize(r,in.userId());
        return new BindResult(r.id(),in.userId(),r.version(),termIds(r.id()),created);
    }
    private List<String> termIds(String roster) {return jdbc.queryForList("SELECT term_id FROM student_roster_terms WHERE roster_id=? ORDER BY term_id",String.class,roster);}
    private boolean materialize(Roster roster,String user) {
        var profiles=jdbc.queryForList("SELECT id FROM student_profiles WHERE user_id=? FOR UPDATE",String.class,user);boolean created=profiles.isEmpty();
        String profile=created?UUID.randomUUID().toString():profiles.getFirst();
        if(created) jdbc.update("INSERT INTO student_profiles(id,user_id,skills_declared,profile_version,created_at,updated_at) VALUES(?,?,false,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",profile,user);
        for(String term:termIds(roster.id())) {
            var source=jdbc.queryForMap("SELECT major_code FROM student_roster_terms WHERE roster_id=? AND term_id=? FOR SHARE",roster.id(),term);
            var reference=terms.findById(term).orElseThrow(()->failure(422,"REFERENCE_DATA_CHANGED"));
            if(!roster.school().equals(reference.schoolOrgId())) throw failure(409,"STATE_CONFLICT");
            boolean newEligibility=jdbc.queryForObject("SELECT COUNT(*) FROM student_term_eligibilities WHERE user_id=? AND term_id=?",Integer.class,user,term)==0;
            boolean newProfileTerm=jdbc.queryForObject("SELECT COUNT(*) FROM student_profile_terms WHERE profile_id=? AND term_id=?",Integer.class,profile,term)==0;
            if((newEligibility || newProfileTerm) && (reference.status()!=TermReferencePort.Status.ACTIVE || catalogs.forProfile(roster.school(),term).entries().get(AcademicCatalogService.Kind.MAJOR).stream().noneMatch(e->e.code().equals(source.get("major_code")) && e.status().equals("ACTIVE")))) throw failure(422,"REFERENCE_DATA_CHANGED");
            if(newEligibility)
                jdbc.update("INSERT INTO student_term_eligibilities(id,student_roster_id,term_code,user_id,school_org_id,term_id,school_approved,created_at) VALUES(?,?,?,?,?,?,false,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),roster.id(),reference.termCode(),user,roster.school(),term);
            if(newProfileTerm)
                jdbc.update("INSERT INTO student_profile_terms(profile_id,term_id,school_org_id,major_code,availability_declared,created_at,updated_at) VALUES(?,?,?,?,false,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",profile,term,roster.school(),source.get("major_code"));
        }
        return created;
    }
}
