/** DEV08 đã tạo thực thể danh mục kỹ năng phân cấp cha-con. */
package vn.utbridora.auth.modules.taxonomy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="skill_categories", uniqueConstraints={
    @UniqueConstraint(name="uk_skill_category_code", columnNames="category_code"),
    @UniqueConstraint(name="uk_skill_category_name_key", columnNames="normalized_name")
})
public class SkillCategory {
    @Id @Column(length=36) public String id;
    @Column(name="category_code", nullable=false, length=64) public String categoryCode;
    @Column(nullable=false, length=128) public String name;
    @Column(name="normalized_name", nullable=false, length=128) public String normalizedName;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="parent_category_id", foreignKey=@ForeignKey(name="fk_skill_category_parent"))
    public SkillCategory parentCategory;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) public SkillStatus status=SkillStatus.ACTIVE;
    @Column(nullable=false) public long version=1;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;

    public SkillCategory() {}
    @PrePersist void insertTimestamps() {
        if(id==null) id=UUID.randomUUID().toString();
        if(createdAt==null) createdAt=Instant.now();
        updatedAt=Instant.now();
    }
    @PreUpdate void updateTimestamp() { updatedAt=Instant.now(); }
}
