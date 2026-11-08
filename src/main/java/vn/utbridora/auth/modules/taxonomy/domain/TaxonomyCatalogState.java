/** DEV08 đã tạo thực thể lưu phiên bản hiện hành của danh mục kỹ năng. */
package vn.utbridora.auth.modules.taxonomy.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="taxonomy_catalog_state")
public class TaxonomyCatalogState {
    @Id public Integer id;
    @Column(name="taxonomy_version", nullable=false) public long taxonomyVersion;
    @Column(name="changed_at", nullable=false) public Instant changedAt;

    protected TaxonomyCatalogState() {}
}
