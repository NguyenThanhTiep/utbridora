# Checklist bàn giao Core Platform

Đối chiếu tài liệu ngày **04/11/2026**. Các mục nền tảng đã có dựa trên source và evidence lịch sử 29–30/10/2026; không có lượt kiểm thử runtime mới trong lần cập nhật này. Số test và thời điểm thực thi xem [TEST_REPORT](../testing/TEST_REPORT.md); trạng thái hiện tại xem [IMPLEMENTATION_STATUS](IMPLEMENTATION_STATUS.md). Mã nguồn sẵn sàng review không có nghĩa toàn bộ SRS đã được nghiệm thu.

SRS hiện có là v1.2.4, Contract Pack B01–B06 còn là Candidate. Giữ phân biệt yêu cầu đã đặc tả, phần chờ signoff, source đã triển khai và test đã thực thi. Hướng dẫn tích hợp hiện hành ở [BE/FE cho các team và AI](../guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md).

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
| T3/T5 | T5 sở hữu Placement, T3 sở hữu case và HR/Mentor assignment | Handoff APPROVED→WAITING_START; chỉ ACTIVE/effectiveAt hợp lệ mới cho task/submission; record-level tests |
| T4 | Thực thi privacy k=5, suppression/query guard và export theo baseline | Test không lộ danh tính/suy ngược nhóm nhỏ; không mở lại lựa chọn k=3 cho demo |
| T5/PM | Xác thực tổ chức thật | Không dùng demo verify ở staging |
| T1/T2/T5 | Import/roster/identity binding, claim/token và mail outbox mới | Contract được owner/consumer chấp nhận, migration/test phù hợp; không tự tạo user ACTIVE hoặc schoolApproved chỉ từ preview/import |
| PM/owner dữ liệu | Chính sách retention/đặc quyền và điều kiện dùng dữ liệu thật | Phân biệt mặc định demo đã đặc tả với phê duyệt vận hành thật; không coi tài liệu proposal là signoff |

## Việc T1 tiếp tục trước môi trường công khai

- [ ] Durable mail outbox/retry, kiểm tra timing enumeration và chống lạm dụng recovery.
- [ ] Gateway throttling theo IP, monitoring/alert và load test.
- [ ] HTTPS cookie/CSRF xuyên reverse proxy trên staging thực.
- [ ] Tách DB runtime/migration credential; audit append-only/retention theo phê duyệt.
- [ ] Backup/restore drill, RPO/RTO, vulnerability/secret scan và review độc lập.
- [ ] File storage/upload/AV/download khi được giao phạm vi; hiện metadata-only.
- [ ] CI chạy thành công trên GitHub Actions; origin local trỏ `NguyenThanhTiep/utbridora`, visibility/quyền và kết quả CI remote chưa được kiểm chứng trong lượt cập nhật này.
- [ ] MFA/SSO theo quyết định triển khai tài khoản đặc quyền.

Không còn dùng checklist thiết kế cũ để chứng minh chức năng đã chạy. Test chưa thực thi ghi NOT RUN; dependency nghiệp vụ ghi CHỜ, không ghi PASS thay team khác.
