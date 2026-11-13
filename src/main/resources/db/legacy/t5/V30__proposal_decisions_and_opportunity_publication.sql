ALTER TABLE cooperations
 ADD COLUMN published_at DATETIME(6) NULL,
 ADD COLUMN closed_at DATETIME(6) NULL,
 ADD CONSTRAINT chk_opportunity_window CHECK (
   (published_at IS NULL AND closed_at IS NULL) OR
   (published_at IS NOT NULL AND closed_at IS NOT NULL AND closed_at > published_at)),
 ADD INDEX ix_opportunity_publication(term_id,state,published_at,closed_at);

INSERT INTO permissions(code,resource,action,description) VALUES
 ('t5:proposal:read','t5:proposal','read','Read explicitly shared enterprise proposals'),
 ('proposal:enterprise:decide','proposal:enterprise','decide','Enterprise decision on a currently shared proposal'),
 ('opportunity:school:publish','opportunity:school','publish','Configure opportunity publication with school delegation');
INSERT INTO role_permissions(role,permission_code) VALUES
 ('ENTERPRISE','t5:proposal:read'),
 ('ENTERPRISE','proposal:enterprise:decide'),
 ('SCHOOL_STAFF','opportunity:school:publish');
