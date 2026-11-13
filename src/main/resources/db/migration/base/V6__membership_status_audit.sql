ALTER TABLE memberships ADD COLUMN updated_at DATETIME(6) NULL;
CREATE INDEX ix_membership_lookup ON memberships (user_id,organization_id,role,active);
-- An explicit status history facilitates supervised lifecycle reviews independently of audit retention.
CREATE TABLE account_status_history (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  actor_id VARCHAR(36) NOT NULL,
  from_status VARCHAR(32) NOT NULL,
  to_status VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NOT NULL,
  changed_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_status_user FOREIGN KEY(user_id) REFERENCES users(id),
  CONSTRAINT fk_status_actor FOREIGN KEY(actor_id) REFERENCES users(id),
  INDEX ix_status_user_date(user_id,changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
