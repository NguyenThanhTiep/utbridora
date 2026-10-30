> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [API_DOCUMENTATION.md](API_DOCUMENTATION.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Hợp đồng API và cách T2–T5 tích hợp

## 1. API giữ từ Authentication #1

`GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `POST /api/v1/auth/password/change`, `POST /api/v1/auth/password/forgot`, `POST /api/v1/auth/password/reset`, `POST /api/v1/auth/invitations/accept`.  
Không thay đổi cách xác thực: browser gửi cookie SESSION + CSRF token cho yêu cầu thay đổi dữ liệu. Sau login, browser gọi lại `/csrf`.

## 2. API mới / nâng cấp

| API | AuthZ | Body hoặc query | Kết quả |
|---|---|---|---|
| POST `/api/v1/admin/memberships` | global `membership:admin:write` | `{userId,role,organizationId}` | cấp hoặc khôi phục membership, revoke phiên |
| DELETE `/api/v1/admin/memberships` | global `membership:admin:write` | body như trên | thu hồi membership, revoke phiên |
| PATCH `/api/v1/admin/users/{id}/status` | global `account:admin:status` | `{status,reasonCode}` | thay trạng thái + history + audit |
| PATCH `/api/v1/admin/users/{id}/revoke-sessions` | global `account:admin:revoke` | không cần body | session cũ hết hiệu lực request kế tiếp |
| GET `/api/v1/admin/audit-logs` | global `audit:global:read` | `page`, `size<=100`, optional `action`, `actorId` | paged AuditLogView |
| POST `/api/v1/admin/organizations` | global `organization:admin:create` | `{name,type}` | tạo tổ chức PENDING |
| PATCH `/api/v1/demo/admin/organizations/{id}/verify` | global + `ALLOW_DEMO_VERIFICATION=true` | không cần body | **chỉ demo**, T5 thay bằng xác thực thật |
| GET `/api/v1/demo/organizations/{orgId}/private` | enterprise membership + permission + org VERIFIED | path orgId | thử quyền theo tổ chức |

**Các endpoint quản lý của T1 là giao diện starter, chưa có OpenAPI generated, transactional validation toàn diện hay hardened request throttling.** Quy ước lỗi nhất quán cần PM/T1 chốt sớm; hiện controller dùng nhiều `IllegalArgumentException` và `ApiErrorHandler` cũ. Không để production tiếp tục thiếu classification lỗi 400/409.

## 3. T2–T5 cần lấy thông tin current user như thế nào?

- Không cho frontend gửi `actorId` rồi tin trực tiếp. Backend lấy `AppPrincipal.userId()` từ `Authentication` do Spring Security cung cấp.
- Không gắn `organizationId` một lần vào session rồi mặc định mọi request thuộc tổ chức đó. Với đa tổ chức, URL/active-context được kiểm tra lại với membership trong DB.
- Endpoint business nên có org path rõ ràng và query scoping theo org. Chặn mọi field cố thay đổi org khi create/update.
- Không cấp bất kỳ quyền nghiệp vụ nào cho ADMIN chỉ vì có quyền quản trị kỹ thuật.

## 4. ProfileVisibilityPort – hợp đồng bắt buộc T2/T5

```java
public interface ProfileVisibilityPort {
    boolean explicitlySharedWithEnterprise(String studentId, String enterpriseOrganizationId);
    boolean delegatedToSchool(String studentId, String universityOrganizationId);
}
```

Starter có `DenyAllProfileVisibility`: mọi quyền xem profile của người khác bị từ chối. Khi T2/T5 có bảng chia sẻ, viết adapter: kiểm tra active share, thời hạn, consent và trường dữ liệu; mở quyền theo *purpose*; thêm audit `STUDENT_PROFILE_ACCESSED`. T1 review trước khi thay default adapter.

## 5. Mẫu request

```http
PATCH /api/v1/admin/users/45aebb26-1dfa-45c0-b8a9-b57006cbbe07/status
Content-Type: application/json
X-CSRF-TOKEN: (lấy từ /auth/csrf)
Cookie: SESSION=(cookie HttpOnly của browser)

{"status":"SUSPENDED","reasonCode":"SECURITY_REVIEW"}
```

**Không chép Cookie/CSRF thật vào tài liệu, issue hoặc log.**

## 6. Giao tiếp giữa team

| Gửi | Nhận | Bản bàn giao |
|---|---|---|
| T1 | T2 | `users.id`, `ProfileVisibilityPort`, `self` permission, file metadata policy |
| T1 | T3 | `AppPrincipal`, `hasOrganization`, audit writer, role enterprise mentor extension |
| T1 | T4 | role enterprise aggregated read, giới hạn dữ liệu, org context |
| T1 | T5 | `organizations`, membership, status separation, quyền chia sẻ profile, scope helpers |
| T2/T5 | T1 | Luồng consent, bảng share, quy tắc trường phụ trách |
| T3 | T1 | người được phân công mentor, evaluation policy và danh mục audit |
| T4 | T1 | chính sách ẩn danh, quyền xuất thống kê nếu có |

**Gợi ý giao diện mới:** `GET /api/v1/auth/me/permissions` trả permission theo từng role/org còn hiệu lực. Đây chỉ để điều hướng frontend; tất cả API backend vẫn tự kiểm tra quyền.
