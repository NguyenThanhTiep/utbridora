/** DEV08 đã tạo lớp truy vấn lưu trữ và tìm danh mục kỹ năng. */
package vn.utbridora.auth.modules.taxonomy.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.utbridora.auth.modules.taxonomy.domain.SkillCategory;

public interface SkillCategoryRepository extends JpaRepository<SkillCategory,String> {
    @EntityGraph(attributePaths="parentCategory")
    Page<SkillCategory> findAllByOrderByCreatedAtDescIdAsc(Pageable pageable);
    @EntityGraph(attributePaths="parentCategory")
    java.util.List<SkillCategory> findAllByOrderByCategoryCodeAsc();
    java.util.Optional<SkillCategory> findByCategoryCode(String categoryCode);
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName,String id);
}
