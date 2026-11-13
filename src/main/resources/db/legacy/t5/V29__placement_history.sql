-- V29: Placement state transition history (append-only).
-- Required by yêu cầu.docx: "Tạo record + history + SUCCESS audit atomic; rollback để dữ liệu mồ côi"
CREATE TABLE placement_history (
  id             VARCHAR(36)  NOT NULL PRIMARY KEY,
  placement_id   VARCHAR(36)  NOT NULL,
  from_state     VARCHAR(32)  NULL,      -- NULL for initial PENDING_APPROVAL
  to_state       VARCHAR(32)  NOT NULL,
  actor_id       VARCHAR(36)  NOT NULL,
  reason_code    VARCHAR(64)  NULL,
  decision_ref   VARCHAR(120) NULL,
  operation_id   VARCHAR(36)  NOT NULL,  -- links to operation_records for idempotency
  changed_at     DATETIME(6)  NOT NULL,
  CONSTRAINT fk_ph_placement FOREIGN KEY (placement_id) REFERENCES placements(id),
  CONSTRAINT fk_ph_actor     FOREIGN KEY (actor_id)     REFERENCES users(id),
  INDEX ix_ph_placement_date (placement_id, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
