-- liên quan đến việc thêm lọc svien theo gpa là MVP: Backfill ngưỡng GPA null cho Demand/revision được tạo trước tính năng này.
UPDATE demands
SET data_json = JSON_SET(data_json, '$.required.minGpa', NULL)
WHERE JSON_CONTAINS_PATH(data_json, 'one', '$.required.minGpa') = 0;

UPDATE demand_revisions
SET data_json = JSON_SET(data_json, '$.required.minGpa', NULL)
WHERE JSON_CONTAINS_PATH(data_json, 'one', '$.required.minGpa') = 0;

UPDATE demand_revisions
SET snapshot_json = JSON_SET(snapshot_json, '$.required.minGpa', NULL)
WHERE JSON_CONTAINS_PATH(snapshot_json, 'one', '$.required.minGpa') = 0;
