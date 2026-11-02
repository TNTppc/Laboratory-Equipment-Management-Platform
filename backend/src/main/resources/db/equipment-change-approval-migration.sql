USE lab_manage;

CREATE TABLE IF NOT EXISTS app_migration (
  version VARCHAR(80) PRIMARY KEY,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP())
);

CREATE TABLE IF NOT EXISTS equipment_change_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  equipment_id BIGINT NOT NULL,
  applicant_id BIGINT NOT NULL,
  payload JSON NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  reviewer_id BIGINT NULL,
  review_comment VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
  reviewed_at DATETIME NULL,
  FOREIGN KEY(equipment_id) REFERENCES equipment(id),
  FOREIGN KEY(applicant_id) REFERENCES lab_user(id),
  FOREIGN KEY(reviewer_id) REFERENCES lab_user(id),
  INDEX idx_equipment_change_pending(equipment_id,status)
);

INSERT IGNORE INTO app_migration(version) VALUES ('2026-11-03-equipment-change-approval');
