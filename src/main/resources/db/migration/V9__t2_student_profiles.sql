-- T2-01 own student profile (Contract Pack v1.1 BC11; data dictionary G.1 line 1682 and K.2 lines 1839-1854).
-- One aggregate per student (UNIQUE user_id); major, availability and academic selections are kept per term
-- (K.2 line 1847). Collections are relations, not JSON (G line 1667).
-- V9 is a provisional branch number (DEV01 review 11.4): it also exists on other branches; T1 sets the final order.
-- term_id has no FK yet: TermReference (T5) is not on main.
CREATE TABLE student_profiles (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL,
  phone VARCHAR(20) NULL,
  cv_file_id VARCHAR(36) NULL,
  skills_declared BIT(1) NOT NULL,
  profile_version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_student_profiles_user UNIQUE (user_id),
  CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_student_profiles_cv FOREIGN KEY (cv_file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_profile_terms (
  profile_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  school_org_id VARCHAR(36) NOT NULL,
  major_code VARCHAR(64) NULL,
  availability_declared BIT(1) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (profile_id, term_id),
  CONSTRAINT fk_student_profile_terms_profile FOREIGN KEY (profile_id) REFERENCES student_profiles(id),
  CONSTRAINT fk_student_profile_terms_school FOREIGN KEY (school_org_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_profile_term_periods (
  profile_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  ordinal INT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  PRIMARY KEY (profile_id, term_id, ordinal),
  CONSTRAINT fk_student_profile_term_periods_term FOREIGN KEY (profile_id, term_id) REFERENCES student_profile_terms(profile_id, term_id),
  CONSTRAINT ck_student_profile_term_periods_order CHECK (start_date <= end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_profile_term_codes (
  profile_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  ordinal INT NOT NULL,
  code VARCHAR(64) NOT NULL,
  PRIMARY KEY (profile_id, term_id, kind, ordinal),
  CONSTRAINT fk_student_profile_term_codes_term FOREIGN KEY (profile_id, term_id) REFERENCES student_profile_terms(profile_id, term_id),
  CONSTRAINT ck_student_profile_term_codes_kind CHECK (kind IN ('ORIENTATION','SUBJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- years_practice DECIMAL(5,2): precision chosen by T2 (K.2 line 1845); the API rejects values beyond it.
CREATE TABLE student_profile_skills (
  profile_id VARCHAR(36) NOT NULL,
  skill_code VARCHAR(64) NOT NULL,
  ordinal INT NOT NULL,
  level INT NOT NULL,
  years_practice DECIMAL(5,2) NOT NULL,
  source VARCHAR(20) NOT NULL,
  note VARCHAR(1000) NULL,
  PRIMARY KEY (profile_id, skill_code),
  CONSTRAINT fk_student_profile_skills_profile FOREIGN KEY (profile_id) REFERENCES student_profiles(id),
  CONSTRAINT ck_student_profile_skills_level CHECK (level BETWEEN 1 AND 5),
  CONSTRAINT ck_student_profile_skills_years CHECK (years_practice >= 0),
  CONSTRAINT ck_student_profile_skills_source CHECK (source IN ('SELF_DECLARED','SCHOOL_VERIFIED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_custom_skills (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  profile_id VARCHAR(36) NOT NULL,
  ordinal INT NOT NULL,
  name VARCHAR(128) NOT NULL,
  category_code VARCHAR(64) NOT NULL,
  level INT NOT NULL,
  years_practice DECIMAL(5,2) NOT NULL,
  note VARCHAR(1000) NULL,
  CONSTRAINT fk_student_custom_skills_profile FOREIGN KEY (profile_id) REFERENCES student_profiles(id),
  CONSTRAINT ck_student_custom_skills_level CHECK (level BETWEEN 1 AND 5),
  CONSTRAINT ck_student_custom_skills_years CHECK (years_practice >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_projects (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  profile_id VARCHAR(36) NOT NULL,
  ordinal INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  role VARCHAR(200) NOT NULL,
  url VARCHAR(2048) NULL,
  CONSTRAINT fk_student_projects_profile FOREIGN KEY (profile_id) REFERENCES student_profiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_project_skills (
  project_id VARCHAR(36) NOT NULL,
  ordinal INT NOT NULL,
  skill_code VARCHAR(64) NOT NULL,
  PRIMARY KEY (project_id, ordinal),
  CONSTRAINT fk_student_project_skills_project FOREIGN KEY (project_id) REFERENCES student_projects(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_project_files (
  project_id VARCHAR(36) NOT NULL,
  ordinal INT NOT NULL,
  file_id VARCHAR(36) NOT NULL,
  PRIMARY KEY (project_id, ordinal),
  CONSTRAINT uk_student_project_files_file UNIQUE (project_id, file_id),
  CONSTRAINT fk_student_project_files_project FOREIGN KEY (project_id) REFERENCES student_projects(id),
  CONSTRAINT fk_student_project_files_file FOREIGN KEY (file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_certificates (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  profile_id VARCHAR(36) NOT NULL,
  ordinal INT NOT NULL,
  title VARCHAR(200) NOT NULL,
  issuer VARCHAR(200) NOT NULL,
  issued_on DATE NOT NULL,
  expires_on DATE NULL,
  file_id VARCHAR(36) NULL,
  CONSTRAINT fk_student_certificates_profile FOREIGN KEY (profile_id) REFERENCES student_profiles(id),
  CONSTRAINT fk_student_certificates_file FOREIGN KEY (file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
