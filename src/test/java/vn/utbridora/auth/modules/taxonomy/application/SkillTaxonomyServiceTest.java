package vn.utbridora.auth.modules.taxonomy.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.ResolveSkillsResponse;
import vn.utbridora.auth.modules.taxonomy.domain.Skill;
import vn.utbridora.auth.modules.taxonomy.domain.SkillAlias;
import vn.utbridora.auth.modules.taxonomy.domain.SkillStatus;
import vn.utbridora.auth.modules.taxonomy.domain.TaxonomyCatalogState;
import vn.utbridora.auth.modules.taxonomy.repository.SkillAliasRepository;
import vn.utbridora.auth.modules.taxonomy.repository.SkillCategoryRepository;
import vn.utbridora.auth.modules.taxonomy.repository.SkillRepository;
import vn.utbridora.auth.modules.taxonomy.repository.TaxonomyCatalogStateRepository;
import vn.utbridora.auth.security.AppPrincipal;

/** DEV08 bổ sung kiểm thử phân giải tên gọi khác qua khóa chuẩn hóa và truy vấn đúng alias. */
class SkillTaxonomyServiceTest {
    private final SkillCategoryRepository categories = mock(SkillCategoryRepository.class);
    private final SkillRepository skills = mock(SkillRepository.class);
    private final SkillAliasRepository aliases = mock(SkillAliasRepository.class);
    private final TaxonomyCatalogStateRepository states = mock(TaxonomyCatalogStateRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final SkillTaxonomyService service = new SkillTaxonomyService(categories, skills, aliases, states, audit, mock(TaxonomyAccess.class));
    private final AppPrincipal actor = new AppPrincipal("00000000-0000-4000-8000-000000000001",
            "tester@example.test", null, 1, List.of());

    @BeforeEach
    void setCatalogVersion() {
        TaxonomyCatalogState state = mock(TaxonomyCatalogState.class);
        state.id = 1;
        state.taxonomyVersion = 3;
        when(states.findById(1)).thenReturn(Optional.of(state));
    }

    @Test
    void resolvesCaseAndWhitespaceVariantsToTheCanonicalSkillCode() {
        Skill skill = new Skill();
        skill.skillCode = "JAVA";
        skill.status = SkillStatus.ACTIVE;
        SkillAlias alias = new SkillAlias();
        alias.skill = skill;
        when(skills.findByNormalizedName("java core")).thenReturn(Optional.empty());
        when(aliases.findByNormalizedAlias("java core")).thenReturn(Optional.of(alias));

        ResolveSkillsResponse response = service.resolve(actor, List.of("  Java   Core "));

        assertEquals("skills-v3", response.taxonomyVersion());
        assertEquals("java core", response.items().getFirst().normalizedInput());
        assertEquals("RESOLVED", response.items().getFirst().status());
        assertEquals("JAVA", response.items().getFirst().skillCode());
        verify(aliases).findByNormalizedAlias("java core");
    }

    @Test
    void unknownNameRemainsUnknownWithoutCreatingASkill() {
        when(skills.findByNormalizedName("java framework")).thenReturn(Optional.empty());
        when(aliases.findByNormalizedAlias("java framework")).thenReturn(Optional.empty());

        ResolveSkillsResponse response = service.resolve(actor, List.of("Java Framework"));

        assertEquals("UNKNOWN", response.items().getFirst().status());
        assertNull(response.items().getFirst().skillCode());
    }
}
