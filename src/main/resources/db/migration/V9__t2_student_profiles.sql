-- T2-01 own student profile per term (Contract Pack v1.0, BC11 lines 748-805).
-- Provisional version number: V8 is already used by three other branches; DEV01 assigns the final one.
-- term_id has no FK yet because TermReference (T5) is not on main.
CREATE TABLE student_profiles (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  student_user_id VARCHAR(36) NOT NULL,
  school_org_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  major_code VARCHAR(64) NULL,
  phone VARCHAR(20) NULL,
  cv_file_id VARCHAR(36) NULL,
  skills_declared BIT(1) NOT NULL,
  availability_declared BIT(1) NOT NULL,
  skills JSON NOT NULL,
  custom_skills JSON NOT NULL,
  available_periods JSON NOT NULL,
  orientation_codes JSON NOT NULL,
  subject_codes JSON NOT NULL,
  projects JSON NOT NULL,
  certificates JSON NOT NULL,
  profile_version BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_student_profiles_student_term UNIQUE (student_user_id, term_id),
  CONSTRAINT fk_student_profiles_user FOREIGN KEY (student_user_id) REFERENCES users(id),
  CONSTRAINT fk_student_profiles_school FOREIGN KEY (school_org_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
