-- Common C.0/G.1 operation ledger. Reservation, domain effect and DONE commit together.
CREATE TABLE operation_records (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  actor_id VARCHAR(36) NULL,
  actor_scope_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_code VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  idempotency_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  state VARCHAR(16) NOT NULL,
  effect_ref VARCHAR(36) NULL,
  response_status INT NULL,
  response_json JSON NULL,
  response_expires_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_operation_scope UNIQUE (actor_scope_key, operation_code, idempotency_key),
  CONSTRAINT fk_operation_actor FOREIGN KEY (actor_id) REFERENCES users(id),
  CONSTRAINT ck_operation_state CHECK (state IN ('IN_PROGRESS', 'DONE')),
  -- The response cache may be purged while the durable operation/effect reference remains.
  CONSTRAINT ck_operation_response CHECK (
    (response_json IS NULL AND response_expires_at IS NULL) OR
    (response_status BETWEEN 200 AND 299 AND response_json IS NOT NULL AND response_expires_at IS NOT NULL)),
  INDEX ix_operation_response_expiry (response_expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
