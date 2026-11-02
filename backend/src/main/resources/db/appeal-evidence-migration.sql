USE lab_manage;

CREATE TABLE IF NOT EXISTS app_migration (
  version VARCHAR(80) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP())
);

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation_appeal' AND column_name='image_url')=0,'ALTER TABLE reservation_appeal ADD COLUMN image_url VARCHAR(500) NULL','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

INSERT IGNORE INTO app_migration(version) VALUES ('2026-11-02-appeal-evidence');
