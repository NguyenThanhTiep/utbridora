-- ==============================================================================
-- UT.BRIDORA - QA SEED DATA FOR MATCHING & TAXONOMY VERIFICATION
-- Commit: 543b7101cfdd63ebbcb00a035ee0aaadadddbc02 (t4-taxonomy)
-- Mục đích: Nạp dữ liệu mẫu kiểm thử luồng Matching và Taxonomy (DBeaver / MySQL)
-- ==============================================================================

USE utbridora;

-- 1. SEED TAXONOMY: CATEGORY & SKILLS
-- Danh mục: Kỹ thuật Backend
INSERT INTO skill_categories (id, category_code, name, normalized_name, parent_category_id, status, version, created_at, updated_at)
VALUES 
('cat-backend-001', 'BACKEND', 'Lập trình Backend', 'lập trình backend', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Kỹ năng chuẩn (Skills)
INSERT INTO skills (id, skill_code, name, normalized_name, category_id, parent_skill_id, status, version, created_at, updated_at)
VALUES
('sk-java-001', 'JAVA', 'Java', 'java', 'cat-backend-001', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('sk-sql-001', 'SQL', 'SQL', 'sql', 'cat-backend-001', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('sk-restapi-001', 'REST_API', 'REST API', 'rest api', 'cat-backend-001', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('sk-git-001', 'GIT', 'Git', 'git', 'cat-backend-001', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('sk-python-001', 'PYTHON', 'Python', 'python', 'cat-backend-001', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('sk-django-001', 'DJANGO', 'Django', 'django', 'cat-backend-001', NULL, 'ACTIVE', 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Tên gọi khác (Skill Aliases) để kiểm tra độ phân giải (Resolver)
INSERT INTO skill_aliases (id, skill_id, alias, normalized_alias, version, created_at)
VALUES
('al-java-001', 'sk-java-001', 'Java Core', 'java core', 1, UTC_TIMESTAMP(6)),
('al-sql-001', 'sk-sql-001', 'Structured Query Language', 'structured query language', 1, UTC_TIMESTAMP(6)),
('al-rest-001', 'sk-restapi-001', 'RESTful API', 'restful api', 1, UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE alias=VALUES(alias);

-- Cập nhật phiên bản danh mục kỹ năng (Catalog State)
UPDATE taxonomy_catalog_state 
SET taxonomy_version = taxonomy_version + 1, changed_at = UTC_TIMESTAMP(6) 
WHERE id = 1;

-- 2. SEED ORGANIZATIONS
INSERT INTO organizations (id, name, type, status, created_at)
VALUES
('org-school-ut-001', 'Trường Đại học Giao thông Vận tải - ĐH GTVT', 'SCHOOL', 'ACTIVE', UTC_TIMESTAMP(6)),
('org-enterprise-corp-001', 'Tập đoàn Công nghệ Demo Corp', 'ENTERPRISE', 'ACTIVE', UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 3. SEED USERS & MEMBERSHIPS CHO 3 HỒ SƠ SINH VIÊN
-- SV001: Nguyễn Văn Khớp (Đầy đủ: Java, SQL, REST API, Git) -> MATCH
-- SV002: Trần Lệch Chuẩn (Đầy đủ: Python, Django, SQL) -> NOT_MATCH
-- SV003: Lê Thiếu Dữ Liệu (Trống thông tin kỹ năng) -> MISSING_DATA
INSERT INTO users (id, email, password_hash, display_name, status, security_version, failed_attempts, created_at, updated_at)
VALUES
('usr-student-001', 'sv001@ut.edu.vn', '$2a$10$demoHashForTestingSV001xxxxxx', 'Nguyễn Văn Khớp (SV001)', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('usr-student-002', 'sv002@ut.edu.vn', '$2a$10$demoHashForTestingSV002xxxxxx', 'Trần Lệch Chuẩn (SV002)', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
('usr-student-003', 'sv003@ut.edu.vn', '$2a$10$demoHashForTestingSV003xxxxxx', 'Lê Thiếu Dữ Liệu (SV003)', 'ACTIVE', 1, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name);

INSERT INTO memberships (id, user_id, organization_id, role, active, created_at)
VALUES
('mem-sv-001', 'usr-student-001', 'org-school-ut-001', 'STUDENT', 1, UTC_TIMESTAMP(6)),
('mem-sv-002', 'usr-student-002', 'org-school-ut-001', 'STUDENT', 1, UTC_TIMESTAMP(6)),
('mem-sv-003', 'usr-student-003', 'org-school-ut-001', 'STUDENT', 1, UTC_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE active=VALUES(active);

-- Ghi chú cho QA:
-- Bảng nghiệp vụ hồ sơ sinh viên (student_profiles) và bảng matching runs (match_runs) 
-- thuộc phạm vi module T2/T4 đang được xây dựng theo Contract BC32. 
-- Script trên hoàn tất nạp khung người dùng, tổ chức, danh mục kỹ năng chuẩn và phân quyền.

