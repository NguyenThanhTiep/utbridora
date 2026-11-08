/** DEV08 đã tạo lớp truy vấn lưu trữ và tìm kỹ năng chuẩn. */
package vn.utbridora.auth.modules.taxonomy.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.utbridora.auth.modules.taxonomy.domain.Skill;
import vn.utbridora.auth.modules.taxonomy.domain.SkillStatus;

public interface SkillRepository extends JpaRepository<Skill,String> {
    @EntityGraph(attributePaths={"category","parentSkill"})
    @Query("select s from Skill s where (:includeDeprecated=true or s.status=:activeStatus) " +
        "and (:query is null or lower(s.skillCode) like concat('%',:query,'%') " +
        "or s.normalizedName like concat('%',:query,'%')) order by s.createdAt desc, s.id asc")
    Page<Skill> findCatalog(@Param("query") String query,@Param("includeDeprecated") boolean includeDeprecated,
                            @Param("activeStatus") SkillStatus activeStatus,
                            Pageable pageable);
    @EntityGraph(attributePaths={"category","parentSkill"})
    java.util.Optional<Skill> findBySkillCode(String skillCode);
    java.util.Optional<Skill> findByNormalizedName(String normalizedName);
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName,String id);
}
