/** DEV08 đã tạo cổng nội bộ để các mô-đun khác gọi chức năng danh mục kỹ năng T4. */
package vn.utbridora.auth.modules.taxonomy.port;

import java.util.List;
import vn.utbridora.auth.modules.taxonomy.api.TaxonomyContracts.*;
import vn.utbridora.auth.security.AppPrincipal;

/**
 * Cổng nội bộ dùng chung trong ứng dụng để T2/T5 gọi chức năng danh mục kỹ năng của T4.
 * CONTRACT_PENDING_SIGNOFF (chưa xác nhận hợp đồng): chữ ký hàm và DTO (đối tượng truyền dữ liệu)
 * theo BC12 trong Contract Pack v1.1 Candidate (bản đề xuất), cần nhóm sở hữu/nhóm sử dụng rà soát.
 * BC12 v1.1 mô tả ActorContext (ngữ cảnh người thao tác) và Result (kết quả có kiểu); core hiện
 * chưa cung cấp hai kiểu này, nên cổng dùng AppPrincipal và DTO trực tiếp, cần T1 chốt bộ chuyển đổi.
 * CONTRACT_PENDING_SIGNOFF (chưa xác nhận hợp đồng): tạm cho ADMIN toàn cục ghi theo quyết định đã nhận.
 * BC12 v1.1 yêu cầu taxonomy:skills:write và named TAX assignment (cấp quyền TAX đích danh),
 * nhưng core hiện chưa có danh mục quyền và cơ chế kiểm tra việc cấp quyền này.
 * Mutation còn chờ Idempotency-Key (khóa chống lặp) và audit metadata (siêu dữ liệu nhật ký) từ T1;
 * module này không tự tạo cơ chế thay thế.
 */
public interface SkillTaxonomyPort {
    Page<CategoryDto> listCategories(AppPrincipal actor, int page, int size);

    SkillCatalogPage list(AppPrincipal actor, String query, boolean includeDeprecated, int page, int size);

    ResolveSkillsResponse resolve(AppPrincipal actor, List<String> values);

    CategoryDto createCategory(AppPrincipal actor, CreateCategory request);

    CategoryDto updateCategory(AppPrincipal actor, String categoryCode, UpdateCategory request);

    SkillCatalogItem createSkill(AppPrincipal actor, CreateSkill request);

    SkillCatalogItem updateSkill(AppPrincipal actor, String skillCode, UpdateSkill request);

    SkillAliasDto createAlias(AppPrincipal actor, String skillCode, CreateAlias request);

    List<SkillAliasDto> listAliases(AppPrincipal actor, String skillCode);

    MutationAck deleteAlias(AppPrincipal actor, String skillCode, String aliasId, DeleteAlias request);
}
