-- Backward-compatible audit extension. Existing audit rows remain readable.
ALTER TABLE audit_logs
  ADD COLUMN organization_id VARCHAR(36) NULL,
  ADD COLUMN request_id VARCHAR(80) NULL,
  ADD COLUMN reason_code VARCHAR(64) NULL,
  ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'APPLICATION',
  ADD COLUMN event_version SMALLINT NOT NULL DEFAULT 1,
  ADD INDEX ix_audit_created_at (created_at),
  ADD INDEX ix_audit_org_date (organization_id, created_at),
  ADD INDEX ix_audit_resource_date (resource_type, resource_id, created_at);
-- Do not FK organization_id: audit is append-only evidence even if reference data is archived.
