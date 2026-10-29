USE lab_manage;

INSERT IGNORE INTO role(code,name) VALUES ('READ_ONLY_ADMIN','只读管理员');

CREATE TABLE IF NOT EXISTS campus_identity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  identity_no VARCHAR(9) NOT NULL UNIQUE,
  real_name VARCHAR(64) NOT NULL,
  person_type VARCHAR(20) NOT NULL,
  default_role_code VARCHAR(32) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
  FOREIGN KEY(default_role_code) REFERENCES role(code),
  CHECK(person_type IN ('STUDENT','STAFF'))
);

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='lab_user' AND column_name='identity_id')=0,'ALTER TABLE lab_user ADD COLUMN identity_id BIGINT NULL AFTER phone','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

CREATE TABLE IF NOT EXISTS lab_auto_approval (
  lab_id BIGINT PRIMARY KEY,
  enabled_until DATETIME NOT NULL,
  enabled_by BIGINT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()),
  FOREIGN KEY(lab_id) REFERENCES laboratory(id),
  FOREIGN KEY(enabled_by) REFERENCES lab_user(id)
);

ALTER TABLE reservation_review MODIFY reviewer_id BIGINT NULL;

UPDATE campus_identity SET identity_no='202601001' WHERE real_name='演示学生';
UPDATE campus_identity SET identity_no='2026001' WHERE real_name='实验室管理员';
UPDATE campus_identity SET identity_no='2026002' WHERE real_name='系统管理员';
UPDATE campus_identity SET identity_no='2026003' WHERE real_name='只读管理员';
UPDATE campus_identity SET identity_no='202601002' WHERE real_name='测试学生';

INSERT INTO campus_identity(identity_no,real_name,person_type,default_role_code) VALUES
('202601001','演示学生','STUDENT','USER'),
('2026001','实验室管理员','STAFF','LAB_ADMIN'),
('2026002','系统管理员','STAFF','SYS_ADMIN'),
('2026003','只读管理员','STAFF','READ_ONLY_ADMIN'),
('202601002','测试学生','STUDENT','USER'),
('202601003','张晨','STUDENT','USER'),('202601004','李雨桐','STUDENT','USER'),('202601005','王浩然','STUDENT','USER'),
('202602001','陈思远','STUDENT','USER'),('202602002','刘佳宁','STUDENT','USER'),('202602003','赵文博','STUDENT','USER'),
('202603001','周欣怡','STUDENT','USER'),('202603002','吴泽宇','STUDENT','USER'),('202603003','孙悦','STUDENT','USER'),
('202501001','林子涵','STUDENT','USER'),('202501002','郑凯','STUDENT','USER'),('202502001','黄静','STUDENT','USER'),
('202502002','何俊杰','STUDENT','USER'),('202503001','高雨晴','STUDENT','USER'),('202401001','罗一帆','STUDENT','USER'),
('202402001','宋嘉琪','STUDENT','USER'),
('2026004','周明远','STAFF','LAB_ADMIN'),('2026005','徐静','STAFF','LAB_ADMIN'),('2026006','蒋文浩','STAFF','READ_ONLY_ADMIN'),
('2025001','韩雪','STAFF','LAB_ADMIN'),('2025002','冯志强','STAFF','READ_ONLY_ADMIN'),('2024001','陆建国','STAFF','LAB_ADMIN')
ON DUPLICATE KEY UPDATE identity_no=VALUES(identity_no);

ALTER TABLE campus_identity MODIFY identity_no VARCHAR(9) NOT NULL;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='campus_identity' AND constraint_name='ck_campus_identity_number')=0,"ALTER TABLE campus_identity ADD CONSTRAINT ck_campus_identity_number CHECK ((person_type='STUDENT' AND identity_no REGEXP '^[0-9]{9}$') OR (person_type='STAFF' AND identity_no REGEXP '^[0-9]{7}$'))",'SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;

UPDATE lab_user u JOIN campus_identity ci ON ci.identity_no=CASE u.username WHEN 'student' THEN '202601001' WHEN 'labadmin' THEN '2026001' WHEN 'admin' THEN '2026002' ELSE '' END SET u.identity_id=ci.id WHERE u.identity_id IS NULL;
UPDATE lab_user SET email=CASE username WHEN 'student' THEN 'student@university.edu.cn' WHEN 'labadmin' THEN 'labadmin@university.edu.cn' WHEN 'admin' THEN 'admin@university.edu.cn' ELSE email END WHERE email IS NULL OR email='';
UPDATE lab_user SET phone=CASE username WHEN 'student' THEN '13800000001' WHEN 'labadmin' THEN '13800000002' WHEN 'admin' THEN '13800000003' ELSE phone END WHERE phone IS NULL OR phone='';

INSERT INTO lab_user(username,password_hash,real_name,email,phone,identity_id)
SELECT 'readonly','$2a$10$CePvciAAo/iHSWrKymmQx.D3yYQFtx3MLwELrN8C9EHndzjyNbf0i',ci.real_name,'readonly@university.edu.cn','13800000004',ci.id
FROM campus_identity ci WHERE ci.identity_no='2026003' AND NOT EXISTS(SELECT 1 FROM lab_user WHERE username='readonly');
INSERT IGNORE INTO user_role SELECT u.id,r.id FROM lab_user u JOIN role r ON r.code='READ_ONLY_ADMIN' WHERE u.username='readonly';

ALTER TABLE lab_user MODIFY email VARCHAR(128) NOT NULL;
ALTER TABLE lab_user MODIFY phone VARCHAR(32) NOT NULL;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='lab_user' AND index_name='uk_lab_user_identity')=0,'CREATE UNIQUE INDEX uk_lab_user_identity ON lab_user(identity_id)','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name='lab_user' AND constraint_name='fk_lab_user_identity')=0,'ALTER TABLE lab_user ADD CONSTRAINT fk_lab_user_identity FOREIGN KEY(identity_id) REFERENCES campus_identity(id)','SELECT 1');
PREPARE migration_stmt FROM @ddl; EXECUTE migration_stmt; DEALLOCATE PREPARE migration_stmt;
ALTER TABLE lab_user MODIFY identity_id BIGINT NOT NULL;

INSERT IGNORE INTO app_migration(version) VALUES ('2026-10-29-identity-and-approval');
