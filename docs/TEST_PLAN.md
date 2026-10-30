> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [TEST_REPORT.md](TEST_REPORT.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

> ⚠️ **TÀI LIỆU AUTHENTICATION GỐC (#1):** xem `00_TONG_QUAN_T1_02_05.md` và `05_API_HANDOFF.md` để biết API #2–#5 đã cập nhật. Endpoint xác thực tổ chức chỉ còn demo-only dưới `/api/v1/demo/admin/...`.

# Test plan đề xuất – Authentication UT.Bridora

## Kiểm thử hệ thống (chưa tự động hóa)

| Mã | Thao tác | Kết quả mong đợi | Ưu tiên |
|---|---|---|---|
| A01 | GET /csrf, login đúng + cookie/header | 200, /me trả user đúng | P0 |
| A02 | Login sai mật khẩu | 401, lỗi chung | P0 |
| A03 | Login không có CSRF | 403 | P0 |
| A04 | Login sai 5 lần / 15 phút | tạm khóa, không phân biệt username tồn tại trong thông báo | P0 |
| A05 | GET /me chưa login | 401 | P0 |
| A06 | POST logout có CSRF, gọi /me bằng cookie cũ | 401 | P0 |
| A07 | Xem session ID trước/sau login | ID thay đổi | P0 |
| A08 | Admin đổi user sang SUSPENDED, user gọi /me lại | 401 | P0 |
| A09 | Admin revoke session, gọi /me bằng session cũ | 401 | P0 |
| A10 | STUDENT gọi /admin/organizations | 403 | P0 |
| A11 | DN A đọc demo resource của DN B | 403 | P0 |
| A12 | DN A thuộc org PENDING đọc demo private | 403 | P0 |
| A13 | DN A thuộc org VERIFIED đọc demo private | 200 | P0 |
| A14 | Password change sai mật khẩu hiện tại | 400, password không đổi | P0 |
| A15 | Password change đúng, dùng session cũ | 401 | P0 |
| A16 | Forgot email có/không có trong DB | cùng response chung | P0 |
| A17 | Reset token hợp lệ dùng 2 lần | lần 1 thành công, lần 2 400 | P0 |
| A18 | Invite token đã dùng / quá 48h | không tạo account thứ hai | P0 |
| A19 | Invite ENTERPRISE vào tổ chức UNIVERSITY | 400 | P0 |
| A20 | Một session SUSPENDED được mở lại sau đó | session cũ không tự được phục hồi | P0 |
| A21 | Xem audit logs bằng user thường | 403 | P1 |
| A22 | Đồng thời tiêu thụ cùng token trên 2 request | tối đa một request thành công | P1 |
| A23 | SQL injection email, password, org ID | không thực hiện SQL tùy ý | P0 |
| A24 | XSS trên displayName / email | không được thực thi khi hiển thị | P0 |
| A25 | Mailpit mất kết nối khi /forgot | không lộ account tồn tại, có retry/queue | Chưa đạt: cần outbox |
| A26 | Rate-limit 100 login attempts/phút từ cùng IP | throttled ở gateway | Chưa đạt: thêm trước production |
| A27 | Session cookie trên HTTPS | Secure, HttpOnly, SameSite theo chính sách | P0 khi deploy |
| A28 | Password reset sau khi token bị dùng | token không chấp nhận lại | P0 |

## Unit test hiện có trong starter

- `TokenCodecTest`: uniqueness, hashing and password length policy.

## Cần viết tiếp trong tuần code

- Testcontainers MySQL + `@SpringBootTest`/MockMvc cho Login/CSRF/Session.
- Integration test nhiều tổ chức và role.
- Performance test 300–500 users mẫu, auditing, session query indexes.
- Security review theo OWASP ASVS và các chức năng xử lý dữ liệu cá nhân.
