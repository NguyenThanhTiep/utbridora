package vn.utbridora.auth.modules.internship.domain;

import java.util.Objects;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.CohortDto;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.CreateCohort;

/** CP-WS1 value aggregate; deliberately has no unapproved status or JPA mapping. */
public record Cohort(UUID id, WorkspaceScope scope, String code, String name, long version) {
    public Cohort {
        Objects.requireNonNull(scope, "scope");
        var validated = new CohortDto(id, scope.enterpriseOrgId(), scope.schoolOrgId(),
            scope.termId(), code, name, version);
        code = validated.code();
        name = validated.name();
    }
    public record Key(WorkspaceScope scope, String code) {
        public Key {
            Objects.requireNonNull(scope, "scope");
            if (code == null) throw new IllegalArgumentException("code is required");
            code = code.replaceAll("(?U)^\\s+|\\s+$", "");
            int length = code.codePointCount(0, code.length());
            if (length < 1 || length > 64) throw new IllegalArgumentException("Invalid cohort code");
        }
    }
    public Key key() { return new Key(scope, code); }
    public static Cohort create(UUID id, WorkspaceScope trustedScope, CreateCohort input) {
        if (!trustedScope.termId().equals(input.termId())) {
            throw new WorkspaceRuleException(WorkspaceRuleException.Kind.SCOPE_MISMATCH);
        }
        return new Cohort(id, trustedScope, input.code(), input.name(), 1);
    }
    public CohortDto toDto() {
        return new CohortDto(id, scope.enterpriseOrgId(), scope.schoolOrgId(), scope.termId(), code, name, version);
    }
}
