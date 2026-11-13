-- DEV08 bổ sung: release doanh nghiệp được bảo vệ. Ops phải đăng ký và kích hoạt batch dữ liệu đã đóng băng
-- trước khi phục vụ thống kê; không có batch hoạt động thì hệ thống từ chối theo cơ chế fail-closed.
CREATE TABLE matching_privacy_release_settings (
  id TINYINT NOT NULL PRIMARY KEY,
  enabled BIT NOT NULL DEFAULT b'0',
  CONSTRAINT ck_matching_privacy_release_settings_singleton CHECK (id=1)
) ENGINE=InnoDB;
INSERT INTO matching_privacy_release_settings(id,enabled) VALUES (1,b'0');

CREATE TABLE matching_demo_batches (
  demo_batch_id VARCHAR(36) NOT NULL PRIMARY KEY,
  school_org_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  dataset_snapshot_id VARCHAR(36) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_matching_demo_batch_scope (demo_batch_id, school_org_id, term_id),
  CONSTRAINT fk_matching_batch_dataset FOREIGN KEY (dataset_snapshot_id) REFERENCES student_snapshot_sets(id),
  CONSTRAINT fk_matching_batch_term FOREIGN KEY (term_id, school_org_id) REFERENCES terms(id, school_org_id)
) ENGINE=InnoDB;

CREATE TABLE matching_active_demo_batches (
  school_org_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  demo_batch_id VARCHAR(36) NOT NULL,
  PRIMARY KEY (school_org_id, term_id),
  CONSTRAINT fk_matching_active_batch FOREIGN KEY (demo_batch_id, school_org_id, term_id)
    REFERENCES matching_demo_batches(demo_batch_id, school_org_id, term_id)
) ENGINE=InnoDB;

CREATE TABLE matching_privacy_releases (
  enterprise_org_id VARCHAR(36) NOT NULL,
  school_org_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  demo_batch_id VARCHAR(36) NOT NULL,
  fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  release_id VARCHAR(36) NOT NULL,
  match_run_id VARCHAR(36) NOT NULL,
  dto_json JSON NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (enterprise_org_id, school_org_id, term_id, demo_batch_id),
  UNIQUE KEY uq_matching_release_id (release_id),
  CONSTRAINT fk_matching_release_batch FOREIGN KEY (demo_batch_id, school_org_id, term_id)
    REFERENCES matching_demo_batches(demo_batch_id, school_org_id, term_id),
  CONSTRAINT fk_matching_release_enterprise FOREIGN KEY (enterprise_org_id) REFERENCES organizations(id),
  CONSTRAINT fk_matching_release_run FOREIGN KEY (match_run_id) REFERENCES match_runs(id)
) ENGINE=InnoDB;
