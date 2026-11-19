-- T1 CP-IMPORT1: additive staging, shared invitation claims and private mail transport.
CREATE TABLE invitation_claims (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 email_canonical VARCHAR(254) NOT NULL,
 context_kind VARCHAR(16) NOT NULL,
 school_org_id VARCHAR(36) NULL,
 roster_id VARCHAR(36) NULL,
 invitation_ref_id VARCHAR(36) NULL,
 expected_user_id VARCHAR(36) NULL,
 user_id VARCHAR(36) NULL,
 claim_state VARCHAR(20) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 generation BIGINT NOT NULL DEFAULT 0,
 current_token_id VARCHAR(36) NULL,
 last_issued_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 UNIQUE(email_canonical),
 FOREIGN KEY(school_org_id) REFERENCES organizations(id),
 FOREIGN KEY(roster_id) REFERENCES student_rosters(id),
 FOREIGN KEY(invitation_ref_id) REFERENCES mentor_invitation_refs(id),
 FOREIGN KEY(expected_user_id) REFERENCES users(id),
 FOREIGN KEY(user_id) REFERENCES users(id),
 CHECK(context_kind IN ('ROSTER','MENTOR','LEGACY')),
 CHECK(claim_state IN ('PENDING','ACTIVATED','REVIEW_REQUIRED')),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 CHECK(generation BETWEEN 0 AND 9007199254740991),
 CHECK(context_kind<>'ROSTER' OR (school_org_id IS NOT NULL AND roster_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE one_time_tokens
 ADD COLUMN claim_id VARCHAR(36) NULL,
 ADD COLUMN generation BIGINT NULL,
 ADD COLUMN issued_at DATETIME(6) NULL,
 ADD COLUMN revoked_at DATETIME(6) NULL,
 ADD COLUMN revocation_reason VARCHAR(64) NULL,
 ADD CONSTRAINT fk_token_invitation_claim FOREIGN KEY(claim_id) REFERENCES invitation_claims(id);
UPDATE one_time_tokens SET issued_at=created_at;
ALTER TABLE one_time_tokens MODIFY issued_at DATETIME(6) NOT NULL;

-- A single pending historical invitation can be anchored without changing its hash/expiry/state.
-- Ambiguous pending invitations become REVIEW_REQUIRED anchors, not silently merged identities.
INSERT INTO invitation_claims(id,email_canonical,context_kind,invitation_ref_id,expected_user_id,claim_state,generation,current_token_id,last_issued_at,created_at,updated_at)
SELECT UUID(),LOWER(TRIM(t.email)),
 CASE WHEN COUNT(*)=1 AND MAX(m.id) IS NOT NULL THEN 'MENTOR' ELSE 'LEGACY' END,
 CASE WHEN COUNT(*)=1 THEN MAX(m.id) ELSE NULL END,
 CASE WHEN COUNT(*)=1 THEN MAX(t.user_id) ELSE NULL END,
 CASE WHEN COUNT(*)=1 THEN 'PENDING' ELSE 'REVIEW_REQUIRED' END,
 CASE WHEN COUNT(*)=1 THEN 1 ELSE 0 END,
 CASE WHEN COUNT(*)=1 THEN MAX(t.id) ELSE NULL END,
 MAX(t.created_at),MIN(t.created_at),UTC_TIMESTAMP(6)
FROM one_time_tokens t LEFT JOIN mentor_invitation_refs m ON m.token_id=t.id
WHERE t.purpose='INVITATION' AND t.consumed_at IS NULL
GROUP BY LOWER(TRIM(t.email));
UPDATE one_time_tokens t JOIN invitation_claims c ON c.current_token_id=t.id
 SET t.claim_id=c.id,t.generation=c.generation;
ALTER TABLE one_time_tokens
 ADD COLUMN current_invitation_claim_id VARCHAR(36) GENERATED ALWAYS AS
   (CASE WHEN purpose='INVITATION' AND consumed_at IS NULL AND revoked_at IS NULL THEN claim_id ELSE NULL END) STORED,
 ADD CONSTRAINT uk_current_invitation_claim UNIQUE(current_invitation_claim_id),
 ADD CONSTRAINT ck_token_claim_generation CHECK((claim_id IS NULL AND generation IS NULL) OR (claim_id IS NOT NULL AND generation>=1));
ALTER TABLE invitation_claims ADD CONSTRAINT fk_claim_current_token FOREIGN KEY(current_token_id) REFERENCES one_time_tokens(id);

CREATE TABLE student_import_jobs (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 actor_id VARCHAR(36) NOT NULL,
 initiated_by VARCHAR(36) NULL,
 initiated_security_version BIGINT NULL,
 authority_assignment_id VARCHAR(36) NULL,
 authority_version BIGINT NULL,
 state VARCHAR(24) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 file_hash CHAR(64) NOT NULL,
 staging_file_id VARCHAR(36) NULL,
 catalog_version VARCHAR(64) NOT NULL,
 term_version BIGINT NOT NULL,
 preview_expires_at DATETIME(6) NOT NULL,
 total_rows INT NOT NULL,
 selected_rows INT NOT NULL DEFAULT 0,
 last_error_code VARCHAR(64) NULL,
 lease_owner VARCHAR(100) NULL,
 lease_epoch BIGINT NOT NULL DEFAULT 0,
 lease_until DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL,
 started_at DATETIME(6) NULL,
 finished_at DATETIME(6) NULL,
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 FOREIGN KEY(initiated_by) REFERENCES users(id),
 FOREIGN KEY(authority_assignment_id) REFERENCES school_delegations(id),
 FOREIGN KEY(staging_file_id) REFERENCES files(id),
 CHECK(state IN ('VALIDATING','PREVIEW_READY','EXPIRED','PROCESSING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED')),
 CHECK(total_rows BETWEEN 0 AND 5000 AND selected_rows BETWEEN 0 AND total_rows),
 CHECK(staging_file_id IS NOT NULL OR (state IN ('VALIDATING','FAILED') AND selected_rows=0)),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 INDEX ix_import_scope(school_org_id,state,created_at,id),
 INDEX ix_import_lease(state,lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_import_effects (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 effect_key CHAR(64) NOT NULL UNIQUE,
 payload_hash CHAR(64) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 roster_id VARCHAR(36) NOT NULL,
 claim_id VARCHAR(36) NULL,
 user_id VARCHAR(36) NULL,
 created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(roster_id) REFERENCES student_rosters(id),
 FOREIGN KEY(claim_id) REFERENCES invitation_claims(id),
 FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_import_rows (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 job_id VARCHAR(36) NOT NULL,
 row_no INT NOT NULL,
 row_hash CHAR(64) NOT NULL,
 normalized_snapshot JSON NOT NULL,
 validation_state VARCHAR(20) NOT NULL,
 selected BOOLEAN NOT NULL DEFAULT false,
 row_state VARCHAR(24) NOT NULL DEFAULT 'NOT_SELECTED',
 error_codes JSON NOT NULL,
 operation_id VARCHAR(36) NOT NULL,
 provisioning_effect_id VARCHAR(36) NULL,
 roster_id VARCHAR(36) NULL,
 claim_id VARCHAR(36) NULL,
 user_id VARCHAR(36) NULL,
 provisioning_outcome VARCHAR(24) NULL,
 version BIGINT NOT NULL DEFAULT 1,
 attempt_count INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NULL,
 UNIQUE(job_id,row_no),
 UNIQUE(job_id,operation_id),
 FOREIGN KEY(job_id) REFERENCES student_import_jobs(id),
 FOREIGN KEY(provisioning_effect_id) REFERENCES student_import_effects(id),
 FOREIGN KEY(roster_id) REFERENCES student_rosters(id),
 FOREIGN KEY(claim_id) REFERENCES invitation_claims(id),
 FOREIGN KEY(user_id) REFERENCES users(id),
 CHECK(row_no BETWEEN 2 AND 5001),
 CHECK(validation_state IN ('VALID','INVALID','NEEDS_REVIEW')),
 CHECK(row_state IN ('NOT_SELECTED','QUEUED','PROCESSING','PROVISIONED','ALREADY_LINKED','NEEDS_REVIEW','PERMANENT_FAILED','TRANSIENT_FAILED')),
 CHECK(selected=true OR row_state='NOT_SELECTED'),
 CHECK(selected=false OR validation_state='VALID'),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 INDEX ix_import_rows(job_id,selected,row_state,row_no),
 INDEX ix_import_retry(row_state,next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE activation_mail_outbox (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 message_id VARCHAR(36) NOT NULL UNIQUE,
 claim_id VARCHAR(36) NOT NULL,
 token_id VARCHAR(36) NOT NULL,
 generation BIGINT NOT NULL,
 template_code VARCHAR(64) NOT NULL,
 state VARCHAR(16) NOT NULL,
 issuance_reason VARCHAR(32) NOT NULL,
 operation_id VARCHAR(36) NOT NULL UNIQUE,
 initiated_by VARCHAR(36) NULL,
 attempt_count INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NULL,
 lease_owner VARCHAR(100) NULL,
 lease_until DATETIME(6) NULL,
 version BIGINT NOT NULL DEFAULT 1,
 encrypted_payload VARBINARY(4096) NULL,
 key_id VARCHAR(64) NULL,
 nonce BINARY(12) NULL,
 tag BINARY(16) NULL,
 last_error_class VARCHAR(64) NULL,
 created_at DATETIME(6) NOT NULL,
 sent_at DATETIME(6) NULL,
 UNIQUE(claim_id,generation,template_code),
 FOREIGN KEY(claim_id) REFERENCES invitation_claims(id),
 FOREIGN KEY(token_id) REFERENCES one_time_tokens(id),
 FOREIGN KEY(initiated_by) REFERENCES users(id),
 CHECK(state IN ('QUEUED','SENDING','RETRY_WAIT','SENT','FAILED','SUPERSEDED','CANCELLED')),
 CHECK(issuance_reason IN ('INITIAL','MANUAL_RESEND','EXPIRED_BEFORE_DELIVERY')),
 CHECK(attempt_count BETWEEN 0 AND 4),
 CHECK((encrypted_payload IS NULL AND key_id IS NULL AND nonce IS NULL AND tag IS NULL) OR (encrypted_payload IS NOT NULL AND key_id IS NOT NULL AND nonce IS NOT NULL AND tag IS NOT NULL)),
 INDEX ix_activation_dispatch(state,next_attempt_at,id),
 INDEX ix_activation_quota(claim_id,issuance_reason,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE mail_dispatch_budget (
 budget_key VARCHAR(64) NOT NULL PRIMARY KEY,
 window_start DATETIME(6) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 1,
 lease_owner VARCHAR(100) NULL,
 lease_until DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE student_activation_outbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY,
 aggregate_id VARCHAR(36) NOT NULL,
 aggregate_version BIGINT NOT NULL,
 roster_id VARCHAR(36) NOT NULL,
 payload JSON NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6) NULL,
 UNIQUE(aggregate_id,aggregate_version),
 FOREIGN KEY(aggregate_id) REFERENCES users(id),
 FOREIGN KEY(roster_id) REFERENCES student_rosters(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE student_activation_inbox (
 event_id VARCHAR(36) NOT NULL PRIMARY KEY,
 aggregate_id VARCHAR(36) NOT NULL,
 aggregate_version BIGINT NOT NULL,
 applied_at DATETIME(6) NOT NULL,
 FOREIGN KEY(aggregate_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO permissions(code,resource,action,description) VALUES('schoolRosterImport:manage','student_import','manage','Current school/term roster import delegation');
INSERT INTO role_permissions(role,permission_code) VALUES('SCHOOL_STAFF','schoolRosterImport:manage');
