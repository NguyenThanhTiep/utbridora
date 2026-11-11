CREATE TABLE users (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(150) NOT NULL,
  status VARCHAR(32) NOT NULL,
  security_version BIGINT NOT NULL DEFAULT 0,
  failed_attempts INT NOT NULL DEFAULT 0,
  locked_until DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE organizations (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE memberships (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  organization_id VARCHAR(36) NULL,
  role VARCHAR(32) NOT NULL,
  active BIT(1) NOT NULL DEFAULT b'1',
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_membership_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
  INDEX ix_memberships_user (user_id),
  INDEX ix_memberships_org_role (organization_id,role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE one_time_tokens (
  id VARCHAR(36) PRIMARY KEY,
  token_hash CHAR(64) NOT NULL UNIQUE,
  purpose VARCHAR(32) NOT NULL,
  user_id VARCHAR(36) NULL,
  email VARCHAR(254) NOT NULL,
  invite_role VARCHAR(32) NULL,
  organization_id VARCHAR(36) NULL,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_token_org FOREIGN KEY (organization_id) REFERENCES organizations(id),
  INDEX ix_token_user_purpose (user_id, purpose)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_logs (
  id VARCHAR(36) PRIMARY KEY,
  actor_id VARCHAR(36) NULL,
  action VARCHAR(90) NOT NULL,
  resource_type VARCHAR(80) NOT NULL,
  resource_id VARCHAR(36) NULL,
  result VARCHAR(20) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users(id),
  INDEX ix_audit_actor_date (actor_id, created_at),
  INDEX ix_audit_action_date (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
