/** DEV08 đã cập nhật quy tắc BC12: phân trang, tên gọi khác, phiên bản và mã thao tác audit (kiểm toán); metadata audit (siêu dữ liệu) chờ T1. */
package vn.utbridora.auth.modules.taxonomy.application;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.*;
import vn.utbridora.auth.modules.taxonomy.domain.*;
import vn.utbridora.auth.modules.taxonomy.port.SkillTaxonomyPort;
import vn.utbridora.auth.modules.taxonomy.repository.*;
import vn.utbridora.auth.security.AppPrincipal;

/** Các quy tắc nghiệp vụ của hợp đồng danh mục kỹ năng T4 tạm thời. */
@Service
public class SkillTaxonomyService implements SkillTaxonomyPort {
    private static final int STATE_ID = 1;
    private static final int MAX_PAGE = 10000;
    private static final int MAX_SIZE = 100;
    private static final String RESOURCE_TYPE = "skill_taxonomy_entry";
    private final SkillCategoryRepository categories;
    private final SkillRepository skills;
    private final SkillAliasRepository aliases;
    private final TaxonomyCatalogStateRepository catalogState;
    private final AuditService audit;

    public SkillTaxonomyService(SkillCategoryRepository categories, SkillRepository skills,
            SkillAliasRepository aliases, TaxonomyCatalogStateRepository catalogState, AuditService audit) {
        this.categories = categories;
        this.skills = skills;
        this.aliases = aliases;
        this.catalogState = catalogState;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryDto> listCategories(AppPrincipal actor, int page, int size) {
        requireActor(actor);
        PageRequest paging = paging(page, size, "categoryCode");
        var result = categories.findAllByOrderByCreatedAtDescIdAsc(paging);
        return new Page<>(result.getContent().stream().map(this::categoryDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeNode> categoryTree(AppPrincipal actor) {
        requireActor(actor);
        List<SkillCategory> all = categories.findAllByOrderByCategoryCodeAsc();
        Map<String, List<SkillCategory>> children = new HashMap<>();
        for (SkillCategory item : all) {
            String parentId = item.parentCategory == null ? null : item.parentCategory.id;
            children.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(item);
        }
        return children.getOrDefault(null, List.of()).stream()
                .map(item -> treeNode(item, children)).toList();
    }

    private CategoryTreeNode treeNode(SkillCategory item, Map<String, List<SkillCategory>> children) {
        return new CategoryTreeNode(item.id, item.categoryCode, item.name, item.status.name(), item.version,
                children.getOrDefault(item.id, List.of()).stream()
                        .map(child -> treeNode(child, children)).toList());
    }

    @Transactional
    public CategoryDto updateCategoryStatus(AppPrincipal actor, String code, UpdateCategoryStatus request) {
        SkillCategory current = category(code);
        return updateCategory(actor, code, new UpdateCategory(request.expectedVersion(), current.name,
                request.status()));
    }

    @Override
    @Transactional(readOnly = true)
    public SkillCatalogPage list(AppPrincipal actor, String query, boolean includeDeprecated, int page, int size) {
        requireActor(actor);
        PageRequest paging = paging(page, size, "skillCode");
        String normalizedQuery = query == null || query.isBlank() ? null : SkillNameNormalizer.normalize(query);
        if (normalizedQuery != null && normalizedQuery.length() > 128)
            throw new IllegalArgumentException("Search text is too long");
        var result = skills.findCatalog(normalizedQuery, includeDeprecated, SkillStatus.ACTIVE, paging);
        return new SkillCatalogPage(version(), new Page<>(result.getContent().stream().map(this::skillDto).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public ResolveSkillsResponse resolve(AppPrincipal actor, List<String> values) {
        requireActor(actor);
        if (values == null || values.isEmpty() || values.size() > 100)
            throw new IllegalArgumentException("Provide between 1 and 100 skill names");
        List<SkillResolution> resolved = values.stream().map(value -> {
            if (value == null || value.length() > 128)
                throw new IllegalArgumentException("Skill name must contain at most 128 characters");
            String key = SkillNameNormalizer.normalize(value);
            if (key.isEmpty())
                throw new IllegalArgumentException("Skill name is required");
            Skill skill = skills.findByNormalizedName(key).orElseGet(() -> aliases.findByNormalizedAlias(key)
                    .map(alias -> alias.skill).orElse(null));
            if (skill == null || skill.status != SkillStatus.ACTIVE)
                return new SkillResolution(value, key, "UNKNOWN", null, null);
            return new SkillResolution(value, key, "RESOLVED", skill.skillCode, skill.id);
        }).toList();
        return new ResolveSkillsResponse(version(), resolved);
    }

    @Transactional
    public CategoryDto createCategory(AppPrincipal actor, CreateCategory request) {
        requireActor(actor);
        validateCode(request.categoryCode(), "categoryCode");
        String name = validatedName(request.name());
        String nameKey = SkillNameNormalizer.normalize(name);
        TaxonomyCatalogState state = lockState();
        if (categories.existsById(request.categoryCode())
                || categories.findByCategoryCode(request.categoryCode()).isPresent())
            throw new IllegalStateException("Category code already exists");
        ensureCategoryNameAvailable(nameKey, null);
        SkillCategory category = new SkillCategory();
        category.categoryCode = request.categoryCode();
        category.name = name;
        category.normalizedName = nameKey;
        category.status = SkillStatus.ACTIVE;
        if (request.parentId() != null && !request.parentId().isBlank()
                && request.parentCategoryCode() != null && !request.parentCategoryCode().isBlank())
            throw new IllegalArgumentException("Provide parentId or parentCategoryCode, not both");
        if (request.parentId() != null && !request.parentId().isBlank())
            category.parentCategory = categories.findById(request.parentId())
                    .orElseThrow(() -> notFound("Parent category not found"));
        else if (request.parentCategoryCode() != null && !request.parentCategoryCode().isBlank())
            category.parentCategory = category(request.parentCategoryCode());
        categories.saveAndFlush(category);
        bump(state);
        auditChange(actor, category.id, "CREATE");
        return categoryDto(category);
    }

    @Transactional
    public CategoryDto updateCategory(AppPrincipal actor, String categoryCode, UpdateCategory request) {
        requireActor(actor);
        TaxonomyCatalogState state = lockState();
        SkillCategory category = category(categoryCode);
        checkVersion(category.version, request.expectedVersion());
        String name = validatedName(request.name());
        String nameKey = SkillNameNormalizer.normalize(name);
        ensureCategoryNameAvailable(nameKey, category.id);
        category.name = name;
        category.normalizedName = nameKey;
        category.status = status(request.status());
        category.version++;
        categories.saveAndFlush(category);
        bump(state);
        auditChange(actor, category.id, category.status == SkillStatus.DEPRECATED ? "DEPRECATE" : "UPDATE");
        return categoryDto(category);
    }

    @Override
    @Transactional
    public SkillCatalogItem createSkill(AppPrincipal actor, CreateSkill request) {
        requireActor(actor);
        validateCode(request.skillCode(), "skillCode");
        TaxonomyCatalogState state = lockState();
        String name = validatedName(request.name());
        String nameKey = SkillNameNormalizer.normalize(name);
        ensureSkillNameAvailable(nameKey, null);
        SkillCategory category = category(request.categoryCode());
        Skill skill = new Skill();
        skill.skillCode = request.skillCode();
        skill.name = name;
        skill.normalizedName = nameKey;
        skill.category = category;
        skill.status = SkillStatus.ACTIVE;
        if (request.parentSkillCode() != null && !request.parentSkillCode().isBlank()) {
            skill.parentSkill = skill(request.parentSkillCode());
            ensureSameCategory(skill.parentSkill, category);
        }
        skills.saveAndFlush(skill);
        bump(state);
        auditChange(actor, skill.id, "CREATE");
        return skillDto(skill);
    }

    @Override
    @Transactional
    public SkillCatalogItem updateSkill(AppPrincipal actor, String skillCode, UpdateSkill request) {
        requireActor(actor);
        TaxonomyCatalogState state = lockState();
        Skill skill = skill(skillCode);
        checkVersion(skill.version, request.expectedVersion());
        String name = validatedName(request.name());
        String nameKey = SkillNameNormalizer.normalize(name);
        ensureSkillNameAvailable(nameKey, skill.id);
        SkillCategory category = category(request.categoryCode());
        Skill parent = request.parentSkillCode() == null || request.parentSkillCode().isBlank() ? null
                : skill(request.parentSkillCode());
        if (parent != null) {
            ensureSameCategory(parent, category);
            if (parent.id.equals(skill.id) || isDescendant(parent, skill.id))
                throw new IllegalStateException("Skill parent would create a hierarchy cycle");
        }
        skill.name = name;
        skill.normalizedName = nameKey;
        skill.category = category;
        skill.parentSkill = parent;
        skill.status = status(request.status());
        skill.version++;
        skills.saveAndFlush(skill);
        bump(state);
        auditChange(actor, skill.id, skill.status == SkillStatus.DEPRECATED ? "DEPRECATE" : "UPDATE");
        return skillDto(skill);
    }

    @Override
    @Transactional
    public SkillAliasDto createAlias(AppPrincipal actor, String skillCode, CreateAlias request) {
        requireActor(actor);
        TaxonomyCatalogState state = lockState();
        Skill skill = skill(skillCode);
        checkVersion(skill.version, request.expectedVersion());
        String value = validatedName(request.alias());
        String key = SkillNameNormalizer.normalize(value);
        if (skills.findByNormalizedName(key).isPresent() || aliases.existsByNormalizedAlias(key))
            throw new IllegalStateException("Alias is already mapped");
        skill.version++;
        SkillAlias alias = new SkillAlias();
        alias.skill = skill;
        alias.alias = value;
        alias.normalizedAlias = key;
        aliases.saveAndFlush(alias);
        skills.saveAndFlush(skill);
        bump(state);
        auditChange(actor, alias.id, "CREATE");
        return aliasDto(alias);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkillAliasDto> listAliases(AppPrincipal actor, String skillCode) {
        requireActor(actor);
        skill(skillCode);
        return aliases.findAllBySkill_SkillCodeOrderByAliasAsc(skillCode).stream().map(this::aliasDto).toList();
    }

    @Override
    @Transactional
    public MutationAck deleteAlias(AppPrincipal actor, String skillCode, String aliasId, DeleteAlias request) {
        requireActor(actor);
        TaxonomyCatalogState state = lockState();
        Skill skill = skill(skillCode);
        SkillAlias alias = aliases.findById(aliasId).orElseThrow(() -> notFound("Alias not found"));
        if (!alias.skill.id.equals(skill.id))
            throw notFound("Alias not found");
        checkVersion(alias.version, request.expectedVersion());
        aliases.delete(alias);
        skill.version++;
        skills.saveAndFlush(skill);
        bump(state);
        auditChange(actor, alias.id, "DELETE_ALIAS");
        return new MutationAck(alias.id, skill.version, Instant.now().toString());
    }

    @Transactional(readOnly = true)
    public Page<CategoryDto> categories(AppPrincipal actor, int page, int size) {
        return listCategories(actor, page, size);
    }

    private PageRequest paging(int page, int size, String order) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > MAX_SIZE)
            throw new IllegalArgumentException("Invalid pagination");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, order));
    }

    private String version() {
        long current = catalogState.findById(STATE_ID)
                .orElseThrow(() -> new IllegalStateException("Taxonomy catalog is not initialized")).taxonomyVersion;
        return "skills-v" + current;
    }

    private TaxonomyCatalogState lockState() {
        return catalogState.findLockedById(STATE_ID)
                .orElseThrow(() -> new IllegalStateException("Taxonomy catalog is not initialized"));
    }

    private void bump(TaxonomyCatalogState state) {
        state.taxonomyVersion++;
        state.changedAt = Instant.now();
        catalogState.save(state);
    }

    private SkillCategory category(String code) {
        return categories.findByCategoryCode(code).orElseThrow(() -> notFound("Category not found"));
    }

    private Skill skill(String code) {
        return skills.findBySkillCode(code).orElseThrow(() -> notFound("Skill not found"));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private void requireActor(AppPrincipal actor) {
        if (actor == null || actor.userId() == null)
            throw new org.springframework.security.access.AccessDeniedException("Authenticated actor required");
    }

    private void checkVersion(long current, long expected) {
        if (current != expected)
            throw new IllegalStateException("Version conflict; reload before retrying");
    }

    private String validatedName(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 128)
            throw new IllegalArgumentException("Name must contain 1 to 128 characters");
        return value.trim();
    }

    private void validateCode(String value, String field) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}"))
            throw new IllegalArgumentException(field + " must use uppercase letters, digits and underscore");
    }

    private SkillStatus status(String value) {
        try {
            return SkillStatus.valueOf(value);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Status must be ACTIVE or DEPRECATED");
        }
    }

    private void ensureCategoryNameAvailable(String key, String exceptId) {
        boolean exists = exceptId == null ? categories.existsByNormalizedName(key)
                : categories.existsByNormalizedNameAndIdNot(key, exceptId);
        if (exists)
            throw new IllegalStateException("Category name is already used");
    }

    private void ensureSkillNameAvailable(String key, String exceptId) {
        boolean duplicate = exceptId == null ? skills.existsByNormalizedName(key)
                : skills.existsByNormalizedNameAndIdNot(key, exceptId);
        if (duplicate || aliases.existsByNormalizedAlias(key))
            throw new IllegalStateException("Skill name is already used");
    }

    private void ensureSameCategory(Skill parent, SkillCategory category) {
        if (!parent.category.id.equals(category.id))
            throw new IllegalArgumentException("Parent skill must be in the same category");
    }

    private boolean isDescendant(Skill candidate, String ancestorId) {
        Skill current = candidate;
        int depth = 0;
        while (current.parentSkill != null && depth++ < 1000) {
            current = current.parentSkill;
            if (current.id.equals(ancestorId))
                return true;
        }
        if (depth >= 1000)
            throw new IllegalStateException("Skill hierarchy exceeds maximum depth");
        return false;
    }

    private void auditChange(AppPrincipal actor, String resourceId, String operation) {
        audit.writeScoped(actor.userId(), "TAXONOMY_CHANGED", RESOURCE_TYPE, resourceId, "SUCCESS", null, operation);
    }

    private CategoryDto categoryDto(SkillCategory category) {
        return new CategoryDto(category.id, category.categoryCode, category.name,
                category.parentCategory == null ? null : category.parentCategory.categoryCode, category.status.name(),
                category.version);
    }

    private SkillCatalogItem skillDto(Skill skill) {
        return new SkillCatalogItem(skill.skillCode, skill.name, skill.category.categoryCode,
                skill.parentSkill == null ? null : skill.parentSkill.skillCode, skill.status.name(), skill.version);
    }

    private SkillAliasDto aliasDto(SkillAlias alias) {
        return new SkillAliasDto(alias.id, alias.skill.skillCode, alias.alias, alias.version);
    }
}
