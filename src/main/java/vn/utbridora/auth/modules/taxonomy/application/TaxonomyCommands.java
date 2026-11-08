package vn.utbridora.auth.modules.taxonomy.application;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.*;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;

/** Current authorization precedes replay; mutation, version and audit are atomic. */
@Service
@Transactional
public class TaxonomyCommands {
    private final SkillTaxonomyService taxonomy;
    private final TaxonomyAccess access;
    private final OperationLedger ledger;
    public TaxonomyCommands(SkillTaxonomyService taxonomy,TaxonomyAccess access,OperationLedger ledger) {
        this.taxonomy=taxonomy;this.access=access;this.ledger=ledger;
    }
    public CategoryDto createCategory(AppPrincipal a,String key,CreateCategory request) {
        return execute(a,key,"CATEGORY_CREATE",request,201,CategoryDto.class,()->taxonomy.createCategory(a,request),taxonomy::categoryId);
    }
    public CategoryDto updateCategory(AppPrincipal a,String key,String code,UpdateCategory request) {
        return execute(a,key,"CATEGORY_UPDATE",Map.of("code",code,"data",request),200,CategoryDto.class,()->taxonomy.updateCategory(a,code,request),taxonomy::categoryId);
    }
    public SkillCatalogItem createSkill(AppPrincipal a,String key,CreateSkill request) {
        return execute(a,key,"SKILL_CREATE",request,201,SkillCatalogItem.class,()->taxonomy.createSkill(a,request),taxonomy::skillId);
    }
    public SkillCatalogItem updateSkill(AppPrincipal a,String key,String code,UpdateSkill request) {
        return execute(a,key,"SKILL_UPDATE",Map.of("code",code,"data",request),200,SkillCatalogItem.class,()->taxonomy.updateSkill(a,code,request),taxonomy::skillId);
    }
    public SkillAliasDto createAlias(AppPrincipal a,String key,String code,CreateAlias request) {
        return execute(a,key,"ALIAS_CREATE",Map.of("code",code,"data",request),201,SkillAliasDto.class,()->taxonomy.createAlias(a,code,request),SkillAliasDto::aliasId);
    }
    public MutationAck deleteAlias(AppPrincipal a,String key,String code,String id,DeleteAlias request) {
        return execute(a,key,"ALIAS_DELETE",Map.of("code",code,"aliasId",id,"data",request),200,MutationAck.class,()->taxonomy.deleteAlias(a,code,id,request),MutationAck::resourceId);
    }
    private <T> T execute(AppPrincipal actor,String key,String operation,Object payload,int status,Class<T> type,Supplier<T> effect,Function<T,String> reference) {
        access.requireEditor(actor);
        return ledger.execute(new OperationLedger.Command(actor.userId(),null,"TAXONOMY_"+operation,key,payload,status),type,effect,reference);
    }
}
