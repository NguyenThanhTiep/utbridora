-- Write is granted only by a reviewed, named Leader T1 + T4 TAX manifest.
INSERT INTO permissions (code,resource,action,description) VALUES
 ('taxonomy:skills:read','taxonomy','skills_read','Read canonical skills and aliases'),
 ('taxonomy:skills:write','taxonomy','skills_write','Named TAX editors only');
INSERT INTO role_permissions (role,permission_code) VALUES
 ('STUDENT','taxonomy:skills:read'),('ENTERPRISE','taxonomy:skills:read'),
 ('SCHOOL_STAFF','taxonomy:skills:read'),('ADMIN','taxonomy:skills:read');
-- No editor is seeded. No ADMIN API can self-grant TAX. Trusted manifest import
-- must bump the user's security_version and audit capability grant/revoke atomically.
CREATE TABLE taxonomy_editor_assignments (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 decision_ref VARCHAR(128) NOT NULL,
 approved_by_t1 VARCHAR(36) NOT NULL,
 approved_by_t4 VARCHAR(36) NOT NULL,
 valid_from DATETIME(6) NOT NULL,
 valid_until DATETIME(6) NOT NULL,
 revoked_at DATETIME(6) NULL,
 CONSTRAINT fk_tax_editor_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT fk_tax_editor_t1 FOREIGN KEY (approved_by_t1) REFERENCES users(id),
 CONSTRAINT fk_tax_editor_t4 FOREIGN KEY (approved_by_t4) REFERENCES users(id),
 CONSTRAINT chk_tax_editor_period CHECK (valid_from < valid_until),
 CONSTRAINT chk_tax_editor_decision CHECK (CHAR_LENGTH(TRIM(decision_ref)) > 0),
 INDEX ix_tax_editor_current(user_id,revoked_at,valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
