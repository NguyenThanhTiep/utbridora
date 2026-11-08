
CREATE TABLE student_rosters (
    id VARCHAR(36) PRIMARY KEY,
    school_org_id VARCHAR(36) NOT NULL,
    student_code VARCHAR(32) NOT NULL,
    user_id VARCHAR(36) NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    major_code VARCHAR(255) NOT NULL,
    term_code VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_student_roster_school_code
        UNIQUE (school_org_id, student_code),
    CONSTRAINT uk_student_roster_bound_scope UNIQUE (id,user_id,school_org_id),

    CONSTRAINT fk_student_roster_school
        FOREIGN KEY (school_org_id)
        REFERENCES organizations(id),

    CONSTRAINT fk_student_roster_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE student_term_eligibilities (
    id VARCHAR(36) PRIMARY KEY,
    student_roster_id VARCHAR(36) NOT NULL,
    term_code VARCHAR(100) NOT NULL,
    -- Legacy unbound rows remain non-approved until explicitly linked to authoritative IDs.
    user_id VARCHAR(36) NULL,
    school_org_id VARCHAR(36) NULL,
    term_id VARCHAR(36) NULL,
    version BIGINT NOT NULL DEFAULT 1,
    approved_by VARCHAR(36) NULL,
    approved_at DATETIME(6) NULL,
    reason_code VARCHAR(64) NULL,
    school_approved BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_student_eligibility
        UNIQUE (student_roster_id, term_code),
    CONSTRAINT uk_student_eligibility_user_term UNIQUE (user_id,term_id),
    CONSTRAINT fk_student_eligibility_bound_roster FOREIGN KEY (student_roster_id,user_id,school_org_id)
        REFERENCES student_rosters(id,user_id,school_org_id),
    CONSTRAINT fk_student_eligibility_bound_term FOREIGN KEY (term_id,school_org_id,term_code)
        REFERENCES terms(id,school_org_id,term_code),
    CONSTRAINT fk_student_eligibility_approver FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT ck_student_eligibility_binding CHECK (
        (user_id IS NULL AND school_org_id IS NULL AND term_id IS NULL AND school_approved=b'0') OR
        (user_id IS NOT NULL AND school_org_id IS NOT NULL AND term_id IS NOT NULL)),
    CONSTRAINT ck_student_eligibility_approval CHECK (
        school_approved=b'0' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)),
    CONSTRAINT ck_student_eligibility_version CHECK (version BETWEEN 1 AND 9007199254740991),

    CONSTRAINT fk_eligibility_roster
        FOREIGN KEY (student_roster_id)
        REFERENCES student_rosters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
