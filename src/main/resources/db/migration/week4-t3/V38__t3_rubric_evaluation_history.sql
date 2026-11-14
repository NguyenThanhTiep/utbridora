-- T3-10 / CP-WS4. Forward-only, after main V37. No existing migration or permission changes.
-- Templates are provisioned as versioned, school/term-scoped fixtures; no CRUD or real-data seed.
CREATE TABLE internship_rubric_templates (
 id VARCHAR(36) NOT NULL,
 template_version BIGINT NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 template_json LONGTEXT NOT NULL,
 PRIMARY KEY(id,template_version),
 FOREIGN KEY(school_org_id) REFERENCES organizations(id),
 FOREIGN KEY(term_id) REFERENCES terms(id),
 CHECK(template_version BETWEEN 0 AND 9007199254740991),
 CHECK(JSON_VALID(template_json)),
 INDEX ix_rubric_scope(school_org_id,term_id,id,template_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_evaluations (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 case_id VARCHAR(36) NOT NULL UNIQUE,
 current_version BIGINT NULL,
 published_version BIGINT NULL,
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 CHECK(current_version IS NULL OR current_version BETWEEN 1 AND 9007199254740991),
 CHECK(published_version IS NULL OR published_version BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- INSERT-only snapshots, including every DRAFT save. DEV06 can append a new revision/publication
-- and atomically advance the root pointers without overwriting SUBMITTED/PUBLISHED history.
CREATE TABLE internship_evaluation_versions (
 evaluation_id VARCHAR(36) NOT NULL,
 version BIGINT NOT NULL,
 revision_no INT NOT NULL,
 state VARCHAR(16) NOT NULL,
 template_id VARCHAR(36) NOT NULL,
 template_version BIGINT NOT NULL,
 snapshot_json LONGTEXT NOT NULL,
 total100 DECIMAL(5,2) NOT NULL,
 created_by VARCHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(evaluation_id,version),
 FOREIGN KEY(evaluation_id) REFERENCES internship_evaluations(id),
 FOREIGN KEY(template_id,template_version) REFERENCES internship_rubric_templates(id,template_version),
 FOREIGN KEY(created_by) REFERENCES users(id),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 CHECK(revision_no>=1),
 CHECK(state IN ('DRAFT','SUBMITTED','PUBLISHED')),
 CHECK(total100 BETWEEN 0 AND 100),
 CHECK(JSON_VALID(snapshot_json)),
 INDEX ix_evaluation_revision(evaluation_id,revision_no,version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE internship_evaluations
 ADD CONSTRAINT fk_evaluation_current FOREIGN KEY(id,current_version)
 REFERENCES internship_evaluation_versions(evaluation_id,version),
 ADD CONSTRAINT fk_evaluation_published FOREIGN KEY(id,published_version)
 REFERENCES internship_evaluation_versions(evaluation_id,version);

CREATE TABLE internship_evaluation_files (
 evaluation_id VARCHAR(36) NOT NULL,
 evaluation_version BIGINT NOT NULL,
 file_id VARCHAR(36) NOT NULL,
 PRIMARY KEY(evaluation_id,evaluation_version,file_id),
 FOREIGN KEY(evaluation_id,evaluation_version) REFERENCES internship_evaluation_versions(evaluation_id,version),
 FOREIGN KEY(file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
