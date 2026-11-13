-- Persist matching audit fields directly for indexed audit queries and statistics.
ALTER TABLE audit_logs
  ADD COLUMN demand_id VARCHAR(36) NULL,
  ADD COLUMN ip_address VARCHAR(45) NULL,
  ADD INDEX ix_audit_demand_date (demand_id, created_at);

-- Deliberately omit a demand foreign key so audit evidence survives archival changes.
