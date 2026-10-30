> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# User Story công việc T1 #2–5 (Must / Should / Could)

**Mẫu:** `As a [actor], I want [capability], so that [benefit]`. Mỗi story có acceptance criteria kiểm thử được; tiêu chuẩn Must là đề xuất cho PM duyệt.

## Authorization (#2)

**AUTHZ-01 – Must – Chặn truy cập chéo doanh nghiệp**  
Là đại diện doanh nghiệp A, tôi chỉ muốn thao tác trên dữ liệu doanh nghiệp A để không truy cập nhầm dữ liệu B.  
AC: (1) orgA VERIFIED + enterprise membership hợp lệ → xem nhu cầu A; (2) orgB ID trong URL → 403/404; (3) sửa JSON organizationId không bypass; (4) từ chối có audit và test.

**AUTHZ-02 – Must – Phân quyền tài nguyên theo role**  
Là người dùng, tôi chỉ được sử dụng chức năng thuộc quyền.  
AC: student không được gọi API admin; admin chỉ có permission kỹ thuật được cấp; role-permission catalog nhất quán với matrix.

**AUTHZ-03 – Must – Quyền chia sẻ hồ sơ SV**  
Là trường, tôi muốn chỉ cung cấp hồ sơ SV cho doanh nghiệp có quyền để bảo vệ dữ liệu cá nhân.  
AC: không share/adapter chưa tồn tại → deny; share hợp lệ + consent + còn hạn + field được phép → allow; hết hạn/thu hồi → deny; access ghi log. Phần share thuộc T5/T2.

**AUTHZ-04 – Must – Cấp/thu hồi membership**  
Là admin có thẩm quyền, tôi muốn phân vai trò theo tổ chức để điều phối nhân sự.  
AC: org-type phù hợp role, không tạo trùng `(user,role,org)`, revoke role làm session cũ không còn hợp lệ; audit thành công.

**AUTHZ-05 – Should – Chuyển active organization context trong UI**  
AC: frontend chọn membership hợp lệ; backend không tin context ngoài session nếu chưa check DB; đổi org không thể vượt quyền.

**AUTHZ-06 – Could – Delegated admin và permission override**  
AC: có cơ chế approval riêng và kiểm thử quyền chéo; không thêm vào starter khi nghiệp vụ chưa chốt.

## Database Core (#3)

**DB-01 – Must – Migrate schema thống nhất**  
Là developer, tôi muốn có database migrations dùng chung để giảm xung đột khi ghép 5 team.  
AC: V1–V6 chạy theo thứ tự trên DB sạch, không sửa V1/V2, `ddl-auto=validate`, dữ liệu baseline giữ nguyên.

**DB-02 – Must – Identity tham chiếu dùng chung**  
AC: `users.id` dùng ở T2/T3/T5; `organizations.id` nối doanh nghiệp; không tái tạo bảng account trong module khác.

**DB-03 – Must – Lưu metadata file an toàn**  
AC: lưu owner, sha256, size, classification, scan status; nội dung file không trong MySQL; download không public; chưa có scan thì deny.

**DB-04 – Should – Cơ chế backup/restore có kiểm chứng**  
AC: có tài liệu khôi phục, chạy restore định kỳ trong môi trường riêng, hạn chế quyền DB.

**DB-05 – Could – Archival/partition audit**  
AC: không xóa chứng cứ ngoài retention policy; kiểm tra restore và query hiệu năng.

## Account Lifecycle (#4)

**STATE-01 – Must – Khóa, mở khóa theo sơ đồ**  
Là admin, tôi muốn quản lý trạng thái tài khoản để chặn truy cập không hợp lệ.  
AC: ACTIVE→SUSPENDED; SUSPENDED→ACTIVE; PENDING→ACTIVE chỉ qua invite, không qua status admin; DEACTIVATED terminal trong starter.

**STATE-02 – Must – Ghi lý do và lịch sử chuyển trạng thái**  
AC: có actor, from/to, reasonCode, timestamp; người dùng không tự disable admin; log cùng transaction.

**STATE-03 – Must – Thu hồi phiên khi thay đổi trạng thái/quyền**  
AC: security_version tăng; request kế tiếp của session cũ 401; tài khoản ACTIVE sau revoke session có thể đăng nhập lại.

**STATE-04 – Must – Bảo vệ admin duy nhất**  
AC: không cho tắt/revoke admin cuối; phải bổ sung concurrent test trước production.

**STATE-05 – Should – Mở lại tài khoản DEACTIVATED qua quy trình xét duyệt**  
AC: có phê duyệt đa bên theo chính sách; chưa triển khai starter.

## Audit Log (#5)

**AUDIT-01 – Must – Ghi hành động bảo mật quan trọng**  
Là người giám sát bảo mật, tôi cần xem ai cấp quyền, khóa account, đổi thông tin để phát hiện sự cố.  
AC: events đúng schema, không log mật khẩu/token, có request_id.

**AUDIT-02 – Must – Ghi hành động nghiệp vụ**  
Là cán bộ có thẩm quyền, tôi muốn đối soát ai duyệt hợp tác/chia sẻ/đánh giá.  
AC: T2/T3/T5 gọi AuditService theo event registry; trường thông tin tối thiểu, có org scope.

**AUDIT-03 – Must – Xem log trong phạm vi được cấp**  
AC: `audit:global:read` chỉ ADMIN global trong starter; student/enterprise gọi API nhận 403; page size max 100.

**AUDIT-04 – Must – Log denied an toàn**  
AC: denial audit được phát khi authorization handler từ chối; không ghi raw URL/token/body; nguồn không có danh tính actor có thể null.

**AUDIT-05 – Should – Cảnh báo bất thường**  
AC: cảnh báo mass denial/credential stuffing, phát hiện vượt ngưỡng; cần monitoring/alert pipeline.

**AUDIT-06 – Could – Immutable archival / WORM**  
AC: có chứng cứ không thể sửa theo chính sách retention; chưa triển khai starter.
