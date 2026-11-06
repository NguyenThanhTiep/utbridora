
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
    school_approved BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_student_eligibility
        UNIQUE (student_roster_id, term_code),

    CONSTRAINT fk_eligibility_roster
        FOREIGN KEY (student_roster_id)
        REFERENCES student_rosters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
