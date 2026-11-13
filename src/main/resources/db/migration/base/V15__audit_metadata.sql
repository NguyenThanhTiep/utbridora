-- Typed producer allowlists control metadata; legacy audit rows remain valid.
ALTER TABLE audit_logs ADD COLUMN metadata_json JSON NULL;
