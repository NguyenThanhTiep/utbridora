-- T5-owned durable sidecar. Verification DTOs and submission history remain unchanged.
CREATE TABLE verification_tax_checks (
 id VARCHAR(36) PRIMARY KEY,
 verification_id VARCHAR(36) NOT NULL,
 submission_no INT NOT NULL,
 input_json JSON NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempt_count INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NOT NULL,
 lease_token VARCHAR(36) NULL,
 lease_until DATETIME(6) NULL,
 result_json JSON NULL,
 created_at DATETIME(6) NOT NULL,
 completed_at DATETIME(6) NULL,
 UNIQUE KEY uq_verification_tax_submission (verification_id,submission_no),
 KEY ix_verification_tax_pending (state,next_attempt_at),
 FOREIGN KEY (verification_id) REFERENCES enterprise_verifications(id) ON DELETE CASCADE,
 CHECK (submission_no > 0),
 CHECK (state IN ('PENDING','PROCESSING','COMPLETED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
