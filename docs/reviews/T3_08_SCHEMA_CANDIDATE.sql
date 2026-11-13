-- REVIEW ONLY: pending T1 migration number and schema approval. Not a Flyway resource.
-- V29 supplies internship_task_comments; do not recreate it.
CREATE TABLE internship_weekly_reports (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 case_id VARCHAR(36) NOT NULL,
 week_start DATE NOT NULL,
 due_at DATETIME(6) NOT NULL,
 current_revision_no INT NOT NULL,
 version BIGINT NOT NULL,
 UNIQUE(case_id,week_start),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 CHECK(WEEKDAY(week_start)=0),
 CHECK(current_revision_no>=1),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 INDEX ix_weekly_case_week(case_id,week_start,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_weekly_report_revisions (
 report_id VARCHAR(36) NOT NULL,
 revision_no INT NOT NULL,
 report_version BIGINT NOT NULL,
 state VARCHAR(16) NOT NULL,
 completed_work MEDIUMTEXT NOT NULL,
 difficulties TEXT NOT NULL,
 next_plan TEXT NOT NULL,
 reason_code VARCHAR(64) COLLATE utf8mb4_bin NULL,
 submitted_at DATETIME(6) NULL,
 submitted_submicro_nanos SMALLINT NULL,
 late_flag BOOLEAN NOT NULL,
 PRIMARY KEY(report_id,revision_no),
 FOREIGN KEY(report_id) REFERENCES internship_weekly_reports(id),
 CHECK(revision_no>=1),
 CHECK(report_version BETWEEN 1 AND 9007199254740991),
 CHECK(state IN ('DRAFT','SUBMITTED','REVIEWED')),
 CHECK(CHAR_LENGTH(completed_work) BETWEEN 1 AND 10000),
 CHECK(CHAR_LENGTH(difficulties) BETWEEN 0 AND 5000),
 CHECK(CHAR_LENGTH(next_plan) BETWEEN 1 AND 5000),
 CHECK((revision_no=1 AND reason_code IS NULL)
    OR (revision_no>1 AND reason_code IS NOT NULL AND reason_code REGEXP '^[A-Z0-9_]{3,64}$')),
 CHECK((state='DRAFT' AND submitted_at IS NULL AND submitted_submicro_nanos IS NULL AND late_flag=false)
    OR (state IN ('SUBMITTED','REVIEWED') AND submitted_at IS NOT NULL
        AND submitted_submicro_nanos IS NOT NULL AND submitted_submicro_nanos BETWEEN 0 AND 999))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_weekly_report_reviews (
 report_id VARCHAR(36) NOT NULL,
 revision_no INT NOT NULL,
 reviewer_user_id VARCHAR(36) NOT NULL,
 comment TEXT NOT NULL,
 reviewed_at DATETIME(6) NOT NULL,
 reviewed_submicro_nanos SMALLINT NOT NULL,
 PRIMARY KEY(report_id,revision_no),
 FOREIGN KEY(report_id,revision_no) REFERENCES internship_weekly_report_revisions(report_id,revision_no),
 FOREIGN KEY(reviewer_user_id) REFERENCES users(id),
 CHECK(CHAR_LENGTH(comment) BETWEEN 1 AND 2000),
 CHECK(reviewed_submicro_nanos BETWEEN 0 AND 999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_weekly_report_files (
 report_id VARCHAR(36) NOT NULL,
 revision_no INT NOT NULL,
 ordinal_no SMALLINT NOT NULL,
 file_id VARCHAR(36) NOT NULL,
 PRIMARY KEY(report_id,revision_no,ordinal_no),
 FOREIGN KEY(report_id,revision_no) REFERENCES internship_weekly_report_revisions(report_id,revision_no),
 FOREIGN KEY(file_id) REFERENCES files(id),
 CHECK(ordinal_no BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Exact role ceilings; current scoped guards remain mandatory.
INSERT IGNORE INTO permissions(code,resource,action,description) VALUES ('t3:comments:write','t3:comments','write','Task comments in assigned internship scope');
INSERT IGNORE INTO role_permissions(role,permission_code) VALUES ('STUDENT','t3:comments:write'),('ENTERPRISE','t3:comments:write');
