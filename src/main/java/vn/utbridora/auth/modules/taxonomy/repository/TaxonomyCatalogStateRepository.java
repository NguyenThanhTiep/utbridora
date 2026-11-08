/** DEV08 đã tạo lớp truy vấn lưu trữ phiên bản danh mục kỹ năng. */
package vn.utbridora.auth.modules.taxonomy.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.utbridora.auth.modules.taxonomy.domain.TaxonomyCatalogState;

public interface TaxonomyCatalogStateRepository extends JpaRepository<TaxonomyCatalogState,Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TaxonomyCatalogState s where s.id=:id")
    java.util.Optional<TaxonomyCatalogState> findLockedById(@Param("id") Integer id);
}
