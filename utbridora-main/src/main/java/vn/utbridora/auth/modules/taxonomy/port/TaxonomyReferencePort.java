package vn.utbridora.auth.modules.taxonomy.port;

import java.util.Set;
import vn.utbridora.auth.security.AppPrincipal;

/** Internal read of exact canonical references, stable for the caller's write transaction. */
public interface TaxonomyReferencePort {
    record References(String taxonomyVersion,Set<String> activeSkillCodes,Set<String> activeCategoryCodes) {}
    References read(AppPrincipal actor,Set<String> skillCodes,Set<String> categoryCodes);
}
