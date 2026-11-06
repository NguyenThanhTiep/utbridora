INSERT INTO permissions (code, resource, action, description)
VALUES ('taxonomy:skills:write', 'taxonomy', 'skills_write', 'Create and manage the skill taxonomy');

INSERT INTO role_permissions (role, permission_code)
VALUES ('ADMIN', 'taxonomy:skills:write');
