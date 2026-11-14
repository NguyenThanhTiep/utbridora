-- Synthetic QUALITY/PROCESS fixture for an explicitly selected PRIVATE development database.
-- Not a Flyway migration. Never run against the shared/demo/production database.
-- Before execution, set all three UUID variables to the target school's existing term and
-- a NEW template UUID. This file does not create identity, Case, Placement or permissions.
-- SET @t310_school_org_id = '...';
-- SET @t310_term_id = '...';
-- SET @t310_template_id = '...';

INSERT INTO internship_rubric_templates(id,template_version,school_org_id,term_id,template_json)
SELECT @t310_template_id,1,t.school_org_id,t.id,
 JSON_OBJECT('templateId',@t310_template_id,'templateVersion',1,'criteria',JSON_ARRAY(
   JSON_OBJECT('criterionCode','QUALITY','label','Synthetic quality criterion','weight',40),
   JSON_OBJECT('criterionCode','PROCESS','label','Synthetic process criterion','weight',60)))
FROM terms t
WHERE t.id=@t310_term_id AND t.school_org_id=@t310_school_org_id
  AND @t310_template_id REGEXP '^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$';

-- Expect exactly one row for a valid term/school; otherwise fix the fixture inputs.
-- Repeating with the same ID/version must fail uniqueness: never overwrite a used template.
SELECT ROW_COUNT() AS fixture_rows_inserted;
