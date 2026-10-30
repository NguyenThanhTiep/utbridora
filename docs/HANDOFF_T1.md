> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

> ⚠️ **TÀI LIỆU AUTHENTICATION GỐC (#1):** xem `00_TONG_QUAN_T1_02_05.md` và `05_API_HANDOFF.md` để biết API #2–#5 đã cập nhật. Endpoint xác thực tổ chức chỉ còn demo-only dưới `/api/v1/demo/admin/...`.

# Bàn giao 2 ngày Team 1 – UT.Bridora

## Gói tối thiểu trước tuần code

| Loại tài liệu | T1 cần đưa ra | Đầu mối xác nhận |
|---|---|---|
| User story Must | US-A01..US-A10 với acceptance criteria | PM + BA T5 |
| State diagram | Account, Session, Invitation, Recovery | T2/T5 |
| Data dictionary | `users`, `organizations`, `memberships`, `one_time_tokens`, `audit_logs`, `SPRING_SESSION` | T2, T3, T4, T5 |
| Wireframe | Login, dashboard shell, invitation, forgot/reset password, user management | Frontend T2/T3/T5 |
| Permission matrix | Actor × action × resource × organization × condition | 4 leader |
| Repository | Spring Boot module, Flyway, README, `.env.example`, React demo | Tất cả dev |

## Permission matrix ban đầu (chưa phải phê duyệt UTH)

| Action | STUDENT | ENTERPRISE | SCHOOL_STAFF | ADMIN |
|---|---|---|---|---|
| `auth.login` | Có | Có | Có | Có |
| `profile.read.self` | Có | Không | Theo phạm vi | Không mặc định |
| `profile.read.shared` | Không | Khi T5 đã cấp chia sẻ | Theo phạm vi | Không mặc định |
| `cooperation.create` | Không | Tổ chức được xác thực | Không | Không |
| `cooperation.approve` | Không | Không | Được phân công | Không mặc định |
| `placement.accept` | Chính bản thân | Không | Không | Không |
| `task.assign` | Không | Nhóm do mình hướng dẫn | Theo quy định | Không |
| `task.submit` | Bản thân trong placement | Không | Không | Không |
| `evaluation.create` | Không | Người hướng dẫn có quyền | Theo quy định | Không |
| `users.suspend` | Không | Không | Không | Theo cấp quyền |
| `audit.read` | Không mặc định | Không mặc định | Khi được cấp | Admin/kiểm toán được cấp |

## Dữ liệu / Quy tắc kỹ thuật trả lời T2–T5

- Mỗi user nhận `userId` dạng UUID-string; mỗi organization `organizationId` dạng UUID-string.
- Một user có thể nhận nhiều memberships, nhưng API cấp thêm vai trò chưa triển khai; team phải thống nhất luồng chọn tổ chức hiện hành.
- Chỉ T1 tạo/chỉnh schema identity, security; team khác thêm migrations nghiệp vụ theo quy tắc thống nhất.
- T5 quản lý quyết định xác thực và chia sẻ hồ sơ, T1 chỉ cung cấp `organizationId` và công cụ kiểm tra quyền.
- Đầu vào kiểm tra quyền phải có user, action, resource, resource owner, organization và trạng thái nghiệp vụ; không chỉ `hasRole`.
- Mọi API business trả 401 khi chưa xác thực; 403/404 khi không có quyền; không hiển thị dữ liệu nhạy cảm qua response lỗi.
- Client không được dựa vào role hiển thị để quyết định cấp quyền; backend là điểm xác thực cuối cùng.

## Wireframe nội dung cần vẽ

1. Login: logo, email, mật khẩu, lỗi chung.
2. Active account: nhập tên, mật khẩu, xác nhận token; link mời quá hạn.
3. Forgot password: email, phản hồi chung không tiết lộ email tồn tại.
4. Reset password: mật khẩu mới + trạng thái token.
5. Dashboard shell: header, sidebar biến đổi theo role.
6. Admin users: bảng người dùng, trạng thái, khóa/mở, revoke sessions.
7. Admin invite: email, role, organization, gửi lời mời.
8. Admin audit: lọc người dùng, hành động, thời gian, kết quả.

## Những việc phải giao đúng hạn

- D1: bảng vai trò/phạm vi + câu hỏi gửi cho từng team, trạng thái tài khoản, schema sơ bộ.
- D2: user story + ma trận quyền + data dictionary + wireframe + kiến trúc và API contract.
- Trước trình bày thứ Bảy: backend khởi động, schema có migration, API CSRF/login/me/logout, React demo, thử trường hợp 401/403.
- Ngay khi có blocker: đưa ra kênh chung cùng người đang chờ và thời điểm cần quyết định.

## Dùng trong trình bày 10 phút

- 0–2p: vấn đề T1 giải quyết và 4 role; minh họa dashboard.
- 2–5p: login -> session -> /me -> role/org -> logout.
- 5–7p: doanh nghiệp A cố xem tài nguyên B, backend từ chối, giải thích điều kiện.
- 7–9p: DB, audit, API & những gì đã sẵn sàng cho 4 team.
- 9–10p: blockers, quyết định còn chờ, kế hoạch tích hợp.
