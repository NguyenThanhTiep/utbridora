-- MySQL binary JSON normalizes fractional numbers to floating point. Keep the
-- exact JSON number text for yearsPractice/minYearsPractice, snapshots and
-- idempotent responses. JSON functions still work and JSON_VALID keeps the old
-- structural constraint. ALTER preserves the serialized contents and hashes of
-- existing immutable documents; do not UPDATE/rebuild any revision or snapshot.
ALTER TABLE operation_records MODIFY response_json LONGTEXT NULL,
  ADD CONSTRAINT ck_exact_operation_response CHECK(response_json IS NULL OR JSON_VALID(response_json));
ALTER TABLE student_profile_snapshots MODIFY data_json LONGTEXT NOT NULL,
  ADD CONSTRAINT ck_exact_profile_snapshot CHECK(JSON_VALID(data_json));
ALTER TABLE demands MODIFY data_json LONGTEXT NOT NULL,
  ADD CONSTRAINT ck_exact_demand_data CHECK(JSON_VALID(data_json));
ALTER TABLE demand_revisions MODIFY data_json LONGTEXT NOT NULL, MODIFY snapshot_json LONGTEXT NOT NULL,
  ADD CONSTRAINT ck_exact_revision_data CHECK(JSON_VALID(data_json)),
  ADD CONSTRAINT ck_exact_revision_snapshot CHECK(JSON_VALID(snapshot_json));
ALTER TABLE cooperation_history MODIFY data_json LONGTEXT NOT NULL,
  ADD CONSTRAINT ck_exact_cooperation_history CHECK(JSON_VALID(data_json));
