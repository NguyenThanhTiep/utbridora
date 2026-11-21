-- Additive T3 CP-WS1/4/5 + separately approved FR-27 hiring-interest extension.
-- Role entries are ceilings only. No assignment, delegation, user or live-data fixtures are seeded.
CREATE TABLE internship_placement_previews (
 id VARCHAR(36) NOT NULL PRIMARY KEY, cohort_id VARCHAR(36) NOT NULL, actor_id VARCHAR(36) NOT NULL,
 cohort_version BIGINT NOT NULL, file_hash CHAR(64) NOT NULL, rows_json JSON NOT NULL, expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(cohort_id) REFERENCES internship_cohorts(id), FOREIGN KEY(actor_id) REFERENCES users(id),
 INDEX ix_placement_preview_expiry(expires_at)
) ENGINE=InnoDB;
CREATE TABLE internship_enterprise_quality_templates (
 id VARCHAR(36) NOT NULL, version BIGINT NOT NULL, school_org_id VARCHAR(36) NOT NULL, term_id VARCHAR(36) NOT NULL,
 template_json JSON NOT NULL, PRIMARY KEY(id,version), FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 CHECK(version BETWEEN 1 AND 9007199254740991), INDEX ix_quality_template_scope(school_org_id,term_id,id,version)
) ENGINE=InnoDB;
CREATE TABLE internship_enterprise_feedback (
 id VARCHAR(36) NOT NULL PRIMARY KEY, school_org_id VARCHAR(36) NOT NULL, enterprise_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL, template_id VARCHAR(36) NOT NULL, template_version BIGINT NOT NULL,
 input_json JSON NOT NULL, created_by VARCHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id), FOREIGN KEY(enterprise_org_id) REFERENCES organizations(id),
 FOREIGN KEY(template_id,template_version) REFERENCES internship_enterprise_quality_templates(id,version), FOREIGN KEY(created_by) REFERENCES users(id),
 INDEX ix_feedback_scope(school_org_id,term_id,enterprise_org_id,created_at)
) ENGINE=InnoDB;
CREATE TABLE internship_coordination_issues (
 id VARCHAR(36) NOT NULL PRIMARY KEY, case_id VARCHAR(36) NOT NULL, category VARCHAR(32) NOT NULL,
 description TEXT NOT NULL, owner_user_id VARCHAR(36) NOT NULL, state VARCHAR(16) NOT NULL, version BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES internship_cases(id), FOREIGN KEY(owner_user_id) REFERENCES users(id),
 CHECK(category IN ('DELAY','COMMUNICATION','OTHER')), CHECK(state IN ('OPEN','IN_PROGRESS','RESOLVED')),
 CHECK(version BETWEEN 1 AND 9007199254740991), INDEX ix_issues_case_state(case_id,state,created_at,id)
) ENGINE=InnoDB;
CREATE TABLE internship_coordination_issue_history (
 issue_id VARCHAR(36) NOT NULL, version BIGINT NOT NULL, state VARCHAR(16) NOT NULL, actor_id VARCHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL, PRIMARY KEY(issue_id,version), FOREIGN KEY(issue_id) REFERENCES internship_coordination_issues(id), FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE internship_case_closures (
 case_id VARCHAR(36) NOT NULL PRIMARY KEY, case_version BIGINT NOT NULL, proof_json JSON NOT NULL,
 actor_id VARCHAR(36) NOT NULL, delegation_id VARCHAR(36) NOT NULL, delegation_version BIGINT NOT NULL, closed_at DATETIME(6) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES internship_cases(id), FOREIGN KEY(actor_id) REFERENCES users(id), FOREIGN KEY(delegation_id) REFERENCES school_delegations(id)
) ENGINE=InnoDB;
CREATE TABLE internship_case_closed_outbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY, case_id VARCHAR(36) NOT NULL, aggregate_version BIGINT NOT NULL,
 operation_id VARCHAR(36) NOT NULL UNIQUE, payload_json JSON NOT NULL, state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0, next_attempt_at DATETIME(6) NOT NULL, last_reason VARCHAR(64) NULL,
 FOREIGN KEY(case_id) REFERENCES internship_cases(id), UNIQUE(case_id,aggregate_version),
 INDEX ix_case_closed_dispatch(state,next_attempt_at,event_id), CHECK(state IN ('PENDING','DONE','DLQ'))
) ENGINE=InnoDB;
CREATE TABLE case_closed_inbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY, case_id VARCHAR(36) NOT NULL, case_version BIGINT NOT NULL,
 placement_id VARCHAR(36) NOT NULL, placement_version BIGINT NOT NULL, outcome VARCHAR(32) NOT NULL,
 applied_at DATETIME(6) NOT NULL, UNIQUE(case_id,case_version), FOREIGN KEY(placement_id) REFERENCES placements(id)
) ENGINE=InnoDB;
CREATE TABLE internship_hiring_interests (
 case_id VARCHAR(36) NOT NULL PRIMARY KEY, interested BOOLEAN NOT NULL, note TEXT NULL, version BIGINT NOT NULL,
 updated_at DATETIME(6) NOT NULL, updated_by VARCHAR(36) NOT NULL, FOREIGN KEY(case_id) REFERENCES internship_cases(id), FOREIGN KEY(updated_by) REFERENCES users(id),
 CHECK(version BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB;
CREATE TABLE internship_hiring_interest_history (
 case_id VARCHAR(36) NOT NULL, version BIGINT NOT NULL, interested BOOLEAN NOT NULL, note TEXT NULL,
 actor_id VARCHAR(36) NOT NULL, created_at DATETIME(6) NOT NULL, PRIMARY KEY(case_id,version), FOREIGN KEY(case_id) REFERENCES internship_cases(id), FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
INSERT IGNORE INTO permissions(code,resource,action,description) VALUES
 ('t3:cases:close','internship_case','close','School-authorized normal or early closure'),
 ('t3:enterprise_quality:school:write','enterprise_quality_evaluation','write','School-scoped partner rubric'),
 ('t3:coordination:manage','coordination_issue','manage','Current school/HR case coordination'),
 ('t3:hiring_interest:manage','hiring_interest','manage','Current assigned HR interest marker, not a hiring decision');
INSERT IGNORE INTO role_permissions(role,permission_code) VALUES
 ('SCHOOL_STAFF','t3:cases:close'),('SCHOOL_STAFF','t3:enterprise_quality:school:write'),
 ('SCHOOL_STAFF','t3:coordination:manage'),('ENTERPRISE','t3:coordination:manage'),('ENTERPRISE','t3:hiring_interest:manage');
