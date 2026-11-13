CREATE TABLE mentor_onboarding_claims (
  email VARCHAR(254) PRIMARY KEY,
  invitation_id VARCHAR(36) NULL,
  last_issued_at DATETIME(6) NULL,
  CONSTRAINT fk_mentor_claim_invitation FOREIGN KEY (invitation_id) REFERENCES mentor_invitation_refs(id)
);
CREATE TABLE mentor_manual_reissues (
  id VARCHAR(36) PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  INDEX ix_mentor_reissue_email_time(email,issued_at),
  CONSTRAINT fk_mentor_reissue_claim FOREIGN KEY(email) REFERENCES mentor_onboarding_claims(email)
);
-- Adopt only a real pending Mentor reference with its existing token; external
-- invitations/roster claims are never taken over merely because the email matches.
INSERT INTO mentor_onboarding_claims(email,invitation_id,last_issued_at)
SELECT r.email,r.id,t.created_at FROM mentor_invitation_refs r
JOIN one_time_tokens t ON t.id=r.token_id
JOIN (SELECT email,MIN(id) AS canonical_id FROM mentor_invitation_refs
      WHERE state IN ('NEW_IDENTITY_PENDING','ACCOUNT_PENDING') AND token_id IS NOT NULL
      GROUP BY email HAVING COUNT(*)=1) c ON c.canonical_id=r.id
WHERE t.consumed_at IS NULL;
