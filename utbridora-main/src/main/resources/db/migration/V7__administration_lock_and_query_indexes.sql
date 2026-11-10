-- Serialize changes that can remove the last active global administrator.
-- Never use JVM-only locks: this row also coordinates multiple backend instances.
CREATE TABLE core_locks (
  lock_name VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO core_locks (lock_name) VALUES ('GLOBAL_ADMIN');

CREATE INDEX ix_users_status_created ON users (status, created_at);
CREATE INDEX ix_tokens_expiry ON one_time_tokens (expires_at);
CREATE INDEX ix_memberships_global_role ON memberships (role, organization_id, active, user_id);
