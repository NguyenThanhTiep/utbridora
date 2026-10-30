# Database Core

MySQL **8.4**, InnoDB, utf8mb4. Identity dùng UUID dạng `VARCHAR(36)`. Thời gian nghiệp vụ là `Instant` UTC, cột `DATETIME(6)`. JDBC session lưu epoch milliseconds. JPA chỉ `ddl-auto=validate`; không dùng Hibernate để nâng cấp schema.

## Schema thực tế

| Bảng | Khóa và quan hệ | Ràng buộc chính |
|---|---|---|
| users | PK id; email unique | Một identity cho nhiều vai trò; BCrypt; security_version |
| organizations | PK id | type UNIVERSITY/ENTERPRISE; status PENDING/VERIFIED/SUSPENDED |
| memberships | PK id; FK user_id, organization_id nullable | unique(user_id,role,scope_key), scope_key generated từ org hoặc GLOBAL |
| permissions | PK code | unique(resource,action) |
| role_permissions | PK(role,permission_code), FK permission | Role enum Java, không có bảng roles/user_roles |
| one_time_tokens | PK id; FK user_id, organization_id | token_hash SHA-256 unique; expires_at, consumed_at |
| files | PK id; FK owner_user_id, organization_id | storage_key unique; size >= 0; checksum/scan/classification |
| audit_logs | PK id; FK actor_id nullable | metadata tối thiểu; organization_id không FK để giữ chứng cứ |
| account_status_history | PK id; FK user_id, actor_id | from/to/reason/time |
| SPRING_SESSION | PK PRIMARY_ID; SESSION_ID unique | index expiry/principal; Spring Session quản lý |
| SPRING_SESSION_ATTRIBUTES | PK(session primary id, attribute name) | FK session ON DELETE CASCADE |
| core_locks | PK lock_name | Một row GLOBAL_ADMIN, serial hóa mutation quản trị |

`files` hiện chỉ có metadata. Không có API upload/download; không được suy ra quyền đọc file từ `storage_key`, ADMIN hoặc scan_status đơn thuần.

## Flyway và tương thích

Giữ nguyên byte của V1–V6 được cung cấp. V7 thêm `core_locks` và index `users(status,created_at)`, token expiry, membership global-role. Không xóa/gộp dữ liệu cũ tự động. Trước nâng cấp phải chạy SQL preflight trong `10_MIGRATION_PREFLIGHT.md`, sao lưu và thử trên bản sao. Nếu V3 gặp membership trùng, dừng để xử lý có review; không chạy repair/clean để bỏ lỗi.

Các field enum JPA dùng JDBC VARCHAR rõ ràng để tương thích cột VARCHAR kế thừa thay vì để Hibernate tự chọn native enum. Quan hệ user–org–role được kiểm tra tại `MembershipService`; FK chỉ bảo vệ tồn tại tham chiếu. Scope ADMIN global không đại diện cho mọi org.

## Lock và tính nhất quán

- Grant/revoke membership, status và revoke-session: khóa row `GLOBAL_ADMIN` trước khi đọc target, kiểm tra lại actor/quyền sau khi chờ lock, rồi khóa target user. Invariant admin cuối được bảo vệ giữa các instance dùng cùng database.
- Đổi mật khẩu và login thành công khóa user; kiểm tra security_version để chặn xác thực dựa trên credential cũ đang đua với reset/revoke.
- Reset: lookup định danh token, khóa user, kiểm tra token, consume bằng UPDATE có điều kiện `consumed_at IS NULL AND expires_at > now`. Không dựa vào snapshot đọc cũ của MySQL REPEATABLE_READ.
- Credential/status/membership cùng audit SUCCESS nằm trong một transaction; audit hỏng khiến mutation rollback.

Không sửa trực tiếp membership/status qua SQL khi vận hành: sẽ bỏ qua guard, version và audit. T5 cần thay đổi organization qua service nghiệp vụ có phân quyền; không đồng nhất organization status với users.status.

## Kiểm chứng

`mvn verify` chạy MySQL 8.4 Testcontainers. `CorePlatformIT` khởi động schema sạch, Flyway V1–V7, JPA validate và JDBC session; `MigrationUpgradeIT` nâng V2 có dữ liệu mẫu lên V7, validate, chạy lại no-op, kiểm tra unique global membership và FK. Kết quả thực thi cụ thể nằm trong `TEST_REPORT.md`.

Chưa đo query plan/hiệu năng dữ liệu lớn. API danh sách user có bounded N+1 membership (tối đa 100 user/trang), cần đo trước khi tăng tải. Backup/restore và retention phải được kiểm chứng trên staging riêng.
