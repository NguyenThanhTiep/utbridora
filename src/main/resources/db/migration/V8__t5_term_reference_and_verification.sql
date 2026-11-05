-- T5: TermReference and School-Enterprise Verification
-- Owner: T5 cooperation module. FK to organizations (T1).
-- V8 follows V7 sequentially; do not edit V1-V7.

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

-- School-Enterprise verification: one current open attempt per enterprise+school pair.
-- previousVerificationId links attempts for history; no soft-delete of approved records.
CREATE TABLE enterprise_verifications (
  id                       VARCHAR(36)  NOT NULL PRIMARY KEY,
  enterprise_org_id        VARCHAR(36)  NOT NULL,
  school_org_id            VARCHAR(36)  NOT NULL,
  previous_verification_id VARCHAR(36)  NULL,
  state                    VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  submission_no            INT          NOT NULL DEFAULT 0,
  -- contact / registration data (staging only; no PII from users table)
  registration_code        VARCHAR(64)  NULL,
  registered_name          VARCHAR(200) NULL,
  address                  VARCHAR(500) NULL,
  contact_name             VARCHAR(150) NULL,
  contact_email            VARCHAR(254) NULL,
  contact_phone            VARCHAR(20)  NULL,
  missing_fields           VARCHAR(500) NULL,  -- comma-separated field names set by reviewer
  decision_ref             VARCHAR(120) NULL,
  submitted_at             DATETIME(6)  NULL,
  decided_at               DATETIME(6)  NULL,
  version                  BIGINT       NOT NULL DEFAULT 0,
  created_at               DATETIME(6)  NOT NULL,
  updated_at               DATETIME(6)  NOT NULL,
  CONSTRAINT fk_ev_enterprise FOREIGN KEY (enterprise_org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ev_school     FOREIGN KEY (school_org_id)     REFERENCES organizations(id),
  CONSTRAINT fk_ev_previous   FOREIGN KEY (previous_verification_id) REFERENCES enterprise_verifications(id),
  -- Only one open (non-terminal) attempt per enterprise+school allowed at a time.
  -- Terminal states: APPROVED, REJECTED. Open states: DRAFT, SUBMITTED, NEEDS_CHANGES.
  INDEX ix_ev_enterprise_school (enterprise_org_id, school_org_id, state),
  INDEX ix_ev_school_state      (school_org_id, state, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
