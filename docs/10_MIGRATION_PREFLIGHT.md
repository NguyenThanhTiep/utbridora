> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [DATABASE.md](DATABASE.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Checklist trước khi migrate MySQL V3–V6

**Quan trọng:** mã nguồn này kế thừa V1/V2 và không được dùng để migrate trực tiếp database production chưa sao lưu.

## 1. Kiểm tra trước khi chạy

```sql
-- Tìm membership bị trùng trong dữ liệu cũ, vì V3 sẽ thêm UNIQUE constraint.
SELECT user_id, role, COALESCE(organization_id, 'GLOBAL') AS scope_key, COUNT(*) AS n
FROM memberships
GROUP BY user_id, role, COALESCE(organization_id, 'GLOBAL')
HAVING COUNT(*) > 1;

-- Tìm membership role không thuộc enum Java hiện tại.
SELECT DISTINCT role FROM memberships
WHERE role NOT IN ('STUDENT','ENTERPRISE','SCHOOL_STAFF','ADMIN');

-- Dữ liệu thiếu quan hệ cơ bản.
SELECT m.id FROM memberships m
LEFT JOIN users u ON u.id = m.user_id
WHERE u.id IS NULL;
```

Nếu có membership trùng, phân tích nguồn gốc và gộp/thu hồi theo chính sách trước khi chạy V3. Không xóa tự động do có thể mất bằng chứng lịch sử. Spring Boot Flyway sẽ báo lỗi và dừng nếu migration không áp dụng thành công.

## 2. Sau khi migrate

```sql
SHOW TABLES;
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

SELECT * FROM permissions ORDER BY code;
SELECT role, COUNT(*) FROM role_permissions GROUP BY role;
SHOW INDEX FROM memberships;
SHOW INDEX FROM audit_logs;
```

Kiểm tra bảng: users, organizations, memberships, one_time_tokens, audit_logs, SPRING_SESSION, SPRING_SESSION_ATTRIBUTES, permissions, role_permissions, files, account_status_history. Xác nhận role-permission được seed và `scope_key` unique index hoạt động.

## 3. Những gì chưa chứng minh

- Tương thích byte-level với mọi DB bản cũ ngoài file V1/V2 được cung cấp.
- Tính hợp lệ Hibernate `ddl-auto: validate` cho mọi table trên MySQL 8.4 (chưa khởi động được backend trong môi trường tạo ZIP).
- Locking last admin trong concurrent transaction.
- Hiệu năng audit log khi tải cao, chính sách lưu trữ/xóa data thật.

Chạy migration trên bản sao staging trước khi nâng cấp dữ liệu thật.
