USE lab_manage;

CREATE TABLE IF NOT EXISTS app_migration (
  version VARCHAR(80) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP())
);

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='fulfillment_reason')=0,'ALTER TABLE reservation ADD COLUMN fulfillment_reason VARCHAR(1000) NULL','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='fulfillment_image_url')=0,'ALTER TABLE reservation ADD COLUMN fulfillment_image_url VARCHAR(500) NULL','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='fulfillment_operator_id')=0,'ALTER TABLE reservation ADD COLUMN fulfillment_operator_id BIGINT NULL','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='fulfillment_penalty')=0,'ALTER TABLE reservation ADD COLUMN fulfillment_penalty DECIMAL(4,1) NOT NULL DEFAULT 0','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='reservation' AND column_name='fulfillment_operator_id' AND referenced_table_name='lab_user')=0,'ALTER TABLE reservation ADD CONSTRAINT fk_reservation_fulfillment_operator FOREIGN KEY(fulfillment_operator_id) REFERENCES lab_user(id)','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

UPDATE reservation r JOIN credit_ledger c ON c.reservation_id=r.id AND c.idempotency_key=CONCAT('reservation:',r.id,':',r.fulfillment_status)
SET r.fulfillment_penalty=ABS(c.delta),r.fulfillment_operator_id=COALESCE(r.fulfillment_operator_id,r.reviewer_id)
WHERE r.fulfillment_status IN ('NOT_FULFILLED','OVERDUE_NOT_RETURNED') AND r.fulfillment_operator_id IS NULL;

INSERT IGNORE INTO app_migration(version) VALUES ('2026-10-30-fulfillment-accountability');
