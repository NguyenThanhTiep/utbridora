CREATE TABLE IF NOT EXISTS student_profiles (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    student_code VARCHAR(50) NOT NULL,
    university_id VARCHAR(36) NOT NULL,
    major_code VARCHAR(50) NOT NULL,
    academic_term VARCHAR(50) NOT NULL,
    phone_number VARCHAR(20),
    bio TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_student_univ UNIQUE (student_code, university_id),
    CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;