-- Demand drafts, immutable revisions and idempotent commands.
-- Term/catalog references remain owned by their providers; no duplicate registry here.
CREATE TABLE demands (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  enterprise_org_id VARCHAR(36) NOT NULL,
  school_org_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  state VARCHAR(16) NOT NULL,
  validation_state VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL,
  criteria_version BIGINT NOT NULL,
  criteria_hash CHAR(64) NOT NULL,
  data_json JSON NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  soft_deleted_at DATETIME(6) NULL,
  CONSTRAINT fk_demand_enterprise FOREIGN KEY (enterprise_org_id) REFERENCES organizations(id),
  CONSTRAINT fk_demand_term FOREIGN KEY (term_id,school_org_id) REFERENCES terms(id,school_org_id),
  CONSTRAINT fk_demand_school FOREIGN KEY (school_org_id) REFERENCES organizations(id),
  CONSTRAINT ck_demand_state CHECK (state IN ('DRAFT','READY')),
  CONSTRAINT ck_demand_validation CHECK (validation_state IN ('VALID','INVALID')),
  CONSTRAINT ck_demand_version CHECK (version BETWEEN 1 AND 9007199254740991
    AND criteria_version BETWEEN 1 AND version),
  INDEX ix_demand_org_list (enterprise_org_id, created_at, id),
  INDEX ix_demand_org_term (enterprise_org_id, term_id, state, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demand_revisions (
  demand_id VARCHAR(36) NOT NULL,
  version BIGINT NOT NULL,
  criteria_version BIGINT NOT NULL,
  criteria_hash CHAR(64) NOT NULL,
  data_json JSON NOT NULL,
  snapshot_json JSON NOT NULL,
  actor_id VARCHAR(36) NOT NULL,
  operation VARCHAR(16) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (demand_id, version),
  CONSTRAINT fk_revision_demand FOREIGN KEY (demand_id) REFERENCES demands(id),
  CONSTRAINT fk_revision_actor FOREIGN KEY (actor_id) REFERENCES users(id),
  CONSTRAINT ck_revision_operation CHECK (operation IN ('CREATE','UPDATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

