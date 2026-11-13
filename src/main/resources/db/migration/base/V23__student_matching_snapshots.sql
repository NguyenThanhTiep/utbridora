CREATE TABLE student_snapshot_sets (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 data_json JSON NOT NULL,
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id)
) ENGINE=InnoDB;
CREATE TABLE student_profile_snapshots (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 dataset_id VARCHAR(36) NOT NULL,
 user_id VARCHAR(36) NOT NULL,
 data_json JSON NOT NULL,
 UNIQUE(dataset_id,user_id),
 FOREIGN KEY(dataset_id) REFERENCES student_snapshot_sets(id),
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE match_runs (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 demand_id VARCHAR(36) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 enterprise_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 dataset_id VARCHAR(36) NOT NULL,
 data_json JSON NOT NULL,
 FOREIGN KEY(demand_id) REFERENCES demands(id),
 FOREIGN KEY(dataset_id) REFERENCES student_snapshot_sets(id),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id)
) ENGINE=InnoDB;
CREATE TABLE match_results (
 run_id VARCHAR(36) NOT NULL,
 student_user_id VARCHAR(36) NOT NULL,
 overall_status VARCHAR(32) NOT NULL,
 fit_score DECIMAL(5,2) NULL,
 data_json JSON NOT NULL,
 PRIMARY KEY(run_id,student_user_id),
 FOREIGN KEY(run_id) REFERENCES match_runs(id),
 FOREIGN KEY(student_user_id) REFERENCES users(id),
 CHECK(overall_status IN ('MATCH','MISSING_DATA','NOT_MATCH')),
 CHECK((overall_status='MATCH' AND fit_score IS NOT NULL AND fit_score BETWEEN 0 AND 100) OR (overall_status<>'MATCH' AND fit_score IS NULL))
) ENGINE=InnoDB;
