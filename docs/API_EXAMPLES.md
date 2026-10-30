> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [API_DOCUMENTATION.md](API_DOCUMENTATION.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

> ⚠️ **TÀI LIỆU AUTHENTICATION GỐC (#1):** xem `00_TONG_QUAN_T1_02_05.md` và `05_API_HANDOFF.md` để biết API #2–#5 đã cập nhật. Endpoint xác thực tổ chức chỉ còn demo-only dưới `/api/v1/demo/admin/...`.

# Tình huống API mẫu – bản demo T1

Các request thay đổi trạng thái đều cần Cookie `SESSION` và CSRF header. Cách dễ nhất là dùng web-demo React, sau đó xem Network tab trong DevTools.

```text
GET /api/v1/auth/csrf
  -> 200 {"headerName":"X-CSRF-TOKEN", "token":"..."}

POST /api/v1/auth/login
  Header: X-CSRF-TOKEN: <token>
  Body: {"email":"admin@example.test", "password":"<secret>"}
  -> 200 {"userId":"...", "email":"...", "displayName":"...", "memberships":[...]}

GET /api/v1/auth/csrf       # refresh after login
GET /api/v1/auth/me         # auth user, active session
POST /api/v1/auth/logout   # include latest csrf header
```

Tạo tổ chức demo (ADMIN):

```json
{"name":"Công ty ABC","type":"ENTERPRISE"}
```

```http
POST /api/v1/admin/organizations
PATCH /api/v1/admin/organizations/{organizationId}/verify
```

Mời đại diện tổ chức (ADMIN):

```http
POST /api/v1/admin/invitations
```

```json
{"email":"representative@example.test","role":"ENTERPRISE","organizationId":"<id>"}
```

Mở Mailpit `localhost:8025`, lấy link mời; frontend route activation (UI chưa có) cần đọc token sau dấu `#token=` rồi gọi:

```http
POST /api/v1/auth/invitations/accept
```

```json
{"token":"<one-time-token>","displayName":"Đại diện ABC","password":"<new-strong-password>"}
```

Tài khoản doanh nghiệp đăng nhập và thử API:

```http
GET /api/v1/demo/organizations/{organizationId}/private
```

Chỉ đúng membership ENTERPRISE + org VERIFIED mới 200.
