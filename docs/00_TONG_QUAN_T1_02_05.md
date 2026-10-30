> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# UT.Bridora · T1 FOUNDATION – Thiết kế công việc 2–5

**Phiên bản:** 1.0 / 29-10-2026  
**Nền tảng kế thừa:** Authentication Foundation Java 21 / Spring Boot 4.1.2 / Spring Security / Spring Session JDBC / MySQL 8.4 / Flyway.  
**Trạng thái:** Bản thiết kế và starter code; **chưa qua build/integration test trên MySQL**. Không khẳng định production-ready.

## 0. Quy tắc thống nhất

1. `users.id` là identity duy nhất; `memberships` biểu diễn (user, role, organization) như đã thiết kế trong Authentication. **Không tạo hệ `users` thứ hai**, không thay đổi cookie/session, không tách 4 form đăng nhập độc lập.
2. `roles` ở bản đầu là enum Java + chuỗi role trong `memberships`/`role_permissions`, không nhân đôi bằng một bảng `user_roles` khác. `permissions` và `role_permissions` là danh mục quyền dùng chung do Flyway quản lý.
3. `organizations` do T1 sở hữu danh tính tổ chức; `enterprise_verifications`, nhu cầu, Placement, chia sẻ hồ sơ thuộc T5. Admin kỹ thuật không mặc định phê duyệt nghiệp vụ của T5.
4. **Deny by default**; điều kiện cho phép = có phiên hợp lệ ∧ tài khoản ACTIVE ∧ membership active ∧ permission phù hợp ∧ đúng organization/owner ∧ điều kiện nghiệp vụ do team sở hữu xác nhận.
5. T1 cung cấp kiểm tra quyền dùng lại; các service T2–T5 **bắt buộc** thực hiện kiểm tra mức tài nguyên (row/record) và chia sẻ chi tiết, không chỉ ẩn nút ở frontend.
6. Mọi cấp/thu hồi quyền, chuyển trạng thái, xem dữ liệu nhạy cảm, duyệt/chia sẻ/đánh giá phải có sự kiện audit. Không ghi mật khẩu, token, payload cá nhân vào audit.
7. Thao tác thay đổi membership, trạng thái hoặc mật khẩu tăng `users.security_version`: phiên cũ bị từ chối ở request tiếp theo qua `AccountGuardFilter`.
8. Schema chỉ tiến về trước qua Flyway, không sửa V1/V2 nếu database đã được tạo. Bản này thêm V3–V6, test trên bản sao DB trước khi migrate thật.

## 1. Ranh giới 4 công việc

| Công việc | Có mã nguồn | Tài liệu | Cần T2–T5 xác nhận |
|---|---|---|---|
| #2 Authorization | `AuthorizationPolicy`, `PermissionCatalog`, `MembershipService`, demo guard | `01_AUTHORIZATION.md`, `contracts/permission_matrix.csv` | Quy tắc chia sẻ hồ sơ, trường phụ trách, mentor/placement |
| #3 Database Core | Flyway V3–V6, JPA Audit, Status History, File Metadata | `02_DATABASE_CORE.md` | FK/entity của các team nghiệp vụ; org status semantics |
| #4 Account Lifecycle | `AccountLifecycleService`, `AccountStatusPolicy`, status history | `03_ACCOUNT_LIFECYCLE.md` | Thẩm quyền kích hoạt/khóa/thu hồi quyền |
| #5 Audit Log | `AuditService`, `AuditLogView`, `RequestIdFilter`, log denied | `04_AUDIT_STANDARD.md` | Event gửi từ service nghiệp vụ, quyền xem audit theo phạm vi |

## 2. Tích hợp với công việc #1 Authentication

- Login: `POST /api/v1/auth/login` → phiên server-side; `/auth/me` trả membership. Không đổi endpoint.
- Spring Security vẫn bảo vệ API. T1 thêm `@PreAuthorize("@authorizationPolicy.hasGlobal(authentication,'permission')")` hoặc `hasOrganization(...,role,permission)`.
- `AccountGuardFilter` kiểm tra `status` và `security_version` **trên mọi request đã xác thực**. Thu hồi role/trạng thái/sessions không cần tự xây JWT blacklist.
- Nếu organization PENDING, doanh nghiệp vẫn có tài khoản ACTIVE nhưng không được sử dụng API yêu cầu VERIFIED. Điều kiện này phải được T5 thống nhất, không được nhầm hai trạng thái.
- Các lời mời tạo tài khoản ở `AccountTokenService` và admin bootstrap vẫn hoạt động; API quản lý membership mới phục vụ trường hợp tài khoản đã tồn tại.

## 3. Luồng đầu-cuối kiểm chứng

1. Admin tạo organization cơ bản (PENDING); T5 xác thực tổ chức theo quy trình **riêng**.
2. Admin mời đại diện doanh nghiệp, user ACTIVE sau khi nhận invite; membership `(user, ENTERPRISE, orgA)`.
3. User đăng nhập và có phiên, permission `cooperation:enterprise:create` theo role ENTERPRISE.
4. T5 nhận request tạo nhu cầu; quyền yêu cầu orgA VERIFIED; request tổ chức B bị chặn dù cùng vai trò ENTERPRISE.
5. T5 gửi yêu cầu chia sẻ profile sinh viên; chỉ sau chấp thuận + consent + phạm vi chia sẻ thì adapter `ProfileVisibilityPort` trả true.
6. User thử xem profile qua T2/T5: xác thực + org permission + explicit share. Trường hợp thiếu adapter: deny.
7. Admin thu hồi membership hoặc SUSPEND user → security_version tăng, session cũ bị hủy ở request kế tiếp.
8. Audit ghi các sự kiện cấp quyền, chấp thuận T5, xem profile, đánh giá T3 và từ chối truy cập.

## 4. Danh sách quyết định còn chờ phê duyệt

- DR-01: Sinh viên có tự đăng ký hay chỉ được nhà trường mời/import? T2 + PM.
- DR-02: T5 hay cán bộ trường nào có thẩm quyền xác thực DN/duyệt yêu cầu, và có được mở quyền xem hồ sơ trước Placement không? T5 + PM.
- DR-03: Quy tắc hồ sơ SV thuộc một hay nhiều trường; ai có quyền delegated school read? T2 + T5.
- DR-04: Mentor doanh nghiệp là role ENTERPRISE có bổ sung quyền nhóm hay role riêng? T3 + T5.
- DR-05: Thời hạn chia sẻ thông tin, thu hồi consent và xóa dữ liệu; xử lý theo chính sách của UTH và quy định pháp luật. PM + pháp chế/đơn vị có thẩm quyền.
- DR-06: Quyền xem audit: chỉ Admin toàn cục hiện tại; việc cấp audit scoped cho trường phải được thiết kế, kiểm thử riêng.
- DR-07: Duyệt/đổi trạng thái organization trong bản thật thuộc T5; endpoint demo verification chỉ mở khi bật cờ môi trường.

## 5. Thứ tự đọc tài liệu

`01_AUTHORIZATION.md` → `contracts/permission_matrix.csv` → `02_DATABASE_CORE.md` → `03_ACCOUNT_LIFECYCLE.md` → `04_AUDIT_STANDARD.md` → `05_API_HANDOFF.md` → `06_USER_STORIES.md` → `07_TEST_STRATEGY.md` → `08_HANDOFF_CHECKLIST.md`.
