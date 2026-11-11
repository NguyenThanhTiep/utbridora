CREATE TABLE internship_projection_locks (
 placement_id VARCHAR(36) NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE internship_cases (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 placement_id VARCHAR(36) NOT NULL UNIQUE,
 student_user_id VARCHAR(36) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 enterprise_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL,
 version BIGINT NOT NULL,
 last_applied_version BIGINT NOT NULL,
 FOREIGN KEY(student_user_id) REFERENCES users(id),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(enterprise_org_id) REFERENCES organizations(id),
 CHECK(state IN ('WAITING_START','IN_PROGRESS','COMPLETED','TERMINATED','CANCELLED')),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 INDEX ix_case_scope(enterprise_org_id,school_org_id,term_id)
) ENGINE=InnoDB;
CREATE TABLE placement_inbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY,
 placement_id VARCHAR(36) NOT NULL,
 aggregate_version BIGINT NOT NULL,
 event_type VARCHAR(32) NOT NULL,
 applied_version BIGINT NOT NULL,
 outcome VARCHAR(32) NOT NULL,
 received_at DATETIME(6) NOT NULL,
 UNIQUE(placement_id,aggregate_version,event_type)
) ENGINE=InnoDB;
-- Technical projection audit, intentionally no invented business action code in the C.0 registry.
CREATE TABLE internship_case_projection_history (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 case_id VARCHAR(36) NOT NULL,
 event_id VARCHAR(36) NOT NULL,
 delivery_operation_id VARCHAR(36) NOT NULL,
 from_state VARCHAR(32) NULL,
 to_state VARCHAR(32) NOT NULL,
 case_version BIGINT NOT NULL,
 source_version BIGINT NOT NULL,
 received_at DATETIME(6) NOT NULL,
 FOREIGN KEY(case_id) REFERENCES internship_cases(id)
) ENGINE=InnoDB;
CREATE TABLE internship_cohorts (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 enterprise_org_id VARCHAR(36) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 name VARCHAR(200) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 UNIQUE(enterprise_org_id,school_org_id,term_id,code),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(enterprise_org_id) REFERENCES organizations(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE internship_groups (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 cohort_id VARCHAR(36) NOT NULL,
 code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 UNIQUE(cohort_id,code),
 UNIQUE(id,cohort_id),
 FOREIGN KEY(cohort_id) REFERENCES internship_cohorts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE internship_cohort_cases (
 cohort_id VARCHAR(36) NOT NULL,
 case_id VARCHAR(36) NOT NULL,
 group_id VARCHAR(36) NULL,
 PRIMARY KEY(cohort_id,case_id),
 UNIQUE(case_id),
 FOREIGN KEY(cohort_id) REFERENCES internship_cohorts(id),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 FOREIGN KEY(group_id,cohort_id) REFERENCES internship_groups(id,cohort_id)
) ENGINE=InnoDB;
CREATE TABLE internship_group_history (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 cohort_id VARCHAR(36) NOT NULL,
 case_id VARCHAR(36) NOT NULL,
 from_group_id VARCHAR(36) NULL,
 to_group_id VARCHAR(36) NOT NULL,
 reason_code VARCHAR(64) NOT NULL,
 changed_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;
CREATE TABLE internship_grouping_plans (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 cohort_id VARCHAR(36) NOT NULL,
 cohort_version BIGINT NOT NULL,
 groups_json JSON NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(cohort_id) REFERENCES internship_cohorts(id)
) ENGINE=InnoDB;
CREATE TABLE internship_context_assignments (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 kind VARCHAR(16) NOT NULL,
 user_id VARCHAR(36) NOT NULL,
 enterprise_org_id VARCHAR(36) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 group_id VARCHAR(36) NULL,
 case_id VARCHAR(36) NULL,
 parent_assignment_id VARCHAR(36) NULL,
 school_delegation_id VARCHAR(36) NULL,
 granted_by VARCHAR(36) NOT NULL,
 decision_ref VARCHAR(120) NOT NULL,
 invitation_accepted BOOLEAN NOT NULL DEFAULT false,
 active BOOLEAN NOT NULL DEFAULT true,
 valid_from DATETIME(6) NOT NULL,
 valid_until DATETIME(6) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(granted_by) REFERENCES users(id),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(enterprise_org_id) REFERENCES organizations(id),
 FOREIGN KEY(group_id) REFERENCES internship_groups(id),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 FOREIGN KEY(parent_assignment_id) REFERENCES internship_context_assignments(id),
 FOREIGN KEY(school_delegation_id) REFERENCES school_delegations(id),
 CHECK(kind IN ('COORDINATOR','HR','MENTOR')),
 CHECK(valid_from<valid_until),
 CHECK((kind='COORDINATOR' AND school_delegation_id IS NOT NULL AND parent_assignment_id IS NULL) OR
       (kind IN ('HR','MENTOR') AND parent_assignment_id IS NOT NULL AND school_delegation_id IS NULL)),
 CHECK(kind<>'MENTOR' OR (group_id IS NOT NULL AND invitation_accepted=true)),
 INDEX ix_context_subject(user_id,kind,term_id,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE internship_tasks (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 group_id VARCHAR(36) NOT NULL,
 title VARCHAR(200) NOT NULL,
 description TEXT NOT NULL,
 due_at DATETIME(6) NOT NULL,
 priority VARCHAR(16) NOT NULL,
 late_allowed BOOLEAN NOT NULL,
 version BIGINT NOT NULL,
 FOREIGN KEY(group_id) REFERENCES internship_groups(id),
 CHECK(priority IN ('LOW','NORMAL','HIGH')),
 INDEX ix_task_due(due_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE internship_task_assignments (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 task_id VARCHAR(36) NOT NULL,
 case_id VARCHAR(36) NOT NULL,
 student_user_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL,
 version BIGINT NOT NULL,
 current_submission_id VARCHAR(36) NULL,
 UNIQUE(task_id,case_id),
 FOREIGN KEY(task_id) REFERENCES internship_tasks(id),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 FOREIGN KEY(student_user_id) REFERENCES users(id),
 CHECK(state IN ('NEW','IN_PROGRESS','SUBMITTED','REVISION_REQUIRED','COMPLETED'))
) ENGINE=InnoDB;
INSERT INTO permissions(code,resource,action,description) VALUES
 ('t3:cohorts:manage','t3_cohort','manage','Manage cohorts within current HR assignment'),
 ('t3:workspace:read','t3_workspace','read','Read current assigned or self workspace');
INSERT INTO role_permissions(role,permission_code) VALUES
 ('ENTERPRISE','t3:cohorts:manage'),('ENTERPRISE','t3:workspace:read'),
 ('STUDENT','t3:workspace:read'),('SCHOOL_STAFF','t3:workspace:read');
