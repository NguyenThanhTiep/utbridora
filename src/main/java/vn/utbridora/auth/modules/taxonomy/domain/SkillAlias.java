/** DEV08 đã tạo thực thể tên gọi khác liên kết với mã kỹ năng chuẩn. */
package vn.utbridora.auth.modules.taxonomy.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="skill_aliases", uniqueConstraints={
    @UniqueConstraint(name="uk_skill_alias_normalized", columnNames="normalized_alias")
})
public class SkillAlias {
    @Id @Column(length=36) public String id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="skill_id", nullable=false, foreignKey=@ForeignKey(name="fk_skill_alias_skill"))
    public Skill skill;
    @Column(nullable=false, length=128) public String alias;
    @Column(name="normalized_alias", nullable=false, length=128) public String normalizedAlias;
    @Column(nullable=false) public long version=1;
    @Column(name="created_at", nullable=false) public Instant createdAt;

    public SkillAlias() {}
    @PrePersist void insertTimestamp() {
        if(id==null) id=UUID.randomUUID().toString();
        if(createdAt==null) createdAt=Instant.now();
    }
}
