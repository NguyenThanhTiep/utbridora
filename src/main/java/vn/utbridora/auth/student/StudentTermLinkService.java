package vn.utbridora.auth.student;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** T2 owns this relationship query. An unbound legacy row never establishes student-term authority. */
@Service
public class StudentTermLinkService {
    private final JdbcTemplate jdbc;
    public StudentTermLinkService(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Transactional(readOnly=true)
    public boolean linked(String userId,String schoolOrgId,String termId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM student_term_eligibilities e JOIN student_rosters r ON r.id=e.student_roster_id WHERE r.user_id=? AND r.school_org_id=? AND e.user_id=r.user_id AND e.school_org_id=r.school_org_id AND e.term_id=?",
                Integer.class,userId,schoolOrgId,termId)>0;
    }
}
