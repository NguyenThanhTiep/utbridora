package vn.utbridora.auth.modules.student.application;

import java.util.Optional;

/**
 * Fail-closed default until the T5 TermReadPort and the T2 student-term link (feature/student-data) are merged:
 * an unverified term is never accepted, so profile reads and writes answer 503 DEPENDENCY_UNAVAILABLE (C.0).
 */
public final class UnavailableStudentTermScope implements StudentTermScopePort {
    @Override public Optional<String> schoolOfLinkedTerm(String studentUserId,String termId) {
        throw StudentProfileException.of(503,"DEPENDENCY_UNAVAILABLE","Chưa thể xác minh học kỳ của sinh viên. Vui lòng thử lại sau.");
    }
}
