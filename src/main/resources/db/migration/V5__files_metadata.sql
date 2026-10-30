-- Metadata only. Binary content belongs in private object storage; NEVER expose storage_key publicly.
CREATE TABLE files (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  owner_user_id VARCHAR(36) NOT NULL,
  organization_id VARCHAR(36) NULL,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(150) NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  classification VARCHAR(32) NOT NULL,
  scan_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME(6) NOT NULL,
  deleted_at DATETIME(6) NULL,
  CONSTRAINT fk_files_owner FOREIGN KEY (owner_user_id) REFERENCES users(id),
  CONSTRAINT fk_files_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
  CONSTRAINT chk_file_size CHECK (size_bytes >= 0),
  INDEX ix_files_owner (owner_user_id,created_at),
  INDEX ix_files_org (organization_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
