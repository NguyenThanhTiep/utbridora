-- BC12: T2 owns versioned academic fixtures. No invented production majors or global fallback.
CREATE TABLE academic_catalogs (
  term_id VARCHAR(36) NOT NULL PRIMARY KEY,
  school_org_id VARCHAR(36) NOT NULL,
  catalog_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  CONSTRAINT fk_academic_term FOREIGN KEY (term_id,school_org_id) REFERENCES terms(id,school_org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE academic_catalog_entries (
  term_id VARCHAR(36) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(128) NOT NULL,
  major_codes JSON NOT NULL,
  status VARCHAR(16) NOT NULL,
  PRIMARY KEY (term_id,kind,code),
  CONSTRAINT fk_academic_entry_catalog FOREIGN KEY (term_id) REFERENCES academic_catalogs(term_id),
  CONSTRAINT ck_academic_kind CHECK (kind IN ('MAJOR','SUBJECT','ORIENTATION')),
  CONSTRAINT ck_academic_status CHECK (status IN ('ACTIVE','INACTIVE')),
  CONSTRAINT ck_academic_major_codes CHECK (JSON_TYPE(major_codes)='ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
