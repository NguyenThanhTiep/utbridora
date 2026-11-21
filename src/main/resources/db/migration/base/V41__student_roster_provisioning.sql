-- Additive owner T2 provisioning storage. Old roster/approval/profile rows remain intact.
ALTER TABLE student_rosters
  MODIFY student_code VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 1,
  ADD CONSTRAINT ck_roster_version CHECK(version BETWEEN 1 AND 9007199254740991),
  ADD CONSTRAINT uk_student_roster_school_user UNIQUE(school_org_id,user_id);

CREATE TABLE student_roster_terms (
  roster_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  major_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  catalog_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  cohort_code VARCHAR(64) NULL,
  class_code VARCHAR(64) NULL,
  version BIGINT NOT NULL DEFAULT 1,
  PRIMARY KEY(roster_id,term_id),
  FOREIGN KEY(roster_id) REFERENCES student_rosters(id),
  FOREIGN KEY(term_id) REFERENCES terms(id),
  CHECK(version BETWEEN 1 AND 9007199254740991)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Backfill only authoritative historical roster/term links with a known catalog.
-- An unbound/ambiguous legacy row is left for explicit review, not silently linked.
INSERT INTO student_roster_terms(roster_id,term_id,major_code,catalog_version)
SELECT r.id,t.id,r.major_code,a.catalog_version
FROM student_rosters r JOIN terms t ON t.school_org_id=r.school_org_id AND t.term_code=r.term_code
JOIN academic_catalogs a ON a.term_id=t.id AND a.school_org_id=r.school_org_id;
