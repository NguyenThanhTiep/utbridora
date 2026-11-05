/** DEV08 đã tạo các tuyến API (giao diện lập trình) tra cứu và quản lý danh mục, kỹ năng, tên gọi khác; thao tác ghi tạm yêu cầu ADMIN. */
package vn.utbridora.auth.modules.taxonomy.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.*;
import vn.utbridora.auth.modules.taxonomy.application.SkillTaxonomyService;
import vn.utbridora.auth.security.AppPrincipal;

@RestController
@RequestMapping("/api/v1/taxonomy")
@Validated
public class SkillTaxonomyController {
    private final SkillTaxonomyService taxonomy;

    public SkillTaxonomyController(SkillTaxonomyService taxonomy) {
        this.taxonomy = taxonomy;
    }

    private AppPrincipal actor(Authentication authentication) {
        return (AppPrincipal) authentication.getPrincipal();
    }

    @GetMapping("/categories")
    public Page<CategoryDto> categories(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return taxonomy.listCategories(actor(authentication), page, size);
    }

    // CONTRACT_PENDING_SIGNOFF (chưa xác nhận hợp đồng): quyền ghi tạm cho ADMIN theo quyết định đã nhận.
    // BC12 v1.1 yêu cầu taxonomy:skills:write và cấp quyền TAX đích danh; cần T1 cung cấp guard trước khi đổi.
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto createCategory(Authentication authentication, @Valid @RequestBody CreateCategory request) {
        return taxonomy.createCategory(actor(authentication), request);
    }

    @PatchMapping("/categories/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public CategoryDto updateCategory(Authentication authentication, @PathVariable String code,
            @Valid @RequestBody UpdateCategory request) {
        return taxonomy.updateCategory(actor(authentication), code, request);
    }

    @GetMapping("/skills")
    public SkillCatalogPage skills(Authentication authentication, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeDeprecated,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return taxonomy.list(actor(authentication), q, includeDeprecated, page, size);
    }

    @PostMapping("/skills/resolve")
    public ResolveSkillsResponse resolve(Authentication authentication,
            @Valid @RequestBody ResolveSkillsRequest request) {
        return taxonomy.resolve(actor(authentication), request.values());
    }

    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SkillCatalogItem createSkill(Authentication authentication, @Valid @RequestBody CreateSkill request) {
        return taxonomy.createSkill(actor(authentication), request);
    }

    @PatchMapping("/skills/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public SkillCatalogItem updateSkill(Authentication authentication, @PathVariable String code,
            @Valid @RequestBody UpdateSkill request) {
        return taxonomy.updateSkill(actor(authentication), code, request);
    }

    @GetMapping("/skills/{code}/aliases")
    public List<SkillAliasDto> aliases(Authentication authentication, @PathVariable String code) {
        return taxonomy.listAliases(actor(authentication), code);
    }

    @PostMapping("/skills/{code}/aliases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SkillAliasDto createAlias(Authentication authentication, @PathVariable String code,
            @Valid @RequestBody CreateAlias request) {
        return taxonomy.createAlias(actor(authentication), code, request);
    }

    @DeleteMapping("/skills/{code}/aliases/{aliasId}")
    @PreAuthorize("hasRole('ADMIN')")
    public MutationAck deleteAlias(Authentication authentication, @PathVariable String code,
            @PathVariable String aliasId, @Valid @RequestBody DeleteAlias request) {
        return taxonomy.deleteAlias(actor(authentication), code, aliasId, request);
    }

}
