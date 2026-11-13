-- BC11/K.2 has no wire precision/scale ceiling. Store the exact BigDecimal
-- representation; these columns are not used for numeric SQL filtering/ranking.
-- Existing decimals convert exactly; old migration checksums remain unchanged.
ALTER TABLE student_profile_skills DROP CHECK ck_student_profile_skills_years;
ALTER TABLE student_custom_skills DROP CHECK ck_student_custom_skills_years;
ALTER TABLE student_profile_skills MODIFY years_practice LONGTEXT NOT NULL;
ALTER TABLE student_custom_skills MODIFY years_practice LONGTEXT NOT NULL;
ALTER TABLE student_profile_skills ADD CONSTRAINT ck_student_profile_skills_years
  CHECK (REGEXP_LIKE(years_practice, '^(0|[1-9][0-9]*)([.][0-9]+)?([eE][+-]?[0-9]+)?$'));
ALTER TABLE student_custom_skills ADD CONSTRAINT ck_student_custom_skills_years
  CHECK (REGEXP_LIKE(years_practice, '^(0|[1-9][0-9]*)([.][0-9]+)?([eE][+-]?[0-9]+)?$'));
