/** DEV08 đã tạo thực thể kỹ năng chuẩn và quan hệ với danh mục cha, kỹ năng cha và trạng thái. */
package vn.utbridora.auth.modules.taxonomy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="skills", uniqueConstraints={
    @UniqueConstraint(name="uk_skill_code", columnNames="skill_code"),
    @UniqueConstraint(name="uk_skill_name_key", columnNames="normalized_name")
})
public class Skill {
    @Id @Column(length=36) public String id;
    @Column(name="skill_code", nullable=false, length=64) public String skillCode;
    @Column(nullable=false, length=128) public String name;
    @Column(name="normalized_name", nullable=false, length=128) public String normalizedName;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="category_id", nullable=false, foreignKey=@ForeignKey(name="fk_skill_category"))
    public SkillCategory category;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="parent_skill_id", foreignKey=@ForeignKey(name="fk_skill_parent"))
    public Skill parentSkill;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) public SkillStatus status=SkillStatus.ACTIVE;
    @Column(nullable=false) public long version=1;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="updated_at", nullable=false) public Instant updatedAt;

    public Skill() {}
    @PrePersist void insertTimestamps() {
        if(id==null) id=UUID.randomUUID().toString();
        if(createdAt==null) createdAt=Instant.now();
        updatedAt=Instant.now();
    }
    @PreUpdate void updateTimestamp() { updatedAt=Instant.now(); }
}
