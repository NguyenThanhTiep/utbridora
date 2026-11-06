-- Term-only integration from Dev10 commit 7353971; verification workflow remains with its owner.
CREATE TABLE terms (
  id             VARCHAR(36)   NOT NULL PRIMARY KEY,
  school_org_id  VARCHAR(36)   NOT NULL,
  term_code      VARCHAR(64)   NOT NULL,
  start_date     DATE          NOT NULL,
  end_date       DATE          NOT NULL,
  status         VARCHAR(32)   NOT NULL DEFAULT 'DRAFT',
  version        BIGINT        NOT NULL DEFAULT 0,
  created_at     DATETIME(6)   NOT NULL,
  updated_at     DATETIME(6)   NOT NULL,
  CONSTRAINT fk_terms_school FOREIGN KEY (school_org_id) REFERENCES organizations(id),
  CONSTRAINT uk_terms_school_code UNIQUE (school_org_id, term_code),
  INDEX ix_terms_school_status (school_org_id, status, start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
