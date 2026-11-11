CREATE TABLE cooperations (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 demand_id VARCHAR(36) NOT NULL UNIQUE,
 school_org_id VARCHAR(36) NOT NULL,
 enterprise_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL,
 submission_no INT NOT NULL DEFAULT 0,
 version BIGINT NOT NULL DEFAULT 1,
 submitted_at DATETIME(6) NULL,
 decision_ref VARCHAR(120) NULL,
 created_at DATETIME(6) NOT NULL,
 CHECK(state IN ('DRAFT','SUBMITTED','NEEDS_CHANGES','APPROVED','REJECTED','CANCELLED')),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 FOREIGN KEY(demand_id) REFERENCES demands(id),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(enterprise_org_id) REFERENCES organizations(id)
) ENGINE=InnoDB;
ALTER TABLE demands ADD COLUMN cooperation_id VARCHAR(36) NULL,
 ADD CONSTRAINT fk_demand_cooperation FOREIGN KEY(cooperation_id) REFERENCES cooperations(id);
CREATE TABLE cooperation_history (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 cooperation_id VARCHAR(36) NOT NULL,
 submission_no INT NOT NULL,
 actor_id VARCHAR(36) NOT NULL,
 operation VARCHAR(32) NOT NULL,
 data_json JSON NOT NULL,
 created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(cooperation_id) REFERENCES cooperations(id),
 FOREIGN KEY(actor_id) REFERENCES users(id),
 INDEX ix_coop_history(cooperation_id,submission_no,created_at)
) ENGINE=InnoDB;
CREATE TABLE proposal_batches (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 cooperation_id VARCHAR(36) NOT NULL UNIQUE,
 version BIGINT NOT NULL DEFAULT 1,
 FOREIGN KEY(cooperation_id) REFERENCES cooperations(id)
) ENGINE=InnoDB;
CREATE TABLE proposal_items (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 batch_id VARCHAR(36) NOT NULL,
 student_user_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 reservation_until DATETIME(6) NOT NULL,
 profile_snapshot_id VARCHAR(36) NOT NULL,
 match_run_id VARCHAR(36) NOT NULL,
 invitation_id VARCHAR(36) NULL,
 share_id VARCHAR(36) NULL,
 placement_id VARCHAR(36) NULL,
 UNIQUE(batch_id,student_user_id),
 CHECK(state IN ('RESERVED','INVITED','ACCEPTED','SHARED','ENTERPRISE_ACCEPTED','PLACED','DECLINED','EXPIRED','REVOKED')),
 FOREIGN KEY(batch_id) REFERENCES proposal_batches(id),
 FOREIGN KEY(student_user_id) REFERENCES users(id),
 FOREIGN KEY(profile_snapshot_id) REFERENCES student_profile_snapshots(id),
 FOREIGN KEY(match_run_id) REFERENCES match_runs(id)
) ENGINE=InnoDB;
CREATE TABLE internship_invitations (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 proposal_item_id VARCHAR(36) NOT NULL UNIQUE,
 student_user_id VARCHAR(36) NOT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
 expires_at DATETIME(6) NOT NULL,
 decided_at DATETIME(6) NULL,
 decision_reason_code VARCHAR(64) NULL,
 version BIGINT NOT NULL DEFAULT 1,
 CHECK(state IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','WITHDRAWN')),
 FOREIGN KEY(proposal_item_id) REFERENCES proposal_items(id),
 FOREIGN KEY(student_user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE internship_invitation_history (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 invitation_id VARCHAR(36) NOT NULL,
 from_state VARCHAR(32) NULL,
 to_state VARCHAR(32) NOT NULL,
 version BIGINT NOT NULL,
 actor_id VARCHAR(36) NOT NULL,
 reason_code VARCHAR(64) NULL,
 created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(invitation_id) REFERENCES internship_invitations(id),
 FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB;
