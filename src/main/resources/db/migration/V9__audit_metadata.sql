-- Additive metadata storage; existing legacy events remain unchanged.
ALTER TABLE audit_logs ADD COLUMN metadata_json JSON NULL;
