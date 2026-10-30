# API nền tảng

Hợp đồng máy đọc: [openapi.json](openapi.json), OpenAPI 3.1. Prefix `/api/v1`. Giữ toàn bộ endpoint đã công bố; thêm read API phục vụ UI quản trị và filter audit. Chưa tích hợp Swagger UI/runtime generator; có thể import file vào công cụ OpenAPI nội bộ.

## Xác thực và CSRF

1. GET `/auth/csrf`, giữ cookie SESSION do server cấp, lấy `headerName` và `token`.
2. POST `/auth/login` JSON `{email,password}`, gửi cookie và header CSRF.
3. Login đổi session ID; GET `/auth/csrf` lại trước mutation kế tiếp.
4. GET `/auth/me`, `/auth/me/permissions` cho danh tính/quyền hiện tại.
5. POST `/auth/logout` có CSRF. Lấy CSRF mới khi bắt đầu flow anonymous tiếp theo.

Cookie HttpOnly, SameSite=Lax; Secure=true trong staging HTTPS. Không lưu token/session trong localStorage. Frontend API wrapper chỉ giữ CSRF trong memory và dùng `credentials: include`, không tự retry mutation lỗi.

## Endpoint

| Method/path sau prefix | Input chính | Quyền |
|---|---|---|
| GET /auth/csrf | Không | Public |
| POST /auth/login | email,password | Public + CSRF |
| POST /auth/logout | Không | Session + CSRF |
| GET /auth/me | Không | Session |
| GET /auth/me/permissions | Không | Session; UI hint |
| POST /auth/password/change | currentPassword,newPassword | Session + CSRF |
| POST /auth/password/forgot | email | Public + CSRF, response chung |
| POST /auth/password/reset | token,newPassword | Public + CSRF |
| POST /auth/invitations/accept | token,displayName,password | Public + CSRF |
| POST /admin/organizations | name,type | organization:admin:create |
| GET /admin/organizations | page,size | membership:admin:write |
| POST /admin/invitations | email,role,organizationId | membership:admin:write |
| POST /admin/memberships | userId,role,organizationId | membership:admin:write |
| DELETE /admin/memberships | Body giống POST | membership:admin:write |
| GET /admin/users | page,size,search,status | account:admin:status |
| PATCH /admin/users/{id}/status | status,reasonCode | account:admin:status |
| PATCH /admin/users/{id}/revoke-sessions | Không | account:admin:revoke |
| GET /admin/users/{id}/status-history | page,size | account:admin:status |
| GET /admin/audit-logs | page,size,action,actorId,organizationId,result,from,until | audit:global:read |

Các permission quản trị yêu cầu **ADMIN global active**. Tất cả mutation cần CSRF. Pagination mặc định 25, max100; page max10000; sort thời gian giảm dần, id làm tie-breaker. Audit `from <= time < until`. `search` user tìm tên/email, escape ký tự wildcard, giới hạn 150 ký tự.

GET `/demo/organizations/{organizationId}/private` trả dữ liệu giả để kiểm chứng enterprise org VERIFIED. PATCH `/demo/admin/organizations/{id}/verify` chỉ tồn tại khi ALLOW_DEMO_VERIFICATION=true; mặc định tắt, không bật staging. Xác thực tổ chức thật không thuộc endpoint T1 này.

## Ví dụ response và lỗi

```json
{"userId":"<uuid>","email":"demo@example.test","displayName":"Người dùng demo","memberships":[{"role":"ENTERPRISE","organizationId":"<uuid>"}]}
```

```json
{"content":[],"number":0,"size":25,"totalElements":0,"totalPages":0}
```

Page thực tế có thể có thêm metadata Spring Data; client chỉ nên dùng các field được tài liệu hóa. Không có passwordHash/securityVersion ở user DTO. Status history chứa actor/from/to/reason/changedAt; audit dùng `occurredAt`.

| HTTP | Ý nghĩa / xử lý |
|---|---|
| 400 | JSON/enum/validation hoặc transition/token không hợp lệ; kiểm tra dữ liệu, không retry mutation tự động |
| 401 | Chưa login, session timeout/revoked hoặc credential không hợp lệ; xóa dữ liệu UI và đăng nhập lại |
| 403 | Role/permission/org/business guard hoặc CSRF không hợp lệ; không tăng quyền ở frontend |
| 404 | Route demo tắt hoặc tài nguyên/route không tồn tại theo controller |
| 409 | Trùng dữ liệu hoặc concurrency conflict; tải lại trạng thái |
| 5xx | Hạ tầng/SMTP admin invite/audit lỗi; không hiển thị stacktrace, dùng X-Request-ID điều tra |

429 chưa có limiter theo IP ở application; cần cấu hình và test gateway trước vận hành công khai. Forgot luôn phản hồi chung kể cả SMTP thất bại; hiện gửi mail đồng bộ, chưa có outbox/retry. Không tuyên bố chống enumeration theo timing đã hoàn tất.
