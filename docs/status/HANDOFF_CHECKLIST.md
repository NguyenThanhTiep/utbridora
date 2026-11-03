# Checklist bàn giao Core Platform

Ngày cập nhật30/10/2026. Trạng thái cuối và số test chính xác xem TEST_REPORT. Mã nguồn sẵn sàng review không có nghĩa toàn bộ SRS đã được nghiệm thu.

## Nền tảng đã có

- [x] Đọc SRS và báo cáo gốc; khảo sát source, build/test baseline được ghi.
- [x] Tiếp tục project cũ; giữ Java21/SpringBoot4.1.2/modular monolith.
- [x] Giữ migrationV1–V6; thêm V7; MySQL8.4 schema sạch và upgrade path có test.
- [x] Login/session JDBC/CSRF/logout/me, credential policy và account lockout.
- [x] Reset/invite token expiry/replay và atomic reset concurrent.
- [x] Membership/global-org permissions và backend scope guard.
- [x] Status history/revoke, transaction audit và database mutex admin cuối.
- [x] Audit registry, correlation, filter/paging và read permission.
- [x] React auth/recovery/account/admin/membership/audit; loading/error, responsive.
- [x] OpenAPI và hướng dẫn tích hợp; cấu hình staging/health/backup/CI.
- [ ] Ký nghiệm thu toàn bộ12 Must và ma trận quyền bởi PM/T2–T5.
- [ ] Triển khai server staging HTTPS và kiểm thử vận hành thực.

## Dependency cần team khác bàn giao

| Owner | Phần còn thiếu | Điều kiện nhận |
|---|---|---|
| T2/T5 | Share/consent/purpose/field/time, delegated school | Adapter reviewed + deny cases + projection an toàn |
| T3/T5 | Placement/mentor assignment | Record-level tests cho task/evaluation |
| T4/PM | Ngưỡng ẩn danh và export | Không lộ danh tính hoặc suy ngược nhóm nhỏ |
| T5/PM | Xác thực tổ chức thật | Không dùng demo verify ở staging |
| PM/T1 | Flow pre-provision PENDING, retention và đặc quyền | Quy trình được phê duyệt, migration/test nếu phát sinh |

## Việc T1 tiếp tục trước môi trường công khai

- [ ] Durable mail outbox/retry, kiểm tra timing enumeration và chống lạm dụng recovery.
- [ ] Gateway throttling theo IP, monitoring/alert và load test.
- [ ] HTTPS cookie/CSRF xuyên reverse proxy trên staging thực.
- [ ] Tách DB runtime/migration credential; audit append-only/retention theo phê duyệt.
- [ ] Backup/restore drill, RPO/RTO, vulnerability/secret scan và review độc lập.
- [ ] File storage/upload/AV/download khi được giao phạm vi; hiện metadata-only.
- [ ] CI chạy thành công trên GitHub Actions; Git local và origin đã cấu hình tới repo private `NguyenThanhTiep/utbridora`, kết quả CI cần kiểm tra sau push.
- [ ] MFA/SSO theo quyết định triển khai tài khoản đặc quyền.

Không còn dùng checklist thiết kế cũ để chứng minh chức năng đã chạy. Test chưa thực thi ghi NOT RUN; dependency nghiệp vụ ghi CHỜ, không ghi PASS thay team khác.
