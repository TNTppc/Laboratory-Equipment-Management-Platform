USE lab_manage;

CREATE TABLE IF NOT EXISTS training_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(120) NOT NULL,
  type VARCHAR(20) NOT NULL,
  lab_id BIGINT NULL,
  validity_months INT NOT NULL DEFAULT 12,
  description VARCHAR(1000) NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
  FOREIGN KEY(lab_id) REFERENCES laboratory(id),
  CHECK(type IN ('SAFETY_TRAINING','OPERATION_EXAM')),
  CHECK(validity_months BETWEEN 0 AND 120)
);

CREATE TABLE IF NOT EXISTS equipment_training_requirement (
  equipment_id BIGINT NOT NULL,
  training_item_id BIGINT NOT NULL,
  PRIMARY KEY(equipment_id,training_item_id),
  FOREIGN KEY(equipment_id) REFERENCES equipment(id),
  FOREIGN KEY(training_item_id) REFERENCES training_item(id),
  INDEX idx_equipment_training_item(training_item_id)
);

CREATE TABLE IF NOT EXISTS training_grant (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  training_item_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  source VARCHAR(16) NOT NULL,
  score DECIMAL(5,1) NULL,
  evidence_url VARCHAR(500) NULL,
  remark VARCHAR(500) NULL,
  reviewer_id BIGINT NULL,
  review_comment VARCHAR(500) NULL,
  applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
  reviewed_at DATETIME NULL,
  granted_at DATETIME NULL,
  expires_at DATETIME NULL,
  reminded_on DATE NULL,
  FOREIGN KEY(user_id) REFERENCES lab_user(id),
  FOREIGN KEY(training_item_id) REFERENCES training_item(id),
  FOREIGN KEY(reviewer_id) REFERENCES lab_user(id),
  CHECK(status IN ('PENDING','APPROVED','REJECTED','REVOKED')),
  CHECK(source IN ('TRAINING','EXAM','ADMIN')),
  INDEX idx_training_grant_user(user_id,training_item_id,status),
  INDEX idx_training_grant_expiry(status,expires_at)
);

INSERT INTO training_item(code,name,type,lab_id,validity_months,description) VALUES
('TR-001','实验室安全基础','SAFETY_TRAINING',NULL,12,'进入实验室前必须完成的安全培训，覆盖用电、消防、应急疏散和危险品管理。'),
('TR-002','协作机器人操作考试','OPERATION_EXAM',(SELECT id FROM laboratory WHERE name='智能制造实验室'),6,'协作机器人上下电、示教、急停与安全围栏操作考试，80 分及格。'),
('TR-003','激光设备安全操作','SAFETY_TRAINING',(SELECT id FROM laboratory WHERE name='智能制造实验室'),12,'激光切割机排风、防火、材料限制和护目要求。'),
('TR-004','电子测量仪器操作考试','OPERATION_EXAM',(SELECT id FROM laboratory WHERE name='电子信息实验室'),6,'示波器、信号源和万用表的接线、量程与安全操作考试。')
ON DUPLICATE KEY UPDATE name=VALUES(name),type=VALUES(type),lab_id=VALUES(lab_id),validity_months=VALUES(validity_months),description=VALUES(description);

INSERT IGNORE INTO equipment_training_requirement(equipment_id,training_item_id)
SELECT e.id,ti.id FROM equipment e JOIN training_item ti ON ti.code IN ('TR-001','TR-002') WHERE e.code='EQ-008';

INSERT IGNORE INTO equipment_training_requirement(equipment_id,training_item_id)
SELECT e.id,ti.id FROM equipment e JOIN training_item ti ON ti.code IN ('TR-001','TR-003') WHERE e.code='EQ-010';

INSERT IGNORE INTO equipment_training_requirement(equipment_id,training_item_id)
SELECT e.id,ti.id FROM equipment e JOIN training_item ti ON ti.code IN ('TR-001','TR-004') WHERE e.code='EQ-001';

INSERT INTO training_grant(user_id,training_item_id,status,source,reviewer_id,applied_at,reviewed_at,granted_at,expires_at)
SELECT u.id,ti.id,'APPROVED','TRAINING',r.id,DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 MONTH),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 MONTH),DATE_SUB(UTC_TIMESTAMP(),INTERVAL 2 MONTH),DATE_ADD(UTC_TIMESTAMP(),INTERVAL 10 MONTH)
FROM lab_user u JOIN training_item ti ON ti.code='TR-001' LEFT JOIN lab_user r ON r.username='labadmin'
WHERE u.username='student' AND NOT EXISTS(SELECT 1 FROM training_grant g WHERE g.user_id=u.id AND g.training_item_id=ti.id);

INSERT INTO training_grant(user_id,training_item_id,status,source,remark,applied_at)
SELECT u.id,ti.id,'PENDING','TRAINING','已完成线下培训，申请参加操作考试',UTC_TIMESTAMP()
FROM lab_user u JOIN training_item ti ON ti.code='TR-002'
WHERE u.username='student' AND NOT EXISTS(SELECT 1 FROM training_grant g WHERE g.user_id=u.id AND g.training_item_id=ti.id);

INSERT IGNORE INTO app_migration(version) VALUES ('2026-11-04-training-authorization');
