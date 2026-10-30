# 实验室设备预约与维护管理系统

基于 Spring Boot 3 与 Vue 3 的前后端分离演示系统，覆盖设备档案、预约审批、报修维护、通知和管理统计。后端为单体应用，使用 MySQL 持久化数据。

## 技术栈

- 后端：Java 17、Spring Boot 3.3.5、Maven、Spring Security、JWT、MyBatis-Plus、Bean Validation、springdoc OpenAPI、MySQL 8。
- 前端：Vue 3、TypeScript、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts。

## 环境要求

安装并确保命令可用：JDK 17、Maven 3.8+、Node.js 20+、npm 10+、MySQL 8。当前工程已在 Java 17、Maven 3.10、Node 24、npm 11、MySQL 8.0 上验证。

## 初始化数据库

先启动 MySQL 8。Windows PowerShell 示例（按本机安装位置使用 `mysql` 命令）：

```powershell
mysql --default-character-set=utf8mb4 -u root -p < backend/src/main/resources/db/schema.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/demo-data.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/timezone-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/identity-and-approval-migration.sql
mysql --default-character-set=utf8mb4 -u root -p lab_manage < backend/src/main/resources/db/credit-and-auto-approval-migration.sql
```

输入 MySQL root 密码后，第一个脚本创建数据库和基础演示数据；第二个脚本按设备编号补充设备档案；第三个脚本用于旧数据库时区修复；第四个脚本为旧数据库补充校园身份、必填联系方式、只读管理员和临时自动审批配置；第五个脚本补充信誉积分、履约、申诉和分范围自动审批规则。迁移脚本可重复执行，不要在已有数据库上重新执行 `schema.sql`。全库时间字段统一保存 UTC，前端按当前电脑时区展示，实验室开放时间按 `Asia/Shanghai` 校验。

## 配置与启动

复制 `backend/.env.example` 中列出的变量到当前终端环境。Spring Boot 不会自动读取 `.env` 文件，必须在启动进程前设置环境变量。不得将真实密码或生产 JWT 密钥提交到版本库。

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
- 设备和基础选项：`/api/equipment`、`/api/equipment/{id}`、`/api/labs`、`/api/categories`。
- 预约：`/api/reservations` 及其审批、取消动作；管理员可按设备、设备分类、实验室或全局临时开启 1、4 或 8 小时自动通过/自动拒绝，优先级为设备 > 分类 > 实验室 > 全局。
- 信誉与履约：普通用户初始信誉分为 100，每日恢复 0.5 分且不超过 100；低于 60 分禁止预约，60 至 90 分需要人工审批，达到 90 分自动通过。管理员可在预约结束后登记已归还、未履约或逾期未归还，后两者分别扣 1 分和 2 分；学生可提交一次履约申诉，管理员审核通过后返还对应积分。
- 报修和处理：`/api/repairs`、`/api/repairs/{id}`、`/api/repairs/{id}/process`；故障图片通过 `/api/uploads/repair-image` 上传并保存到 `UPLOAD_DIR`。
- 管理用户、实验室、分类：`/api/admin/**`；仅系统管理员可用。
- 管理统计：`/api/stats`；实验室管理员仅统计授权实验室。
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>；Swagger UI：<http://localhost:8080/swagger-ui.html>。

接口返回统一的 `{ success, message, data }` 结构。身份认证采用 Bearer JWT；后端执行角色和实验室范围校验，前端路由/按钮控制只用于体验优化。

## 业务规则和统计口径

- 预约区间为左闭右开 `[开始, 结束)`；冲突条件为既有开始早于请求结束且既有结束晚于请求开始。待审批和已通过占用时间，驳回和取消不占用；提交与审批通过都会复查，按设备加锁并在事务中处理。
- 普通用户只能预约实验室本地日期的明日至未来第 7 天，且必须落在每日开放时间内；维修中或停用设备不能预约。演示开放时间为 08:00 至 22:00。
- 待审批预约在开始前 2 小时仍未处理时由定时任务自动驳回并发送站内通知。管理员审批通过时会复查设备状态、开放时间和冲突，但不会重新套用“提前一天提交”规则。
- 临时自动审批开启期间，新预约仍需通过时间范围、开放时间、设备状态和并发冲突检查，符合规则后直接通过并记录审核人和通知。
- 管理员预约列表同时展示待审批和已通过预约，便于登记履约；只读管理员可以查看但不能审批、登记或审核申诉。
- 预约结束后，管理统计查询会将过期的已通过预约更新为已完成。
- 设备统计总数按设备档案计数；可预约和维修中按设备当前状态计数，不把“某时段有预约”视为设备全局状态。
- 预约总数统计所有预约记录；审批通过率口径为已通过数 /（已通过数 + 已驳回数），待审批、取消记录不进入分母。
- 实验室预约次数按预约关联设备所属实验室统计。设备使用时长累计已通过及已完成预约的预约区间时长，不代表传感器采集的实际开机时长。
- 故障总数按维修工单记录数统计，已完成按工单状态计数；一个设备可保留多条维修历史。
- 报修会将设备置为维修中；维修完成后由管理员显式选择恢复可预约，否则转为停用。维修工单与处理记录保留历史。

## 测试与常见问题

- 后端单元测试：`cd backend; mvn -s maven-settings.xml test`，覆盖预约冲突边界、非法预约、维修工单状态流转、系统管理员权限及实验室数据范围。
- 前端生产构建：`cd frontend; npm run build`。
- 数据库连接失败：确认 MySQL80 服务已启动、数据库已初始化，检查 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。
- JWT 启动失败：设置 `JWT_SECRET`，至少 32 字节；不要在源码内放密钥。
- 前端请求失败：先确认后端 8080 可用；不同端口时设置 `VITE_API_PROXY_TARGET` 并重启 Vite。
- Windows 中文路径下 Maven `spring-boot:run` 可能遇到主类定位问题，可使用 `mvn package` 后 `java -jar`。
- 首次构建需要 Maven/npm 依赖可用；网络受限时请检查本机代理或仓库镜像。
