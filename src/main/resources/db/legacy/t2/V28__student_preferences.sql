-- T2-07 Preference (BC22): one current intent per student/opportunity/term plus immutable history.
-- Taken from DEV02 commit 46a7a28; the T5 opportunity_publications part stays with T5 and is not included here.
CREATE TABLE student_preferences (
    preference_id VARCHAR(36) NOT NULL PRIMARY KEY,
    student_user_id VARCHAR(36) NOT NULL,
    school_org_id VARCHAR(36) NOT NULL,
    term_id VARCHAR(36) NOT NULL,
    opportunity_id VARCHAR(36) NOT NULL,
    intent_version INT NOT NULL,
    version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    message VARCHAR(1000) NULL,
    intent_created_at DATETIME(6) NOT NULL,
    withdrawn_at DATETIME(6) NULL,
    reviewed_at DATETIME(6) NULL,
    reviewed_by_user_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_student_preference_intent UNIQUE (student_user_id, opportunity_id, term_id),
    CONSTRAINT fk_student_preference_student FOREIGN KEY (student_user_id) REFERENCES users(id),
    CONSTRAINT fk_student_preference_school FOREIGN KEY (school_org_id) REFERENCES organizations(id),
    CONSTRAINT fk_student_preference_term FOREIGN KEY (term_id, school_org_id) REFERENCES terms(id, school_org_id),
    CONSTRAINT fk_student_preference_opportunity FOREIGN KEY (opportunity_id) REFERENCES cooperations(id),
    CONSTRAINT fk_student_preference_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id),
    CONSTRAINT ck_student_preference_version CHECK (intent_version >= 1 AND version BETWEEN 1 AND 9007199254740991),
    CONSTRAINT ck_student_preference_status CHECK (status IN ('SUBMITTED', 'WITHDRAWN', 'REVIEWED')),
    CONSTRAINT ck_student_preference_review CHECK (
        (status = 'REVIEWED' AND reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL) OR
        (status <> 'REVIEWED' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
    ),
    INDEX ix_student_preferences_student (student_user_id, term_id, updated_at, preference_id),
    INDEX ix_student_preferences_school (school_org_id, term_id, status, updated_at, preference_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_preference_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    preference_id VARCHAR(36) NOT NULL,
    intent_version INT NOT NULL,
    version BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    reason_code VARCHAR(64) NULL,
    snapshot_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_student_preference_history_version UNIQUE (preference_id, version),
    CONSTRAINT fk_student_preference_history_preference FOREIGN KEY (preference_id) REFERENCES student_preferences(preference_id),
    CONSTRAINT fk_student_preference_history_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    CONSTRAINT ck_student_preference_history_operation CHECK (operation IN ('SUBMIT', 'WITHDRAW', 'REVIEW')),
    INDEX ix_student_preference_history_created (preference_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO permissions(code, resource, action, description) VALUES
    ('student:preference:self:manage', 'student:preference:self', 'manage', 'Manage own internship preferences'),
    ('student:preference:school:review', 'student:preference:school', 'review', 'Review student preferences in delegated school scope');

INSERT IGNORE INTO role_permissions(role, permission_code) VALUES
    ('STUDENT', 'student:preference:self:manage'),
    ('SCHOOL_STAFF', 'student:preference:school:review');
