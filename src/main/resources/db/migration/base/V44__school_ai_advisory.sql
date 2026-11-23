-- Additive optional advisory extension. No backfill or automatic delegation grant.
INSERT INTO permissions(code,resource,action,description) VALUES
 ('matching:school:advisory_manage','matching_advisory','manage','Delegated school advisory creation'),
 ('matching:school:advisory_read','matching_advisory','read','Delegated school advisory read with source field rights');
INSERT INTO role_permissions(role,permission_code) VALUES
 ('SCHOOL_STAFF','matching:school:advisory_manage'),('SCHOOL_STAFF','matching:school:advisory_read');
CREATE TABLE matching_run_origins (
 run_id VARCHAR(36) PRIMARY KEY, audience VARCHAR(16) NOT NULL,
 FOREIGN KEY(run_id) REFERENCES match_runs(id), CHECK(audience IN ('SCHOOL','ENTERPRISE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE student_semantic_sets (
 id VARCHAR(36) PRIMARY KEY, data_json LONGTEXT NOT NULL, expires_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE student_semantic_snapshots (
 id VARCHAR(36) PRIMARY KEY, set_id VARCHAR(36) NOT NULL, student_user_id VARCHAR(36) NOT NULL, data_json LONGTEXT NOT NULL,
 UNIQUE(set_id,student_user_id), FOREIGN KEY(set_id) REFERENCES student_semantic_sets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE demand_semantic_snapshots (
 id VARCHAR(36) PRIMARY KEY, data_json LONGTEXT NOT NULL, source_context_json LONGTEXT NOT NULL, expires_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE matching_advisory_jobs (
 id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, school_org_id VARCHAR(36) NOT NULL,
 data_json LONGTEXT NOT NULL, state VARCHAR(16) NOT NULL, created_at DATETIME(6) NOT NULL, expires_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL, counts_json LONGTEXT NULL, failure_code VARCHAR(64) NULL,
 attempt INT NOT NULL DEFAULT 0, lease_token BIGINT NOT NULL DEFAULT 0, lease_until DATETIME(6) NULL,
 next_attempt_at DATETIME(6) NOT NULL, sources_purged BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(run_id) REFERENCES match_runs(id), FOREIGN KEY(school_org_id) REFERENCES organizations(id),
 INDEX advisory_admission(school_org_id,run_id,state,created_at), INDEX advisory_poll(state,next_attempt_at,lease_until), INDEX advisory_expiry(expires_at),
 CHECK(state IN ('QUEUED','RUNNING','READY','NO_DATA','FAILED','STALE','REVOKED','EXPIRED')),
 CHECK(attempt BETWEEN 0 AND 3), CHECK(lease_token>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE matching_advisory_rows (
 job_id VARCHAR(36) NOT NULL, student_user_id VARCHAR(36) NOT NULL, position INT NOT NULL, data_json LONGTEXT NOT NULL, input_hash CHAR(64) NOT NULL,
 PRIMARY KEY(job_id,student_user_id), UNIQUE(job_id,position), FOREIGN KEY(job_id) REFERENCES matching_advisory_jobs(id), CHECK(position BETWEEN 1 AND 5000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE matching_advisory_control (
 id INT PRIMARY KEY, hold_enabled BOOLEAN NOT NULL DEFAULT FALSE, hold_approval_ref VARCHAR(200) NULL,
 hold_reason VARCHAR(500) NULL, hold_until DATETIME(6) NULL,
 CHECK(id=1), CHECK(hold_enabled=FALSE OR (hold_approval_ref IS NOT NULL AND hold_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO matching_advisory_control(id) VALUES(1);
