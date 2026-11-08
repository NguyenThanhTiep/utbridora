-- Canonical T5 TermReference foundation, extracted from the owner branch for T2 integration.
-- Verification workflow remains on its task branch until its guards/wire contract are reviewed.
CREATE TABLE terms (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  school_org_id VARCHAR(36) NOT NULL,
  term_code VARCHAR(64) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  version BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_terms_school FOREIGN KEY (school_org_id) REFERENCES organizations(id),
  CONSTRAINT uk_terms_school_code UNIQUE (school_org_id,term_code),
  CONSTRAINT uk_terms_id_school UNIQUE (id,school_org_id),
  CONSTRAINT uk_terms_id_school_code UNIQUE (id,school_org_id,term_code),
  CONSTRAINT ck_terms_dates CHECK (start_date<end_date),
  CONSTRAINT ck_terms_status CHECK (status IN ('DRAFT','ACTIVE','CLOSED')),
  CONSTRAINT ck_terms_version CHECK (version BETWEEN 0 AND 9007199254740991),
  INDEX ix_terms_school_status (school_org_id,status,start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
