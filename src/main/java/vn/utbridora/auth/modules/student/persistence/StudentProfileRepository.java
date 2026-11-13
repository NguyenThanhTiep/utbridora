package vn.utbridora.auth.modules.student.persistence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/**
 * Relational storage of the BC11 profile aggregate (Contract Pack v1.1 K.2 lines 1845-1849): one row per student,
 * per-term rows for major, availability and academic selections, ordered child rows for every collection.
 * Writers replace collections inside the caller's transaction after locking the profile row.
 */
@Repository
public class StudentProfileRepository {
    public record ProfileRow(String id,String userId,String phone,String cvFileId,boolean skillsDeclared,long version) {}
    public record TermRow(String schoolOrgId,String majorCode,boolean availabilityDeclared) {}
    private static final String PROFILE="SELECT id,user_id,phone,cv_file_id,skills_declared,profile_version FROM student_profiles WHERE user_id=?";
    private final JdbcTemplate jdbc;
    public StudentProfileRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public Optional<ProfileRow> findByUser(String userId) { return profile(PROFILE,userId); }
    /** Serializes writers of one profile so the version check and the update commit together (G line 1667). */
    public Optional<ProfileRow> lockByUser(String userId) { return profile(PROFILE+" FOR UPDATE",userId); }
    private Optional<ProfileRow> profile(String sql,String userId) {
        return jdbc.query(sql,(rs,i)->new ProfileRow(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),rs.getLong(6)),userId)
            .stream().findFirst();
    }
    public Optional<TermRow> findTerm(String profileId,String termId) {
        return jdbc.query("SELECT school_org_id,major_code,availability_declared FROM student_profile_terms WHERE profile_id=? AND term_id=?"+currentRead(),
            (rs,i)->new TermRow(rs.getString(1),rs.getString(2),rs.getBoolean(3)),profileId,termId).stream().findFirst();
    }
    public List<SelfSkillView> skills(String profileId) {
        // Parse text directly: a driver's generic numeric conversion may go
        // through floating point for a LONGTEXT column.
        return jdbc.query("SELECT skill_code,level,years_practice,source,note FROM student_profile_skills WHERE profile_id=? ORDER BY ordinal"+currentRead(),
            (rs,i)->new SelfSkillView(rs.getString(1),rs.getInt(2),plain(new BigDecimal(rs.getString(3))),rs.getString(4),rs.getString(5)),profileId);
    }
    public List<CustomSkillView> customSkills(String profileId) {
        return jdbc.query("SELECT id,name,category_code,level,years_practice,note FROM student_custom_skills WHERE profile_id=? ORDER BY ordinal",
            (rs,i)->new CustomSkillView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),plain(new BigDecimal(rs.getString(5))),rs.getString(6)),profileId);
    }
    public List<Project> projects(String profileId) {
        List<Project> rows=jdbc.query("SELECT id,title,description,role,url FROM student_projects WHERE profile_id=? ORDER BY ordinal",
            (rs,i)->new Project(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),List.of(),rs.getString(5),List.of()),profileId);
        return rows.stream().map(p->new Project(p.projectId(),p.title(),p.description(),p.role(),
            jdbc.queryForList("SELECT skill_code FROM student_project_skills WHERE project_id=? ORDER BY ordinal",String.class,p.projectId()),p.url(),
            jdbc.queryForList("SELECT file_id FROM student_project_files WHERE project_id=? ORDER BY ordinal",String.class,p.projectId()))).toList();
    }
    public List<Certificate> certificates(String profileId) {
        return jdbc.query("SELECT id,title,issuer,issued_on,expires_on,file_id FROM student_certificates WHERE profile_id=? ORDER BY ordinal",
            (rs,i)->new Certificate(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),profileId);
    }
    public List<DateRange> periods(String profileId,String termId) {
        return jdbc.query("SELECT start_date,end_date FROM student_profile_term_periods WHERE profile_id=? AND term_id=? ORDER BY ordinal"+currentRead(),
            (rs,i)->new DateRange(rs.getString(1),rs.getString(2)),profileId,termId);
    }
    public List<String> codes(String profileId,String termId,String kind) {
        return jdbc.queryForList("SELECT code FROM student_profile_term_codes WHERE profile_id=? AND term_id=? AND kind=? ORDER BY ordinal",
            String.class,profileId,termId,kind);
    }

    public void insert(ProfileRow p) {
        jdbc.update("INSERT INTO student_profiles (id,user_id,phone,cv_file_id,skills_declared,profile_version,created_at,updated_at) "
            +"VALUES (?,?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",p.id(),p.userId(),p.phone(),p.cvFileId(),p.skillsDeclared(),p.version());
    }
    /** G line 1667: a wrong affected-row count rolls back instead of overwriting a newer version. */
    public void update(ProfileRow p,long expectedVersion) {
        int rows=jdbc.update("UPDATE student_profiles SET phone=?,cv_file_id=?,skills_declared=?,profile_version=?,updated_at=UTC_TIMESTAMP(6) "
            +"WHERE id=? AND profile_version=?",p.phone(),p.cvFileId(),p.skillsDeclared(),p.version(),p.id(),expectedVersion);
        if(rows!=1) throw new OptimisticLockingFailureException("Student profile version changed");
    }
    public void saveTerm(String profileId,String termId,TermRow term) {
        jdbc.update("INSERT INTO student_profile_terms (profile_id,term_id,school_org_id,major_code,availability_declared,created_at,updated_at) "
            +"VALUES (?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)) AS incoming ON DUPLICATE KEY UPDATE "
            +"major_code=incoming.major_code,availability_declared=incoming.availability_declared,updated_at=incoming.updated_at",
            profileId,termId,term.schoolOrgId(),term.majorCode(),term.availabilityDeclared());
    }
    public void replaceSkills(String profileId,List<SelfSkillView> skills) {
        jdbc.update("DELETE FROM student_profile_skills WHERE profile_id=?",profileId);
        for(int i=0;i<skills.size();i++) {
            SelfSkillView s=skills.get(i);
            jdbc.update("INSERT INTO student_profile_skills (profile_id,skill_code,ordinal,level,years_practice,source,note) VALUES (?,?,?,?,?,?,?)",
                profileId,s.skillCode(),i,s.level(),s.yearsPractice().toString(),s.source(),s.note());
        }
    }
    public void replaceCustomSkills(String profileId,List<CustomSkillView> skills) {
        jdbc.update("DELETE FROM student_custom_skills WHERE profile_id=?",profileId);
        for(int i=0;i<skills.size();i++) {
            CustomSkillView s=skills.get(i);
            jdbc.update("INSERT INTO student_custom_skills (id,profile_id,ordinal,name,category_code,level,years_practice,note) VALUES (?,?,?,?,?,?,?,?)",
                s.customSkillId(),profileId,i,s.name(),s.categoryCode(),s.level(),s.yearsPractice().toString(),s.note());
        }
    }
    public void replaceProjects(String profileId,List<Project> projects) {
        jdbc.update("DELETE f FROM student_project_files f JOIN student_projects p ON p.id=f.project_id WHERE p.profile_id=?",profileId);
        jdbc.update("DELETE s FROM student_project_skills s JOIN student_projects p ON p.id=s.project_id WHERE p.profile_id=?",profileId);
        jdbc.update("DELETE FROM student_projects WHERE profile_id=?",profileId);
        for(int i=0;i<projects.size();i++) {
            Project p=projects.get(i);
            jdbc.update("INSERT INTO student_projects (id,profile_id,ordinal,title,description,role,url) VALUES (?,?,?,?,?,?,?)",
                p.projectId(),profileId,i,p.title(),p.description(),p.role(),p.url());
            for(int j=0;j<p.skillCodes().size();j++)
                jdbc.update("INSERT INTO student_project_skills (project_id,ordinal,skill_code) VALUES (?,?,?)",p.projectId(),j,p.skillCodes().get(j));
            for(int j=0;j<p.evidenceFileIds().size();j++)
                jdbc.update("INSERT INTO student_project_files (project_id,ordinal,file_id) VALUES (?,?,?)",p.projectId(),j,p.evidenceFileIds().get(j));
        }
    }
    public void replaceCertificates(String profileId,List<Certificate> certificates) {
        jdbc.update("DELETE FROM student_certificates WHERE profile_id=?",profileId);
        for(int i=0;i<certificates.size();i++) {
            Certificate c=certificates.get(i);
            jdbc.update("INSERT INTO student_certificates (id,profile_id,ordinal,title,issuer,issued_on,expires_on,file_id) VALUES (?,?,?,?,?,?,?,?)",
                c.certificateId(),profileId,i,c.title(),c.issuer(),c.issuedOn(),c.expiresOn(),c.fileId());
        }
    }
    public void replacePeriods(String profileId,String termId,List<DateRange> periods) {
        jdbc.update("DELETE FROM student_profile_term_periods WHERE profile_id=? AND term_id=?",profileId,termId);
        for(int i=0;i<periods.size();i++)
            jdbc.update("INSERT INTO student_profile_term_periods (profile_id,term_id,ordinal,start_date,end_date) VALUES (?,?,?,?,?)",
                profileId,termId,i,periods.get(i).startDate(),periods.get(i).endDate());
    }
    public void replaceCodes(String profileId,String termId,String kind,List<String> codes) {
        jdbc.update("DELETE FROM student_profile_term_codes WHERE profile_id=? AND term_id=? AND kind=?",profileId,termId,kind);
        for(int i=0;i<codes.size();i++)
            jdbc.update("INSERT INTO student_profile_term_codes (profile_id,term_id,kind,ordinal,code) VALUES (?,?,?,?,?)",profileId,termId,kind,i,codes.get(i));
    }
    /** Return plain decimals, including the exact text storage introduced in V34. */
    private static String currentRead() {
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()?" FOR SHARE":"";
    }
    private static BigDecimal plain(BigDecimal value) {
        BigDecimal stripped=value.stripTrailingZeros();
        return stripped.scale()<0?stripped.setScale(0):stripped;
    }
}
