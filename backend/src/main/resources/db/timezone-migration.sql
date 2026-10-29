USE lab_manage;

CREATE TABLE IF NOT EXISTS app_migration (
  version VARCHAR(80) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP())
);

SET @migration_version = '2026-10-29-unify-datetime-to-utc';
SET @needs_migration = NOT EXISTS(SELECT 1 FROM app_migration WHERE version = @migration_version);

-- starts_at and ends_at were already submitted as UTC and must not be shifted.
UPDATE reservation SET submitted_at = DATE_SUB(submitted_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE reservation_review SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE repair_ticket SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR), updated_at = DATE_SUB(updated_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE repair_record SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE notification SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE operation_log SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE lab_user SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;
UPDATE equipment SET created_at = DATE_SUB(created_at, INTERVAL 8 HOUR) WHERE @needs_migration = 1;

INSERT IGNORE INTO app_migration(version) VALUES (@migration_version);

ALTER TABLE lab_user MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE equipment MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE reservation MODIFY submitted_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE reservation_review MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE repair_ticket
  MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
  MODIFY updated_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE repair_record MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE notification MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
ALTER TABLE operation_log MODIFY created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP());
