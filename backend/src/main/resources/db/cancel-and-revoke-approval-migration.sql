USE lab_manage;

CREATE TABLE IF NOT EXISTS app_migration (
  version VARCHAR(80) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP())
);

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='auto_credit_approved')=0,'ALTER TABLE reservation ADD COLUMN auto_credit_approved BOOLEAN NOT NULL DEFAULT FALSE','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

UPDATE reservation SET auto_credit_approved=TRUE
WHERE auto_credit_approved=FALSE AND reviewer_id IS NULL AND review_comment='信誉积分达到90分，系统自动通过';

INSERT IGNORE INTO app_migration(version) VALUES ('2026-11-03-cancel-and-revoke-approval');
