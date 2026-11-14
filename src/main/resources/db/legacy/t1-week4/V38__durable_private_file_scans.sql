-- Forward-only: preserve existing bindings, binary hashes and CLEAN/REJECTED states.
-- Legacy PENDING rows are eligible for an integrity-checked scan, never auto-CLEAN.
ALTER TABLE files
  ADD COLUMN scan_attempt INT NOT NULL DEFAULT 0,
  ADD COLUMN scanner_reason_code VARCHAR(64) NULL,
  ADD COLUMN scan_next_attempt_at DATETIME(6) NULL,
  ADD COLUMN scan_lease_id VARCHAR(36) NULL,
  ADD COLUMN scan_lease_until DATETIME(6) NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
  ADD CONSTRAINT ck_file_scan_attempt CHECK (scan_attempt BETWEEN 0 AND 4),
  ADD CONSTRAINT ck_file_scan_lease CHECK ((scan_lease_id IS NULL) = (scan_lease_until IS NULL)),
  ADD CONSTRAINT ck_file_version CHECK (version BETWEEN 1 AND 9007199254740991),
  ADD INDEX ix_file_scan_due (scan_status,deleted_at,scan_next_attempt_at,scan_lease_until);
