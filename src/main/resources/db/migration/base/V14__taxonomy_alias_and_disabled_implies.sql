-- Demo taxonomy policy:
-- Alias resolution is exact after normalization. Implication rules are Should
-- and remain disabled until both the domain owner and PM approve with rationale.

CREATE TABLE skill_implies (
    id VARCHAR(36) NOT NULL,
    skill_id VARCHAR(36) NOT NULL,
    implies_skill_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
    rationale VARCHAR(1000) NULL,
    domain_owner_approved_by VARCHAR(36) NULL,
    domain_owner_approved_at DATETIME(6) NULL,
    pm_approved_by VARCHAR(36) NULL,
    pm_approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_skill_implies PRIMARY KEY (id),
    CONSTRAINT uk_skill_implies_pair UNIQUE (skill_id, implies_skill_id),
    CONSTRAINT fk_skill_implies_source FOREIGN KEY (skill_id)
        REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT fk_skill_implies_target FOREIGN KEY (implies_skill_id)
        REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT chk_skill_implies_not_self CHECK (skill_id <> implies_skill_id),
    CONSTRAINT chk_skill_implies_status CHECK (status IN ('DISABLED', 'ACTIVE')),
    CONSTRAINT chk_skill_implies_approval CHECK (
        status = 'DISABLED' OR (
            rationale IS NOT NULL AND CHAR_LENGTH(TRIM(rationale)) > 0
            AND domain_owner_approved_by IS NOT NULL AND domain_owner_approved_at IS NOT NULL
            AND pm_approved_by IS NOT NULL AND pm_approved_at IS NOT NULL
        )
    ),
    INDEX ix_skill_implies_source_status (skill_id, status),
    INDEX ix_skill_implies_target (implies_skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Add the concrete demo alias only when canonical JAVA exists; do not invent a
-- skill/category or remap a normalized alias that already belongs elsewhere.
INSERT INTO skill_aliases (id, skill_id, alias, normalized_alias, version, created_at)
SELECT UUID(), s.id, 'Java Core', 'java core', 1, UTC_TIMESTAMP(6)
FROM skills s
WHERE s.skill_code = 'JAVA'
  AND NOT EXISTS (
      SELECT 1 FROM skill_aliases a WHERE a.normalized_alias = 'java core'
  )
  AND NOT EXISTS (
      SELECT 1 FROM skills canonical WHERE canonical.normalized_name = 'java core'
  );
