-- Preserve historical user actors; a scheduled activation has no impersonated user.
ALTER TABLE placement_history
 MODIFY COLUMN actor_id VARCHAR(36) NULL,
 ADD COLUMN actor_kind VARCHAR(8) NOT NULL DEFAULT 'USER',
 ADD CONSTRAINT ck_placement_history_actor CHECK (
   (actor_kind='USER' AND actor_id IS NOT NULL) OR
   (actor_kind='SYSTEM' AND actor_id IS NULL));
