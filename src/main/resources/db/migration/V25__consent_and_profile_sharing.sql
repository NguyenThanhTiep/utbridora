CREATE TABLE school_sharing_policies (
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 version BIGINT NOT NULL,
 allowed_fields JSON NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 updated_by VARCHAR(36) NOT NULL,
 PRIMARY KEY(school_org_id,term_id),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(updated_by) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE student_consent_lineages (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 student_user_id VARCHAR(36) NOT NULL,
 school_org_id VARCHAR(36) NOT NULL,
 term_id VARCHAR(36) NOT NULL,
 recipient_org_id VARCHAR(36) NOT NULL,
 purpose VARCHAR(32) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 current_consent_id VARCHAR(36) NULL,
 UNIQUE(student_user_id,school_org_id,term_id,recipient_org_id,purpose),
 FOREIGN KEY(student_user_id) REFERENCES users(id),
 FOREIGN KEY(term_id,school_org_id) REFERENCES terms(id,school_org_id),
 FOREIGN KEY(recipient_org_id) REFERENCES organizations(id)
) ENGINE=InnoDB;
CREATE TABLE student_consents (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 lineage_id VARCHAR(36) NOT NULL,
 student_user_id VARCHAR(36) NOT NULL,
 data_json JSON NOT NULL,
 FOREIGN KEY(lineage_id) REFERENCES student_consent_lineages(id),
 FOREIGN KEY(student_user_id) REFERENCES users(id)
) ENGINE=InnoDB;
CREATE TABLE profile_shares (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 proposal_item_id VARCHAR(36) NOT NULL,
 consent_id VARCHAR(36) NOT NULL,
 data_json JSON NOT NULL,
 FOREIGN KEY(proposal_item_id) REFERENCES proposal_items(id),
 FOREIGN KEY(consent_id) REFERENCES student_consents(id)
) ENGINE=InnoDB;
