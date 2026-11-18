-- DEV06 T3-11. Preserve V38 evaluation snapshots and existing migration checksums.
CREATE TABLE internship_evaluation_revision_decisions (
 evaluation_id VARCHAR(36) NOT NULL,
 evaluation_version BIGINT NOT NULL,
 decision_ref VARCHAR(120) NOT NULL,
 reason_code VARCHAR(64) NOT NULL,
 PRIMARY KEY(evaluation_id,evaluation_version),
 FOREIGN KEY(evaluation_id,evaluation_version) REFERENCES internship_evaluation_versions(evaluation_id,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_school_result_policies (
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 enabled BOOLEAN NOT NULL,
 version BIGINT NOT NULL,
 PRIMARY KEY(school_org_id,term_id),
 FOREIGN KEY(school_org_id) REFERENCES organizations(id),
 FOREIGN KEY(term_id) REFERENCES terms(id),
 CHECK(version BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_grade_visibility (
 enterprise_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 scope_key VARCHAR(36) NOT NULL,
 case_id VARCHAR(36) NULL,
 enabled BOOLEAN NOT NULL,
 version BIGINT NOT NULL,
 PRIMARY KEY(enterprise_org_id,term_id,scope_key),
 FOREIGN KEY(enterprise_org_id) REFERENCES organizations(id),
 FOREIGN KEY(term_id) REFERENCES terms(id),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 CHECK((case_id IS NULL AND scope_key='ORGANIZATION_TERM') OR (case_id IS NOT NULL AND scope_key=case_id)),
 CHECK(version BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO permissions(code,resource,action,description) VALUES
 ('t3:grades:publish','t3:grades','publish','School scoped evaluation publication and revision'),
 ('t3:result_policy:manage','t3:result_policy','manage','School scoped result visibility policy'),
 ('t3:grade_visibility:manage','t3:grade_visibility','manage','HR scoped result visibility');
INSERT INTO role_permissions(role,permission_code) VALUES
 ('SCHOOL_STAFF','t3:grades:publish'),('SCHOOL_STAFF','t3:result_policy:manage'),('ENTERPRISE','t3:grade_visibility:manage');
