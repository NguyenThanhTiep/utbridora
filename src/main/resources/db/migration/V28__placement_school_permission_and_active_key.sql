-- V28: placement:school:write permission (referenced but not seeded in V22)
-- and unique active-key constraint to prevent duplicate APPROVED/ACTIVE placements
-- per student/term.

INSERT IGNORE INTO permissions(code, resource, action, description) VALUES
  ('placement:school:write', 'placement', 'school_write',
   'Create, approve, activate and cancel placements under school delegation');

INSERT IGNORE INTO role_permissions(role, permission_code) VALUES
  ('SCHOOL_STAFF', 'placement:school:write');

-- Unique active-key: at most one APPROVED or ACTIVE placement per student+term.
-- Uses a generated nullable column so the unique index only fires on those two states.
-- NULL values are excluded from UNIQUE in MySQL (multiple NULLs allowed).
ALTER TABLE placements
  ADD COLUMN active_student_term_key VARCHAR(74) GENERATED ALWAYS AS (
    CASE WHEN state IN ('APPROVED','ACTIVE')
         THEN CONCAT(student_user_id, ':', term_id)
         ELSE NULL
    END
  ) STORED,
  ADD UNIQUE INDEX uk_placement_active_student_term (active_student_term_key);
