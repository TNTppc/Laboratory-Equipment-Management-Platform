USE lab_manage;

CREATE TABLE IF NOT EXISTS app_migration (
  version VARCHAR(80) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP())
);

SET @migration_version = '2026-10-30-credit-auto-approval-appeal';
SET @needs_migration = NOT EXISTS(SELECT 1 FROM app_migration WHERE version=@migration_version);

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_user' AND column_name='credit_score')=0,'ALTER TABLE lab_user ADD COLUMN credit_score DECIMAL(5,1) NOT NULL DEFAULT 100.0, ADD COLUMN credit_recovered_on DATE NULL','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='reject_reason')=0,'ALTER TABLE reservation ADD COLUMN reject_reason VARCHAR(32) NULL, ADD COLUMN fulfillment_status VARCHAR(24) NOT NULL DEFAULT ''NOT_STARTED''','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

CREATE TABLE IF NOT EXISTS auto_approval_rule (id BIGINT PRIMARY KEY AUTO_INCREMENT, scope_type VARCHAR(16) NOT NULL, scope_id BIGINT NOT NULL DEFAULT 0, action VARCHAR(16) NOT NULL, enabled_until DATETIME NOT NULL, enabled_by BIGINT NOT NULL, updated_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()), FOREIGN KEY(enabled_by) REFERENCES lab_user(id), UNIQUE KEY uk_auto_rule_scope(scope_type,scope_id), INDEX idx_auto_rule_active(enabled_until));
DELETE older FROM auto_approval_rule older JOIN auto_approval_rule newer ON older.scope_type='GLOBAL' AND older.scope_id IS NULL AND newer.scope_type='GLOBAL' AND newer.scope_id=0;
DELETE older FROM auto_approval_rule older JOIN auto_approval_rule newer ON older.scope_type='GLOBAL' AND newer.scope_id IS NULL AND newer.id>older.id AND older.scope_id IS NULL;
UPDATE auto_approval_rule SET scope_id=0 WHERE scope_type='GLOBAL' AND scope_id IS NULL;
ALTER TABLE auto_approval_rule MODIFY COLUMN scope_id BIGINT NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS credit_ledger (id BIGINT PRIMARY KEY AUTO_INCREMENT, user_id BIGINT NOT NULL, reservation_id BIGINT, delta DECIMAL(4,1) NOT NULL, reason VARCHAR(40) NOT NULL, idempotency_key VARCHAR(120) NOT NULL UNIQUE, created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()), FOREIGN KEY(user_id) REFERENCES lab_user(id), FOREIGN KEY(reservation_id) REFERENCES reservation(id));
CREATE TABLE IF NOT EXISTS reservation_appeal (id BIGINT PRIMARY KEY AUTO_INCREMENT, reservation_id BIGINT NOT NULL, applicant_id BIGINT NOT NULL, reason VARCHAR(1000) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING', reviewer_id BIGINT, review_comment VARCHAR(1000), created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()), reviewed_at DATETIME, FOREIGN KEY(reservation_id) REFERENCES reservation(id), FOREIGN KEY(applicant_id) REFERENCES lab_user(id), FOREIGN KEY(reviewer_id) REFERENCES lab_user(id), UNIQUE KEY uk_appeal_reservation(reservation_id));

INSERT IGNORE INTO auto_approval_rule(scope_type,scope_id,action,enabled_until,enabled_by,updated_at)
SELECT 'LAB',lab_id,'APPROVE',enabled_until,enabled_by,updated_at FROM lab_auto_approval;
UPDATE lab_user SET credit_score=100.0 WHERE credit_score IS NULL;
INSERT IGNORE INTO app_migration(version) VALUES (@migration_version);
