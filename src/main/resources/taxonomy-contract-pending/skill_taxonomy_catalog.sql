-- DEV08 đã tạo bản nâng cấp tạm V8 để lưu danh mục kỹ năng, tên gọi khác và phiên bản danh mục.
-- CONTRACT_PENDING_SIGNOFF (chưa xác nhận hợp đồng): T1 cần cấp hoặc xác nhận số thứ tự trước khi hợp nhất.
-- Tệp đang nằm ngoài thư mục Flyway nên không tự chạy khi khởi động ứng dụng.
-- Dùng UUID dạng chuỗi và thời gian UTC; giữ toàn bộ dữ liệu trong một schema của ứng dụng.

CREATE TABLE taxonomy_catalog_state (
    id INT NOT NULL,
    taxonomy_version BIGINT NOT NULL,
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_taxonomy_catalog_state PRIMARY KEY (id),
    CONSTRAINT chk_taxonomy_catalog_state_singleton CHECK (id = 1),
    CONSTRAINT chk_taxonomy_catalog_version_positive CHECK (taxonomy_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO taxonomy_catalog_state (id, taxonomy_version, changed_at)
VALUES (1, 1, UTC_TIMESTAMP(6));

CREATE TABLE skill_categories (
    id VARCHAR(36) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    parent_category_id VARCHAR(36) NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_skill_categories PRIMARY KEY (id),
    CONSTRAINT uk_skill_category_code UNIQUE (category_code),
    CONSTRAINT uk_skill_category_name_key UNIQUE (normalized_name),
    CONSTRAINT fk_skill_category_parent FOREIGN KEY (parent_category_id)
        REFERENCES skill_categories (id) ON DELETE RESTRICT,
    CONSTRAINT chk_skill_category_status CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    CONSTRAINT chk_skill_category_version CHECK (version >= 1),
    INDEX ix_skill_category_parent (parent_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skills (
    id VARCHAR(36) NOT NULL,
    skill_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    normalized_name VARCHAR(128) NOT NULL,
    category_id VARCHAR(36) NOT NULL,
    parent_skill_id VARCHAR(36) NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_skills PRIMARY KEY (id),
    CONSTRAINT uk_skill_code UNIQUE (skill_code),
    CONSTRAINT uk_skill_name_key UNIQUE (normalized_name),
    CONSTRAINT fk_skill_category FOREIGN KEY (category_id)
        REFERENCES skill_categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_skill_parent FOREIGN KEY (parent_skill_id)
        REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT chk_skill_status CHECK (status IN ('ACTIVE', 'DEPRECATED')),
    CONSTRAINT chk_skill_version CHECK (version >= 1),
    INDEX ix_skill_category (category_id),
    INDEX ix_skill_parent (parent_skill_id),
    INDEX ix_skill_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_aliases (
    id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(36) NOT NULL,
    alias VARCHAR(128) NOT NULL,
    normalized_alias VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_skill_aliases PRIMARY KEY (id),
    CONSTRAINT uk_skill_alias_normalized UNIQUE (normalized_alias),
    CONSTRAINT fk_skill_alias_skill FOREIGN KEY (skill_id)
        REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT chk_skill_alias_version CHECK (version >= 1),
    INDEX ix_skill_alias_skill (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
