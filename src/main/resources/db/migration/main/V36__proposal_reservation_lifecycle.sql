CREATE TABLE proposal_reservation_history (
  id VARCHAR(36) PRIMARY KEY,
  proposal_id VARCHAR(36) NOT NULL,
  proposal_version BIGINT NOT NULL,
  from_state VARCHAR(32) NOT NULL,
  to_state VARCHAR(32) NOT NULL,
  previous_until DATETIME(6) NOT NULL,
  next_until DATETIME(6) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  actor_kind VARCHAR(16) NOT NULL,
  UNIQUE KEY uk_proposal_reservation_history (proposal_id,proposal_version),
  CONSTRAINT fk_proposal_reservation_history FOREIGN KEY (proposal_id) REFERENCES proposal_items(id),
  CONSTRAINT ck_proposal_reservation_actor CHECK (actor_kind='SYSTEM')
);

-- Reconcile mutable unplaced proposals from earlier releases. Do not rewrite
-- invitation decisions, immutable criteria/snapshots, or any Placement history.
INSERT INTO proposal_reservation_history
  (id,proposal_id,proposal_version,from_state,to_state,previous_until,next_until,occurred_at,actor_kind)
SELECT UUID(),x.id,x.version+1,x.state,x.state,x.reservation_until,x.correct_until,UTC_TIMESTAMP(6),'SYSTEM'
FROM (
  SELECT i.id,i.version,i.state,i.reservation_until,
    CASE WHEN i.state='INVITED' THEN v.expires_at ELSE
      LEAST(DATE_ADD(v.decided_at,INTERVAL 7 DAY),
        CAST(REPLACE(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.deadline')),'T',' '),'Z','') AS DATETIME(6)),
        COALESCE((SELECT MIN(CAST(REPLACE(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(s.data_json,'$.expiresAt')),'T',' '),'Z','') AS DATETIME(6)))
          FROM student_consent_lineages l JOIN student_consents s ON s.id=l.current_consent_id
          WHERE l.student_user_id=i.student_user_id AND l.school_org_id=c.school_org_id
            AND l.term_id=c.term_id AND l.recipient_org_id=c.enterprise_org_id
            AND l.purpose='INTERNSHIP_SELECTION'), '9999-12-31 23:59:59')) END AS correct_until
  FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id
    JOIN cooperations c ON c.id=b.cooperation_id JOIN demands d ON d.id=c.demand_id
    JOIN internship_invitations v ON v.id=i.invitation_id
  WHERE i.placement_id IS NULL AND
    ((i.state='INVITED' AND v.state='PENDING') OR
     (i.state IN ('ACCEPTED','SHARED','ENTERPRISE_ACCEPTED') AND v.state='ACCEPTED'))
) x WHERE x.correct_until IS NOT NULL AND x.correct_until<>x.reservation_until;

UPDATE proposal_items i JOIN proposal_reservation_history h
  ON h.proposal_id=i.id AND h.proposal_version=i.version+1
SET i.reservation_until=h.next_until,i.version=h.proposal_version;
