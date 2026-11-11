
package vn.utbridora.auth.modules.taxonomy.api;

import java.util.List;
import jakarta.validation.constraints.*;


public final class TaxonomyContracts {
    private TaxonomyContracts() {
    }


    public record SkillCatalogItem(String skillCode, String name, String categoryCode,
            String parentSkillCode, String status, long version) {
    }

    public record CategoryDto(String categoryCode, String name, String parentCategoryCode,
            String status, long version) {
    }

    public record CategoryTreeNode(String id, String categoryCode, String name, String status,
            long version, List<CategoryTreeNode> children) {
    }

    public record UpdateCategoryStatus(@Min(1) long expectedVersion,
            @Pattern(regexp = "ACTIVE|DEPRECATED") String status) {
    }

    public record SkillAliasDto(String aliasId, String skillCode, String alias, long version) {
    }

    public record SkillCatalogPage(String taxonomyVersion, Page<SkillCatalogItem> skills) {
    }

    public record SkillResolution(String input, String normalizedInput, String status,
            String skillCode) {
    }

    public record ResolveSkillsRequest(@NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 128) String> values) {
    }

    public record ResolveSkillsResponse(String taxonomyVersion, List<SkillResolution> items) {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record CreateCategory(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String categoryCode,
            @NotBlank @Size(max = 128) String name, String parentCategoryCode) {
    }

    public record UpdateCategory(@Min(1) long expectedVersion, @NotBlank @Size(max = 128) String name,
            @Pattern(regexp = "ACTIVE|DEPRECATED") String status) {
    }

    public record CreateSkill(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String skillCode,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String categoryCode,
            String parentSkillCode) {
    }

    public record UpdateSkill(@Min(1) long expectedVersion, @NotBlank @Size(max = 128) String name,
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String categoryCode,
            String parentSkillCode, @Pattern(regexp = "ACTIVE|DEPRECATED") String status) {
    }

    public record CreateAlias(@NotBlank @Size(max = 128) String alias, @Min(1) long expectedVersion) {
    }

    public record DeleteAlias(@Min(1) long expectedVersion) {
    }

    public record MutationAck(String resourceId, long version, String changedAt) {
    }
}
