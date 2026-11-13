-- T3 shared schema, allocated after T1 V28. CP-WS2 v1.0.
-- FR-15/16 producers append immutable artifacts/reviews. FR-17 runtime belongs to DEV06.
CREATE TABLE internship_submissions (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 assignment_id VARCHAR(36) NOT NULL,
 version_no INT NOT NULL,
 url TEXT NULL,
 note TEXT NULL,
 submitted_at DATETIME(6) NOT NULL,
 late_flag BOOLEAN NOT NULL,
 UNIQUE KEY uq_submission_version(assignment_id,version_no),
 UNIQUE KEY uq_submission_owner(id,assignment_id),
 UNIQUE KEY uq_submission_review_version(id,version_no),
 FOREIGN KEY(assignment_id) REFERENCES internship_task_assignments(id),
 CHECK(version_no>=1),
 CHECK(note IS NULL OR CHAR_LENGTH(note)<=2000),
 INDEX ix_submission_history(assignment_id,version_no DESC,id ASC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A current pointer must reference an artifact belonging to this exact assignment.
ALTER TABLE internship_task_assignments
 ADD CONSTRAINT fk_assignment_current_submission
 FOREIGN KEY(current_submission_id,id) REFERENCES internship_submissions(id,assignment_id);

CREATE TABLE internship_submission_reviews (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 submission_id VARCHAR(36) NOT NULL,
 reviewed_version_no INT NOT NULL,
 verdict VARCHAR(32) NOT NULL,
 comment TEXT NOT NULL,
 reviewer_user_id VARCHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 assignment_version BIGINT NOT NULL,
 UNIQUE KEY uq_submission_review(submission_id),
 FOREIGN KEY(submission_id,reviewed_version_no) REFERENCES internship_submissions(id,version_no),
 FOREIGN KEY(reviewer_user_id) REFERENCES users(id),
 CHECK(verdict IN ('ACCEPT','REQUEST_REVISION')),
 CHECK(CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 2000),
 CHECK(assignment_version BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Reserved ordered artifact references; URL runtime never invents CLEAN file bindings.
CREATE TABLE internship_submission_files (
 submission_id VARCHAR(36) NOT NULL,
 file_id VARCHAR(36) NOT NULL,
 ordinal INT NOT NULL,
 PRIMARY KEY(submission_id,file_id),
 UNIQUE KEY uq_submission_file_order(submission_id,ordinal),
 FOREIGN KEY(submission_id) REFERENCES internship_submissions(id),
 FOREIGN KEY(file_id) REFERENCES files(id),
 CHECK(ordinal BETWEEN 1 AND 20)
) ENGINE=InnoDB;

-- Schema only for DEV06; no Comment routes, grants or audit producers are added here.
CREATE TABLE internship_task_comments (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 assignment_id VARCHAR(36) NOT NULL,
 author_user_id VARCHAR(36) NOT NULL,
 text TEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(assignment_id) REFERENCES internship_task_assignments(id),
 FOREIGN KEY(author_user_id) REFERENCES users(id),
 CHECK(CHAR_LENGTH(TRIM(text)) BETWEEN 1 AND 2000),
 INDEX ix_task_comment_history(assignment_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
