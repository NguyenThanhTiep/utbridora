package vn.utbridora.auth.student;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfileEntity, String> {
    Optional<StudentProfileEntity> findByUserId(String userId);
    boolean existsByUserId(String userId);
    boolean existsByStudentCodeAndUniversityId(String studentCode, String universityId);
}