-- T1-06: permission ceilings only. Resource/owner policies remain mandatory for every request.
INSERT INTO permissions(code,resource,action,description) VALUES
 ('file:upload','file','upload','Private upload with current resource write authority'),
 ('file:download','file','download','Private metadata/content with current resource read authority');
INSERT INTO role_permissions(role,permission_code) VALUES
 ('ADMIN','file:upload'),('ADMIN','file:download'),
 ('STUDENT','file:upload'),('STUDENT','file:download'),
 ('ENTERPRISE','file:upload'),('ENTERPRISE','file:download'),
 ('SCHOOL_STAFF','file:upload'),('SCHOOL_STAFF','file:download');
CREATE INDEX ix_file_binding_resource ON file_bindings(binding_kind,resource_id,purpose);
-- Existing metadata and bindings are preserved. No backfill fabricates CLEAN or storage objects.
