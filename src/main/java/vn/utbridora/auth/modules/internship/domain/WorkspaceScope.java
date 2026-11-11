package vn.utbridora.auth.modules.internship.domain;

import java.util.Objects;
import java.util.UUID;

/** Complete resource scope, resolved by trusted adapters, never from client actor claims. */
public record WorkspaceScope(UUID enterpriseOrgId, UUID schoolOrgId, UUID termId) {
    public WorkspaceScope {
        Objects.requireNonNull(enterpriseOrgId, "enterpriseOrgId");
        Objects.requireNonNull(schoolOrgId, "schoolOrgId");
        Objects.requireNonNull(termId, "termId");
    }
    public void requireSame(WorkspaceScope other) {
        if (!equals(other)) throw new WorkspaceRuleException(WorkspaceRuleException.Kind.SCOPE_MISMATCH);
    }
}
