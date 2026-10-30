> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [TEST_REPORT.md](TEST_REPORT.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Kế hoạch kiểm thử & tiêu chí nghiệm thu T1 #2–5

## 1. Test pyramid

- **Unit:** trạng thái tài khoản (`AccountStatusPolicyTest`), thuộc tính quyền (`AuthorizationPolicyTest`) và validation reason.
- **Integration @SpringBootTest:** khởi động với MySQL 8.4 sạch (Testcontainers nếu nhóm cấu hình), chạy Flyway V1–V6, Jpa validate, Spring Session JDBC.
- **HTTP E2E:** dùng MockMvc/TestRestTemplate/browser cookie+CSRF; test login → API có scope → revoke/status → session cũ 401.
- **Security regression:** brute-force/CSRF/IDOR/multi-org/inactive memberships/missing business share.

## 2. Cases bắt buộc

| ID | Thao tác | Kết quả mong đợi | Team |
|---|---|---|---|
| SEC-01 | Không login truy cập API private | 401 | T1 |
| SEC-02 | Login thành công, GET /me | đúng user+memberships | T1 |
| SEC-03 | POST không có CSRF | 403 | T1 |
| SEC-04 | Student gọi PATCH admin status | 403 | T1 |
| SEC-05 | Enterprise A gọi endpoint org B | 403/404 | T1/T5 |
| SEC-06 | Enterprise A có role nhưng org PENDING | 403 | T1/T5 |
| SEC-07 | Enterprise A có share chưa phê duyệt | 403 | T1/T5 |
| SEC-08 | Enterprise A share được T5 cấp theo thời hạn | 200; chỉ đúng field | T2/T5 |
| SEC-09 | Admin grant role vào org sai type | lỗi validation | T1 |
| SEC-10 | Grant role trùng | idempotent hoặc conflict theo contract | T1 |
| SEC-11 | Revoke role, gọi lại với cookie cũ | 401 | T1 |
| SEC-12 | ACTIVE → SUSPENDED, cookie cũ | 401 request kế tiếp | T1 |
| SEC-13 | SUSPENDED → ACTIVE | login mới hoạt động | T1 |
| SEC-14 | PENDING → ACTIVE qua admin status | bị chặn | T1 |
| SEC-15 | DEACTIVATED → ACTIVE | bị chặn | T1 |
| SEC-16 | Vô hiệu hóa admin cuối | bị chặn | T1 |
| SEC-17 | Hai admin cùng bị tắt đồng thời | vẫn còn >=1 admin, production gate | T1 |
| SEC-18 | Đổi password/reset password | security_version tăng và cookie cũ 401 | T1 |
| SEC-19 | Khóa account → audit/history | từ/đến + reason code + actor | T1 |
| SEC-20 | API bị từ chối phân quyền | audit ACCESS_DENIED, không raw URL/token | T1 |
| SEC-21 | Student gọi audit logs | 403 | T1 |
| SEC-22 | Admin GET audit logs page/size | 200, dữ liệu lọc, không PII | T1 |
| SEC-23 | Migrations chạy trên DB sạch | đủ tables/index/FKs | T1 |
| SEC-24 | Migration nâng cấp dữ liệu bản cũ | không mất user/session/audit V1/V2 | T1 |
| SEC-25 | File metadata PENDING scan | không có API download công khai | T1/T2 |
| SEC-26 | T5 phê duyệt cooperation rollback | không có audit SUCCESS giả | T5/T1 |
| SEC-27 | 1 user có 2 org membership | không đọc chéo khi đổi orgId | T1/T5 |
| SEC-28 | Org bị SUSPENDED sau login | API enterprise cần VERIFIED từ chối | T1/T5 |

## 3. Test integration mẫu với MySQL

1. Khởi động MySQL sạch qua `docker compose up -d`, cấu hình `DB_URL`, `DB_USER`, `DB_PASSWORD`.
2. `mvn test` và `mvn spring-boot:run` (máy developer phải cài Maven/JDK21 và tải được dependencies).
3. Kiểm tra log Flyway: V1,V2,V3,V4,V5,V6 migrate thành công; không có hibernate schema update.
4. Tạo admin bootstrap (env), tạo organization ENTERPRISE, bật `ALLOW_DEMO_VERIFICATION=true` **chỉ local** để demo verified org.
5. Mời user ENTERPRISE, accept, login (cookie + csrf), truy vấn org A và B.
6. Grant/revoke membership + status, kiểm tra `security_version` và audit.
7. Tắt demo flag, xác nhận demo verification API trả 404 và không tồn tại trong production.

## 4. Cảnh báo giới hạn kiểm thử hiện tại

Project đã bổ sung source JUnit nhưng **chưa được build/test end-to-end hoặc xác minh chạy MySQL trong môi trường phát hành file**. Trong môi trường hiện tại có `javac` nhưng không có Maven/môi trường Spring Boot tích hợp; đã có thể kiểm tra biên dịch Java độc lập cho rule trạng thái. Test 28 trường hợp là **kế hoạch** chứ không phải 28 bài đã chạy.
