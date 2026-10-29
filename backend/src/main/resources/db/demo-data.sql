USE lab_manage;

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-004','台式万用表',c.id,l.id,'Keysight 34461A','B305-02',u.id,'六位半高精度数字万用表','测量前确认量程和接线，使用后整理测试线','2023-03-15','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='电子信息实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='测量仪器' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-004');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-005','函数信号发生器',c.id,l.id,'Rigol DG1022Z','B305-03',u.id,'双通道函数与任意波形发生器','接入电路前核对输出幅值和偏置','2022-09-20','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='电子信息实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='测量仪器' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-005');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-006','直流稳压电源',c.id,l.id,'ITECH IT6332A','B305-05',u.id,'三路可编程直流稳压电源','禁止短路输出，调压前确认负载额定值','2024-01-10','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='电子信息实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='测量仪器' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-006');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-007','高性能计算工作站',c.id,l.id,'Lenovo ThinkStation P620','A201-09',u.id,'用于仿真、建模和机器视觉训练','禁止安装来源不明软件，大型任务需提前预约','2024-05-08','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='智能制造实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='计算设备' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-007');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-008','协作机器人',c.id,l.id,'Universal Robots UR5e','A201-12',u.id,'六轴协作机器人实验平台','操作前完成安全培训，运行时保持安全距离','2023-11-02','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='智能制造实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='加工设备' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-008');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-009','桌面级3D打印机',c.id,l.id,'Raise3D Pro3','A201-15',u.id,'双喷头熔融沉积成型设备','打印时不得打开舱门，完成后清理打印平台','2022-12-12','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='智能制造实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='加工设备' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-009');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-010','激光切割机',c.id,l.id,'Han''s Laser CMA6040','A201-18',u.id,'非金属材料激光切割与雕刻设备','必须开启排风，禁止加工未知或含氯材料','2021-07-26','MAINTENANCE'
FROM equipment_category c JOIN laboratory l ON l.name='智能制造实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='加工设备' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-010');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-011','工业视觉相机套件',c.id,l.id,'Basler ace 2','A201-11',u.id,'含工业相机、镜头和光源控制器','拆装镜头前关闭电源，避免触碰传感器','2024-03-18','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='智能制造实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='测量仪器' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-011');

INSERT INTO equipment(code,name,category_id,lab_id,brand_model,location,owner_id,description,usage_rules,purchase_date,status)
SELECT 'EQ-012','便携式数据采集仪',c.id,l.id,'NI USB-6343','B305-08',u.id,'多功能USB数据采集设备','接线时断开被测系统电源，严禁输入过压','2023-06-30','AVAILABLE'
FROM equipment_category c JOIN laboratory l ON l.name='电子信息实验室' LEFT JOIN lab_user u ON u.username='labadmin'
WHERE c.name='测量仪器' AND NOT EXISTS(SELECT 1 FROM equipment WHERE code='EQ-012');