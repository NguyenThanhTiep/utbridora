/** DEV08 đã tạo lớp truy vấn lưu trữ và tìm tên gọi khác của kỹ năng. */
package vn.utbridora.auth.modules.taxonomy.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.utbridora.auth.modules.taxonomy.domain.SkillAlias;

public interface SkillAliasRepository extends JpaRepository<SkillAlias,String> {
    @EntityGraph(attributePaths="skill")
    java.util.Optional<SkillAlias> findByNormalizedAlias(String normalizedAlias);
    boolean existsByNormalizedAlias(String normalizedAlias);
    @EntityGraph(attributePaths="skill")
    java.util.List<SkillAlias> findAllBySkill_SkillCodeOrderByAliasAsc(String skillCode);
}
