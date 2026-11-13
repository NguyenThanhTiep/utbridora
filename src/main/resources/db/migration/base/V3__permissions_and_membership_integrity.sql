-- Extension of V1: preserve users/memberships from the Authentication foundation.
CREATE TABLE permissions (
  code VARCHAR(100) NOT NULL PRIMARY KEY,
  resource VARCHAR(60) NOT NULL,
  action VARCHAR(40) NOT NULL,
  description VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_permission_resource_action (resource, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permissions (
  role VARCHAR(32) NOT NULL,
  permission_code VARCHAR(100) NOT NULL,
  PRIMARY KEY (role, permission_code),
  CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_code) REFERENCES permissions(code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A generated scope key ensures UNIQUE also works for organization_id = NULL (global ADMIN).
ALTER TABLE memberships
  ADD COLUMN scope_key VARCHAR(36) GENERATED ALWAYS AS (COALESCE(organization_id, 'GLOBAL')) STORED,
  ADD CONSTRAINT uk_memberships_user_role_scope UNIQUE (user_id, role, scope_key);

INSERT INTO permissions (code,resource,action,description) VALUES
('account:self:read','account','self_read','Read own identity and memberships'),
('account:self:password_change','account','self_password_change','Change own password'),
('account:admin:status','account','admin_status','Manage account status'),
('account:admin:revoke','account','admin_revoke','Revoke account sessions'),
('membership:admin:write','membership','admin_write','Grant and remove memberships'),
('organization:admin:create','organization','admin_create','Create basic organization identity'),
('audit:global:read','audit','global_read','Read security audit log globally'),
('student_profile:self:read','student_profile','self_read','View own student profile'),
('student_profile:self:write','student_profile','self_write','Edit own student profile'),
('student_profile:shared:read','student_profile','shared_read','Read student profile through an active share'),
('student_profile:school:read','student_profile','school_read','Read student profile under delegated school scope'),
('cooperation:enterprise:create','cooperation','enterprise_create','Create demand under organization'),
('cooperation:school:review','cooperation','school_review','Review cooperation request in school scope'),
('placement:school:write','placement','school_write','Create placement under school approval'),
('internship:enterprise:task_write','internship','enterprise_task_write','Write tasks for entrusted placements'),
('internship:student:submission_write','internship','student_submission_write','Submit own internship deliverables'),
('internship:enterprise:evaluate','internship','enterprise_evaluate','Evaluate entrusted placements'),
('matching:enterprise:aggregate_read','matching','enterprise_aggregate_read','Read safe matching aggregates');

INSERT INTO role_permissions (role,permission_code) VALUES
('STUDENT','account:self:read'),('STUDENT','account:self:password_change'),
('STUDENT','student_profile:self:read'),('STUDENT','student_profile:self:write'),
('STUDENT','internship:student:submission_write'),
('ENTERPRISE','account:self:read'),('ENTERPRISE','account:self:password_change'),
('ENTERPRISE','student_profile:shared:read'),('ENTERPRISE','cooperation:enterprise:create'),
('ENTERPRISE','internship:enterprise:task_write'),('ENTERPRISE','internship:enterprise:evaluate'),
('ENTERPRISE','matching:enterprise:aggregate_read'),
('SCHOOL_STAFF','account:self:read'),('SCHOOL_STAFF','account:self:password_change'),
('SCHOOL_STAFF','student_profile:school:read'),('SCHOOL_STAFF','cooperation:school:review'),
('SCHOOL_STAFF','placement:school:write'),
('ADMIN','account:self:read'),('ADMIN','account:self:password_change'),
('ADMIN','account:admin:status'),('ADMIN','account:admin:revoke'),
('ADMIN','membership:admin:write'),('ADMIN','organization:admin:create'),
('ADMIN','audit:global:read');
