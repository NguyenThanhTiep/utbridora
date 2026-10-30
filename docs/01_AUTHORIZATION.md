> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [AUTHORIZATION.md](AUTHORIZATION.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Công việc 2 – Authorization: thiết kế chi tiết

## 1. Mục tiêu

Cấu trúc cấp quyền phải đáp ứng ít nhất 4 vai trò (STUDENT, ENTERPRISE, SCHOOL_STAFF, ADMIN); nhiều organization; nhiều membership/1 identity; phân tách quyền kỹ thuật và quyền nghiệp vụ; ngăn doanh nghiệp A xem dữ liệu B; tránh admin kỹ thuật mặc định đọc hồ sơ SV.

## 2. Công thức quyết định

`ALLOW = authenticated && user.status == ACTIVE && security_version current && role permission && active membership in context && correct organization status/type && record ownership/share/delegation && operation-specific business guard`.

Không đủ một điều kiện → `DENY`. 401 khi chưa xác thực; 403 khi biết tài khoản nhưng thiếu quyền; 404 có thể dùng để che tài nguyên không thuộc phạm vi theo policy đã thống nhất. CSRF thiếu/sai → 403. Frontend chỉ dùng quyền `/auth/me` để hiển thị UI; backend là nơi đưa ra quyết định cuối cùng.

## 3. Roles, permissions và organizational scope

- `users`: 1 identity, không gắn duy nhất một role.
- `memberships(user_id, organization_id, role, active)`: gắn role với tổ chức; chỉ global ADMIN có `organization_id IS NULL`.
- `permissions(code,resource,action,description)`: code dạng `domain:scope:action` được tạo từ Flyway.
- `role_permissions(role,permission_code)`: liên kết Role enum với permission; nâng cấp nhiều role trong một org không cần tạo account thứ hai.
- Permission là **khả năng thực hiện loại thao tác**; scope và nghiệp vụ được kiểm tra ở service riêng. `ENTERPRISE` có `student_profile:shared:read` **không tự động** được xem mọi profile.
- Đổi membership phải tăng `users.security_version` và audit; chỉ admin toàn cục được cấp membership trong starter. Delegated administration là phase sau.

## 4. Các phép kiểm tra được implement

`AuthorizationPolicy.isSelf(authentication,userId)`
: Kiểm tra identity trùng `userId`.

`AuthorizationPolicy.hasGlobal(authentication, permission)`
: Yêu cầu ADMIN global active + permission trong DB. Không tự suy ra Admin có quyền nghiệp vụ T5.

`AuthorizationPolicy.hasOrganization(authentication, orgId, Role.ENTERPRISE|SCHOOL_STAFF|STUDENT, permission)`
: Yêu cầu membership active, role-permission match, organization đúng type + VERIFIED. Quan hệ T2 student school membership cần được thống nhất với T2.

`AuthorizationPolicy.canReadStudentProfile(authentication, studentId, orgId)`
: Tự xem hồ sơ hoặc explicit enterprise share/school delegated access. Nếu adapter `ProfileVisibilityPort` chưa được T2/T5 triển khai → deny mọi lượt xem của người khác. Bắt buộc bổ sung điều kiện field-level: CV, MSSV, số liên hệ, chứng chỉ có thể có quyền chia sẻ khác nhau.

## 5. Thứ không được chỉ dựa vào permission

| Tài nguyên | Yêu cầu bổ sung của nghiệp vụ |
|---|---|
| Student profile | Owner hoặc quyền chia sẻ hợp lệ + consent còn hiệu lực + trường dữ liệu được phép |
| Enterprise demand | `demand.organization_id == current enterprise context`, VERIFIED, trạng thái cho phép sửa |
| Cooperation request | Trường nhận yêu cầu, nhân sự được giao, trạng thái chấp thuận |
| Invitation | Chỉ student được mời; lời mời chưa hết hạn, đúng trạng thái |
| Placement | Trường xác nhận tiếp nhận; student/enterprise đúng bản ghi |
| Task/submission | Student đã có placement hợp lệ, mentor thuộc nhóm |
| Evaluation | Người hướng dẫn được phân công; công bố theo quy trình trường |
| Matching statistics | Trả aggregate ẩn danh, không trả row-level student data |
| File | Owner + reference object permission + scan CLEAN; file dạng PRIVATE không có URL public |
| Audit | ADMIN global `audit:global:read` trong starter, chưa cấp mặc định STAFF |

## 6. Mẫu truy cập T5

```java
@PreAuthorize("@authorizationPolicy.hasOrganization(authentication, #orgId, T(vn.utbridora.auth.user.Role).ENTERPRISE, 'cooperation:enterprise:create')")
@PostMapping("/organizations/{orgId}/demands")
public DemandDto create(@PathVariable String orgId, @Valid @RequestBody DemandDto dto) {
    // T5 also verifies dto.organizationId cannot override orgId; uses actor identity from session.
    return demandService.createForOrganization(orgId, dto);
}
```

T5 phải check record ownership bên trong service để ngăn gọi API từ luồng khác vượt quyền (Defense in Depth); với action khác dùng `@PreAuthorize` hoặc service guard phù hợp.

## 7. Chống IDOR / leo thang quyền

- Tránh `findById(id)` rồi trả object khi endpoint có org scope. Ưu tiên query `findByIdAndOrganizationId(...)`, sau đó check ownership/state trước serialization.
- Chặn thay đổi `organizationId`, `userId`, `role` từ JSON gửi lên nếu có thể suy ra từ session hoặc URL đã kiểm tra.
- Không cho user tự đổi role qua `/auth/me` hoặc update hồ sơ.
- Mỗi API mới phải có row trong permission matrix hoặc được PM/T1 duyệt quy tắc deny mặc định.
- Không ghi ra log toàn bộ URL có token/reset link hay thông tin cá nhân; không in access token/cookie.
- Thay đổi quyền khiến session cũ được thu hồi bằng security_version; role/permission catalog đổi trực tiếp trong DB không tự tăng version → **chỉ thay đổi catalog qua Flyway và quy trình rollout**, hoặc triển khai policy version toàn cục khi cần quyền động.

## 8. Acceptance criteria

1. Anonymous → 401; CSRF không hợp lệ → 403.
2. User role STUDENT gọi admin/membership grant → 403.
3. Enterprise A với `orgA` gọi API `orgB` → 403, ngay cả khi cùng ROLE_ENTERPRISE.
4. Enterprise A có permission nhưng organization PENDING/SUSPENDED → 403 tại API yêu cầu VERIFIED.
5. Enterprise A chưa có share profile → 403; đã share chỉ xem trường được phép.
6. Admin mặc định không xem profile/đánh giá nếu không có quyền nghiệp vụ riêng.
7. Mọi grant/revoke active membership có audit và revoke session thông qua security_version.
8. Regression: một người có membership ở 2 tổ chức không được chéo dữ liệu.
