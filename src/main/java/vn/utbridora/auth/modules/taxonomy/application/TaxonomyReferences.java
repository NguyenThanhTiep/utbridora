package vn.utbridora.auth.modules.taxonomy.application;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort;
import vn.utbridora.auth.security.AppPrincipal;

@Service
public class TaxonomyReferences implements TaxonomyReferencePort {
    private final NamedParameterJdbcTemplate jdbc;
    private final TaxonomyAccess access;
    public TaxonomyReferences(NamedParameterJdbcTemplate jdbc,TaxonomyAccess access) {this.jdbc=jdbc;this.access=access;}
    @Override @Transactional(propagation=Propagation.MANDATORY)
    public References read(AppPrincipal actor,Set<String> skills,Set<String> categories) {
        access.requireReader(actor);
        Long version=jdbc.getJdbcTemplate().queryForObject("SELECT taxonomy_version FROM taxonomy_catalog_state WHERE id=1 FOR SHARE",Long.class);
        // Match the current locked version, even if Core already established an older RR snapshot.
        Set<String> validSkills=skills.isEmpty()?Set.of():new HashSet<>(jdbc.queryForList(
            "SELECT skill_code FROM skills WHERE status='ACTIVE' AND BINARY skill_code IN (:codes) FOR SHARE",Map.of("codes",skills),String.class));
        Set<String> validCategories=categories.isEmpty()?Set.of():new HashSet<>(jdbc.queryForList(
            "SELECT category_code FROM skill_categories WHERE status='ACTIVE' AND BINARY category_code IN (:codes) FOR SHARE",Map.of("codes",categories),String.class));
        return new References("skills-v"+version,Set.copyOf(validSkills),Set.copyOf(validCategories));
    }
}
