
package vn.utbridora.auth.modules.taxonomy.port;

import java.util.List;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.*;
import vn.utbridora.auth.security.AppPrincipal;


public interface SkillTaxonomyPort {
    Page<CategoryDto> listCategories(AppPrincipal actor, int page, int size);

    SkillCatalogPage list(AppPrincipal actor, String query, boolean includeDeprecated, int page, int size);

    ResolveSkillsResponse resolve(AppPrincipal actor, List<String> values);






    List<SkillAliasDto> listAliases(AppPrincipal actor, String skillCode);

}
