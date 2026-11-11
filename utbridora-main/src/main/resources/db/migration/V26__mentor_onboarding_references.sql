-- T1 identity/onboarding references use the existing one_time_tokens, not a credential store in T3.
CREATE TABLE mentor_invitation_refs (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 email VARCHAR(254) NOT NULL,
 user_id VARCHAR(36) NULL,
 token_id VARCHAR(36) NULL UNIQUE,
 hr_user_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 scope_json JSON NOT NULL,
 assignment_ids JSON NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 FOREIGN KEY(user_id) REFERENCES users(id),
 FOREIGN KEY(token_id) REFERENCES one_time_tokens(id),
 FOREIGN KEY(hr_user_id) REFERENCES users(id),
 CHECK(state IN ('NEW_IDENTITY_PENDING','ACCOUNT_PENDING','MEMBERSHIP_PENDING','ACCEPTED','REVOKED','REVIEW_REQUIRED')),
 INDEX ix_mentor_invitee(user_id,state)
) ENGINE=InnoDB;
ALTER TABLE internship_context_assignments ADD COLUMN mentor_invitation_id VARCHAR(36) NULL,
 ADD CONSTRAINT fk_context_mentor_invite FOREIGN KEY(mentor_invitation_id) REFERENCES mentor_invitation_refs(id);
