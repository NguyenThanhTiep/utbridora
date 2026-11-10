package vn.utbridora.auth.modules.taxonomy.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.*;
import vn.utbridora.auth.modules.taxonomy.application.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.operations.OperationFailure;

@RestController
@RequestMapping("/api/v1/taxonomy")
public class SkillTaxonomyController {
    private final SkillTaxonomyService taxonomy;
    private final TaxonomyCommands commands;
    private final TaxonomyAccess access;
    public SkillTaxonomyController(SkillTaxonomyService taxonomy,TaxonomyCommands commands,TaxonomyAccess access) {
        this.taxonomy=taxonomy;this.commands=commands;this.access=access;
    }
    private AppPrincipal actor(Authentication a) { return (AppPrincipal)a.getPrincipal(); }
    @GetMapping("/editor-access")
    public Map<String,Boolean> editorAccess(Authentication a) {
        access.requireReader(actor(a));
        try { access.requireEditor(actor(a));return Map.of("allowed",true); }
        catch(OperationFailure denied) { if(denied.status!=403) throw denied;return Map.of("allowed",false); }
    }
    @GetMapping("/categories")
    public Page<CategoryDto> categories(Authentication a,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return taxonomy.listCategories(actor(a),page,size);
    }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(Authentication a,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateCategory r) {
        return commands.createCategory(actor(a),key,r);
    }
    @PatchMapping("/categories/{code}")
    public CategoryDto updateCategory(Authentication a,@RequestHeader("Idempotency-Key") String key,@PathVariable String code,@Valid @RequestBody UpdateCategory r) {
        return commands.updateCategory(actor(a),key,code,r);
    }
    @GetMapping("/skills")
    public SkillCatalogPage skills(Authentication a,@RequestParam(required=false) String q,@RequestParam(defaultValue="false") boolean includeDeprecated,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) { return taxonomy.list(actor(a),q,includeDeprecated,page,size); }
    @PostMapping("/skills/resolve")
    public ResolveSkillsResponse resolve(Authentication a,@Valid @RequestBody ResolveSkillsRequest r) { return taxonomy.resolve(actor(a),r.values()); }
    @PostMapping("/skills") @ResponseStatus(HttpStatus.CREATED)
    public SkillCatalogItem createSkill(Authentication a,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateSkill r) {
        return commands.createSkill(actor(a),key,r);
    }
    @PatchMapping("/skills/{code}")
    public SkillCatalogItem updateSkill(Authentication a,@RequestHeader("Idempotency-Key") String key,@PathVariable String code,@Valid @RequestBody UpdateSkill r) {
        return commands.updateSkill(actor(a),key,code,r);
    }
    @GetMapping("/skills/{code}/aliases")
    public List<SkillAliasDto> aliases(Authentication a,@PathVariable String code) { return taxonomy.listAliases(actor(a),code); }
    @PostMapping("/skills/{code}/aliases") @ResponseStatus(HttpStatus.CREATED)
    public SkillAliasDto createAlias(Authentication a,@RequestHeader("Idempotency-Key") String key,@PathVariable String code,@Valid @RequestBody CreateAlias r) {
        return commands.createAlias(actor(a),key,code,r);
    }
    @DeleteMapping("/skills/{code}/aliases/{aliasId}")
    public MutationAck deleteAlias(Authentication a,@RequestHeader("Idempotency-Key") String key,@PathVariable String code,@PathVariable String aliasId,@Valid @RequestBody DeleteAlias r) {
        return commands.deleteAlias(actor(a),key,code,aliasId,r);
    }
}
