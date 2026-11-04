# 实验室设备预约与维护管理系统

基于 Spring Boot 3 与 Vue 3 的前后端分离演示系统，覆盖设备档案、预约审批、报修维护、通知和管理统计。后端为单体应用，使用 MySQL 持久化数据。

## 技术栈

- 后端：Java 17、Spring Boot 3.3.5、Maven、Spring Security、JWT、MyBatis-Plus、Bean Validation、springdoc OpenAPI、MySQL 8。
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts。

## 环境要求

安装并确保命令可用：JDK 17、Maven 3.8+、Node.js 20+、npm 10+、MySQL 8。当前工程已在 Java 17、Maven 3.10、Node 24、npm 11、MySQL 8.0 上验证。

## 快速开始（Windows PowerShell 脚本）

仓库自带两个脚本，用于本机一键初始化与启动：

```powershell
# 1) 首次初始化：检查环境、建库导入演示数据、生成 backend/.env.local、安装依赖并构建后端
powershell -ExecutionPolicy Bypass -File .\scripts\init.ps1

# 2) 启动前后端（后台运行，等待端口就绪后输出地址）
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1

# 3) 停止前后端
powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -Stop
```

- `init.ps1` 参数：`-DbHost`（默认 `localhost`）、`-DbPort`（`3306`）、`-DbUser`（`root`）、`-DbPassword`、`-JwtSecret`、`-MysqlPath`、`-SkipInstall`、`-SkipBuild`、`-ForceEnv`。未提供 `-DbPassword` 时会安全提示输入；数据库密码和随机 JWT 密钥只写入被 git 忽略的 `backend/.env.local`，不会显示在终端。
- 已有数据库不会被覆盖：只有 `lab_manage` 不存在或没有任何表时才执行 `schema.sql` + `demo-data.sql`；新库的 `schema.sql` 已包含全部结构，脚本会把历史迁移版本登记为已应用。重复执行是安全的。
- `start.ps1` 参数：`-Stop` 停止前后端、`-Build` 强制重新构建后端 jar、`-NoFrontend` 只启动后端、`-EnvFile` 指定其他环境变量文件（默认 `backend/.env.local`）。启动日志在 `backend/logs` 与 `frontend/logs`。脚本会校验 jar 是否有 Spring Boot 主清单，损坏或缺失时自动重新构建。
- 两个脚本需要 Windows PowerShell 5.1+；在其他终端调用时使用 `powershell -ExecutionPolicy Bypass -File ...` 绕过执行策略限制。

## 初始化数据库

先启动 MySQL 8。Windows PowerShell 示例（按本机安装位置使用 `mysql` 命令）：

```powershell
# 全新初始化（只执行一次）
mysql --default-character-set=utf8mb4 -u root -p < backend/src/main/resources/db/schema.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/demo-data.sql

# 已有数据库升级时按顺序执行（可重复执行，已应用版本记录在 app_migration 表）
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/timezone-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/identity-and-approval-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/credit-and-auto-approval-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/fulfillment-accountability-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/appeal-evidence-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/cancel-and-revoke-approval-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/equipment-change-approval-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/training-authorization-migration.sql
```

`schema.sql` 是最新全量结构（含全部表、字段和演示账号/实验室/设备），只用于全新数据库；`demo-data.sql` 按设备编号补充 12 台演示设备。迁移脚本用于把旧数据库升级到当前结构，可重复执行；新库不需要再执行迁移。不要在已有数据库上重新执行 `schema.sql`。全库时间字段统一保存 UTC，前端按当前电脑时区展示，实验室开放时间按 `Asia/Shanghai` 校验。

## 配置与启动

复制 `backend/.env.example` 中列出的变量到当前终端环境。Spring Boot 不会自动读取 `.env` 文件，必须在启动进程前设置环境变量。不得将真实密码或生产 JWT 密钥提交到版本库。使用 `scripts/start.ps1` 时会自动读取 `backend/.env.local`（由 `scripts/init.ps1` 生成）并注入环境变量，等价于下面的手动设置。

后端：

```powershell
cd backend
$env:DB_URL='jdbc:mysql://localhost:3306/lab_manage?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<本机 MySQL 密码>'
$env:JWT_SECRET='<至少 32 字节的随机密钥>'
$env:UPLOAD_DIR='uploads'
mvn -s maven-settings.xml test
mvn -s maven-settings.xml package -DskipTests
java -jar target/lab-equipment-system-1.0.0.jar
```

`maven-settings.xml` 将 Maven 本地仓库放在项目的 `backend/.m2/` 下，解决某些 Windows 安装目录不可写的问题。也可以在后端目录运行 `mvn test`（若全局 Maven settings 正常可写）。数据库连接会强制会话使用 UTC；前端根据当前电脑时区自动转换并以中文日期显示。生产部署必须设置随机、独立且妥善保管的 `JWT_SECRET`，不可使用示例值。

前端另开终端：

```powershell
cd frontend
npm ci
npm run dev
```

开发地址：<http://localhost:5173>。Vite 将 `/api` 代理到 `VITE_API_PROXY_TARGET`，默认 `http://localhost:8080`；可按需设置该环境变量。前端构建与依赖审计：

```powershell
npm run build
npm audit --omit=dev
```

## 演示账号

四个账号的演示密码均为 `LabDemo123!`：

| 用户名 | 角色 | 数据范围 |
| --- | --- | --- |
| `student` | 普通用户 | 自己的预约与报修，可查看设备 |
| `labadmin` | 实验室管理员 | 授权的“智能制造实验室” |
| `admin` | 系统管理员 | 全局管理数据 |
| `readonly` | 只读管理员 | 查看全局统计、用户、设备、预约和报修，不可修改或审批 |

演示账号仅用于本地演示。对外部署前应更换密码、JWT 密钥，并使用专用的最小权限数据库账号。

## 功能与接口

- 登录、注册、退出、当前用户和联系方式：`/api/auth/login`、`/api/auth/register`、`/api/auth/logout`、`/api/auth/me`、`/api/auth/contacts`。注册必须匹配系统管理员预登记的校园身份。
- 注销账号：在个人信息页验证当前密码后软注销（`DELETE /api/auth/me`）；账号停用、历史业务数据保留，已有 JWT 随即失效。系统管理员不能自行注销，避免系统失去管理权限。
- 校园身份编号使用纯数字：学生学号为 `4 位入学年份 + 2 位班级编码 + 3 位编号`（示例 `202601001`），教职工工号为 `4 位入职年份 + 3 位进校流水号`（示例 `2026001`）。本项目将需求中的“班级”按 2 位数字编码实现。
- 演示数据库迁移预置了 18 条学生和 9 条教职工身份；其中学生和教职工的姓名、编号为可注册演示数据。执行 `backend/src/main/resources/db/identity-and-approval-migration.sql` 可幂等补齐现有数据库身份记录。
- 设备和基础选项：`/api/equipment`、`/api/equipment/{id}`、`/api/labs`、`/api/categories`。设备档案的“变更审批”列只对管理员显示，普通学生看不到待审批变更标记。设备档案的“责任人”只有系统管理员可以直接修改；实验室管理员只能修改自己负责实验室的设备（其他实验室的设备在列表中只读，`/auth/me` 返回 `labIds` 供前端判断），提交的修改保存在 `equipment_change_request` 中，需系统管理员通过 `GET /api/equipment/change-requests`、`POST /api/equipment/change-requests/{id}/review` 审批后才写入设备档案。
- 培训与准入授权：`GET /api/trainings`、`GET /api/trainings/mine`、`POST /api/trainings/{id}/apply`、`GET /api/trainings/access/{equipmentId}`；管理员使用 `GET/POST/PUT /api/trainings`、`GET /api/trainings/items`、`GET /api/trainings/grants`、`POST /api/trainings/grants/{id}/review`、`POST /api/trainings/grants/{id}/revoke`、`PUT /api/equipment/{id}/training-requirements`。安全培训和仪器操作考试按项目建模，同一项目可绑定多台设备；用户必须持有设备要求的每一项有效授权才能预约。当前版本由管理员在审批时确认线下培训或考试成绩后发放授权，不包含在线题库和自动判分。
- 预约：`/api/reservations` 及其审批、取消动作；管理员可按设备、设备分类、实验室或全局临时开启 1、4 或 8 小时自动通过/自动拒绝，优先级为设备 > 分类 > 实验室 > 全局。已通过的预约取消时按距开始时间扣除信誉积分：48 小时以上扣 1 分、24 至 48 小时扣 2 分、不足 24 小时扣 4 分；待审批预约取消不扣分。学生取消前可通过 `GET /api/reservations/{id}/cancel-preview` 查看本次扣分并阅读“退约规则”。
- 履约申诉：学生提交申诉后，管理员在独立的“履约申诉”页面查看并审核（`GET /api/reservations/appeals`、`POST /api/reservations/appeals/{id}/review`），实验室管理员只看负责实验室的申诉，系统管理员看全局，只读管理员可查看但不能审核。
- 信誉与履约：普通用户初始信誉分为 100，每日恢复 0.5 分且不超过 100；低于 60 分禁止预约，60 至 90 分需要人工审批，达到 90 分自动通过：提交预约时学生端会立即弹窗提示自动通过结果，同时通知实验室管理员和系统管理员。管理员可在预约开始前 2 小时以前通过 `POST /api/reservations/{id}/revoke-approval` 撤销任意已通过预约的通过结果，预约退回待审批并通知学生；距开始不足 2 小时不能撤销。正常履约每次恢复 0.5 分；连续未履约从扣 3 分起、最高 5 分，连续逾期未归还从扣 5 分起、最高 10 分。管理员登记违约须填写原因，可上传图片；申诉须在预约结束后 7 天内提交，且每次预约只能申诉一次。申诉通过后返还本次扣除的信誉积分，并把履约状态改为“已完成”。
- 站内通知：覆盖预约提交（实验室管理员、系统管理员）、审核结果/自动通过/自动驳回/超时驳回（学生）、信誉积分自动通过（实验室管理员、系统管理员）、已通过预约被取消（实验室管理员、系统管理员）、撤销通过（学生）、履约登记结果与申诉审核结果（学生）、新申诉待审核（实验室管理员、系统管理员）、报修提交（管理员）、维修进度（报修人）、设备变更提交（系统管理员）与变更审批结果（申请人）、培训授权申请待审批（实验室管理员、系统管理员）、培训授权审批结果与到期提醒（学生）、培训授权被撤销（学生）。
- 报修和处理：`/api/repairs`、`/api/repairs/{id}`、`/api/repairs/{id}/process`；故障图片通过 `/api/uploads/repair-image` 上传并保存到 `UPLOAD_DIR`。
- 管理用户、实验室、分类：`/api/admin/**`；仅系统管理员可用。
- 管理统计：`/api/stats`；实验室管理员仅统计授权实验室。
- 管理操作审计：`/api/reservations/audit`，系统管理员和只读管理员可查全局日志，实验室管理员仅查看授权实验室相关记录（最近 500 条）。
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>；Swagger UI：<http://localhost:8080/swagger-ui.html>。

接口返回统一的 `{ success, message, data }` 结构。身份认证采用 Bearer JWT；后端执行角色和实验室范围校验，前端路由/按钮控制只用于体验优化。

## 业务规则和统计口径

- 预约区间为左闭右开 `[开始, 结束)`；冲突条件为既有开始早于请求结束且既有结束晚于请求开始。待审批和已通过占用时间，驳回和取消不占用；提交与审批通过都会复查，按设备加锁并在事务中处理。
- 取消扣分按取消操作时刻与预约开始时间的间隔计算，扣分写入 `credit_ledger` 且按预约幂等，最低扣到 0 分；只对已通过（含被系统自动通过）的预约生效，待审批预约取消不扣分。
- 设备档案的“责任人”只有系统管理员可以修改；实验室管理员只能编辑自己负责实验室的设备，其他字段的修改先进入待审批变更，系统管理员审批通过后才生效，同一设备只保留一条待审批变更（再次提交会覆盖）。
- 管理员只能在预约开始前 2 小时以前撤销已通过的预约；撤销后预约退回待审批（时间段仍占用），学生收到通知，管理员可重新审核或驳回。距离开始不足 2 小时时撤销入口置灰并说明原因。
- 普通用户只能预约实验室本地日期的明日至未来第 7 天，且必须落在每日开放时间内；维修中或停用设备不能预约。演示开放时间为 08:00 至 22:00。
- 培训准入：设备可绑定多个培训项目，预约时必须持有每个项目的有效授权（`APPROVED` 且未过期）。授权待审批、已驳回、已撤销或已过期时提交预约会被拒绝并提示缺失项目；审批通过预约时会再次复查授权，自动审批和信誉自动通过不能绕过该检查。
- 培训项目有效期从审批通过时开始计算，填 0 表示长期有效；授权到期前 30 天发送一次站内提醒。授权过期或被撤销后，已有预约不会自动取消，但新预约和重新审批都会被拦截。
- 培训项目按实验室划分维护权限：校级通用项目（所属实验室为空）只有系统管理员能维护，实验室管理员只能维护自己负责实验室的项目；设备准入要求由实验室管理员维护自己实验室的设备，系统管理员可维护全部设备。
- 待审批预约在开始前 2 小时仍未处理时由定时任务自动驳回并发送站内通知。管理员审批通过时会复查设备状态、开放时间和冲突，但不会重新套用“提前一天提交”规则。
- 临时自动审批开启期间，新预约仍需通过时间范围、开放时间、设备状态和并发冲突检查，符合规则后直接通过并记录审核人和通知。
- 管理员预约列表同时展示待审批和已通过预约，便于登记履约，状态列对已登记履约的预约显示履约结果（未履约、逾期未归还、已完成）；只读管理员可以查看但不能审批、登记或审核申诉。
- 预约结束后，管理统计查询会将过期的已通过预约更新为已完成。
- 设备统计总数按设备档案计数；可预约和维修中按设备当前状态计数，不把“某时段有预约”视为设备全局状态。
- 预约总数统计所有预约记录；审批通过率口径为已通过数 /（已通过数 + 已驳回数），待审批、取消记录不进入分母。
- 实验室预约次数按预约关联设备所属实验室统计。设备使用时长累计已通过及已完成预约的预约区间时长，不代表传感器采集的实际开机时长。
- 故障总数按维修工单记录数统计，已完成按工单状态计数；一个设备可保留多条维修历史。
- 报修会将设备置为维修中；维修完成后由管理员显式选择恢复可预约，否则转为停用。维修工单与处理记录保留历史。
- 前端的状态、角色、人员类型、启用/有效及审计操作类型等枚举统一显示中文标签，接口与数据库仍使用英文代码。

## 测试与常见问题

- 后端单元测试：`cd backend; mvn -s maven-settings.xml test`，覆盖预约冲突边界、非法预约、取消扣分梯度、信誉自动通过与撤销、申诉通知与申诉通过后的履约状态、设备变更审批、培训准入校验、维修工单状态流转、系统管理员权限及实验室数据范围。
- 前端生产构建：`cd frontend; npm run build`。
- 数据库连接失败：确认 MySQL80 服务已启动、数据库已初始化，检查 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
- JWT 启动失败：设置 `JWT_SECRET`，至少 32 字节；不要在源码内放密钥。
- 前端请求失败：先确认后端 8080 可用；不同端口时设置 `VITE_API_PROXY_TARGET` 并重启 Vite。
- Windows 中文路径下 Maven `spring-boot:run` 可能遇到主类定位问题，可使用 `mvn package` 后 `java -jar`。
- 首次构建需要 Maven/npm 依赖可用；网络受限时请检查本机代理或仓库镜像。
