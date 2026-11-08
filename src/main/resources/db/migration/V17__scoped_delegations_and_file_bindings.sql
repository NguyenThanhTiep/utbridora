-- Read gates for reviewed school delegation manifests and owner-issued file bindings.
-- No root delegate, TAX editor or CLEAN file is fabricated by this migration.
CREATE TABLE school_delegations (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 user_id VARCHAR(36) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 parent_id VARCHAR(36) NULL,
 granted_by VARCHAR(36) NOT NULL,
 decision_ref VARCHAR(120) NOT NULL,
 profile_read_fields_json JSON NOT NULL,
 valid_from DATETIME(6) NOT NULL,
 valid_until DATETIME(6) NOT NULL,
 active BOOLEAN NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 CONSTRAINT fk_sd_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_sd_grantor FOREIGN KEY(granted_by) REFERENCES users(id),
 CONSTRAINT fk_sd_term_school FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 CONSTRAINT fk_sd_parent FOREIGN KEY(parent_id) REFERENCES school_delegations(id),
 CONSTRAINT ck_sd_period CHECK(valid_from < valid_until),
 CONSTRAINT ck_sd_version CHECK(version BETWEEN 1 AND 9007199254740991),
 CONSTRAINT ck_sd_decision CHECK(CHAR_LENGTH(TRIM(decision_ref))>0),
 INDEX ix_sd_current(user_id,school_org_id,term_id,active,valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE school_delegation_permissions (
 assignment_id VARCHAR(36) NOT NULL,
 permission_code VARCHAR(100) NOT NULL,
 PRIMARY KEY(assignment_id,permission_code),
 CONSTRAINT fk_sd_permission_assignment FOREIGN KEY(assignment_id) REFERENCES school_delegations(id),
 CONSTRAINT fk_sd_permission_code FOREIGN KEY(permission_code) REFERENCES permissions(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO permissions(code,resource,action,description) VALUES
 ('enterprise_verification:enterprise:manage','enterprise_verification','enterprise_manage','Own enterprise verification'),
 ('enterprise_verification:school:review','enterprise_verification','school_review','Current delegated school reviewer'),
 ('t3:coordinator:confirm','t3_coordinator','confirm','Current delegated school coordinator confirmation');
INSERT INTO role_permissions(role,permission_code) VALUES
 ('ENTERPRISE','enterprise_verification:enterprise:manage'),
 ('SCHOOL_STAFF','enterprise_verification:school:review'),
 ('SCHOOL_STAFF','t3:coordinator:confirm');

CREATE TABLE file_bindings (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 file_id VARCHAR(36) NOT NULL,
 binding_kind VARCHAR(64) NOT NULL,
 resource_id VARCHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_file_binding(file_id,binding_kind,resource_id),
 CONSTRAINT fk_binding_file FOREIGN KEY(file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
