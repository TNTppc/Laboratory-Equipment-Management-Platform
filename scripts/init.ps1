#Requires -Version 5.1
<#
.SYNOPSIS
  首次启动初始化：检查环境、创建数据库与演示数据、生成本地环境变量文件、安装依赖并构建后端。
.DESCRIPTION
  只有在 lab_manage 不存在或没有任何表时才会建库并导入演示数据；已有数据库不会被覆盖。
  重复执行是安全的：已存在的 .env.local、node_modules、jar 会跳过（可用 -ForceEnv 覆盖环境文件）。
  初始化完成后运行 scripts\start.ps1 启动前后端。
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\init.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\init.ps1 -DbPassword root -SkipBuild
#>
[CmdletBinding()]
param(
  [string]$DbHost = 'localhost',
  [int]$DbPort = 3306,
  [string]$DbUser = 'root',
  [string]$DbPassword = $env:DB_PASSWORD,
  [string]$JwtSecret,
  [string]$MysqlPath,
  [switch]$SkipInstall,
  [switch]$SkipBuild,
  [switch]$ForceEnv
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$dbDir = Join-Path $backend 'src\main\resources\db'
$envFile = Join-Path $backend '.env.local'
$dbName = 'lab_manage'

function Step([string]$text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Info([string]$text) { Write-Host "    $text" }
function Warn([string]$text) { Write-Host "    [提示] $text" -ForegroundColor Yellow }
function Fail([string]$text) { Write-Host "`n[错误] $text" -ForegroundColor Red; exit 1 }

function Find-MysqlClient {
  if ($MysqlPath) {
    if (-not (Test-Path $MysqlPath)) { Fail "指定的 mysql 路径不存在：$MysqlPath" }
    return (Resolve-Path $MysqlPath).Path
  }
  $cmd = Get-Command mysql -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $found = Get-ChildItem 'C:\Program Files\MySQL' -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Join-Path $_.FullName 'bin\mysql.exe' } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1
  if ($found) { return $found }
  Fail '找不到 mysql 客户端。请安装 MySQL 8，或用 -MysqlPath 指定 mysql.exe 的完整路径。'
}

$mysql = Find-MysqlClient

function Invoke-MysqlQuery([string]$sql) {
  $output = & $mysql -h $DbHost -P $DbPort -u $DbUser -N -B --default-character-set=utf8mb4 -e $sql 2>&1
  if ($LASTEXITCODE -ne 0) { Fail "MySQL 执行失败（退出码 $LASTEXITCODE）：$($output -join ' ')" }
  return ($output | Out-String).Trim()
}

function Invoke-SqlFile([string]$file) {
  if (-not (Test-Path $file)) { Fail "缺少 SQL 文件：$file" }
  $name = Split-Path $file -Leaf
  Info "执行 $name"
  $outFile = [IO.Path]::GetTempFileName()
  $errFile = [IO.Path]::GetTempFileName()
  $p = Start-Process -FilePath $mysql `
    -ArgumentList @('-h', $DbHost, '-P', "$DbPort", '-u', $DbUser, '--default-character-set=utf8mb4') `
    -RedirectStandardInput $file -RedirectStandardOutput $outFile -RedirectStandardError $errFile `
    -Wait -PassThru -NoNewWindow
  $code = $p.ExitCode
  $detail = ''
  if ($code -ne 0) {
    $detail = Get-Content $errFile -Raw -ErrorAction SilentlyContinue
    if (-not $detail) { $detail = Get-Content $outFile -Raw -ErrorAction SilentlyContinue }
  }
  Remove-Item $outFile, $errFile -ErrorAction SilentlyContinue
  if ($code -ne 0) { Fail "执行 $name 失败（退出码 $code）：$detail" }
}

Step '检查 MySQL 客户端'
Info "mysql: $mysql"

Step '检查 MySQL 服务'
$svc = Get-Service -Name 'MySQL80' -ErrorAction SilentlyContinue
if ($svc) {
  if ($svc.Status -eq 'Running') {
    Info 'MySQL80 正在运行'
  } else {
    Info "MySQL80 当前为 $($svc.Status)，尝试启动..."
    try {
      Start-Service -Name $svc.Name
      Start-Sleep -Seconds 2
      Info 'MySQL80 已启动'
    } catch {
      Warn '无法自动启动 MySQL80（可能需要管理员权限），请手动启动后重新运行本脚本。'
    }
  }
} else {
  Warn '未找到 MySQL80 服务，跳过服务检查（如果使用其他服务名或远程数据库可忽略）。'
}

Step "连接 MySQL（$DbUser@$DbHost`:$DbPort）"
if (-not $DbPassword) {
  $secure = Read-Host "请输入 MySQL 用户 $DbUser 的密码" -AsSecureString
  $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $DbPassword) { Fail 'MySQL 密码不能为空。' }
$env:MYSQL_PWD = $DbPassword
Invoke-MysqlQuery 'SELECT 1' | Out-Null
Info '连接成功'

Step '初始化数据库'
$tableCount = [int](Invoke-MysqlQuery "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$dbName'")
if ($tableCount -eq 0) {
  Info "数据库 $dbName 不存在或为空，开始初始化"
  Invoke-SqlFile (Join-Path $dbDir 'schema.sql')
  Invoke-SqlFile (Join-Path $dbDir 'demo-data.sql')

  Info '登记初始化版本（新库的 schema.sql 已包含全部表结构）'
  $versions = @(
    '2026-10-29-unify-datetime-to-utc',
    '2026-10-29-identity-and-approval',
    '2026-10-30-credit-auto-approval-appeal',
    '2026-10-30-fulfillment-accountability',
    '2026-11-02-appeal-evidence',
    '2026-11-03-cancel-and-revoke-approval',
    '2026-11-03-equipment-change-approval'
  )
  $sql = "USE $dbName; CREATE TABLE IF NOT EXISTS app_migration (version VARCHAR(80) PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT (UTC_TIMESTAMP()));"
  foreach ($v in $versions) { $sql += " INSERT IGNORE INTO app_migration(version) VALUES ('$v');" }
  Invoke-MysqlQuery $sql | Out-Null
  Info '数据库初始化完成（含 12 台演示设备与 4 个演示账号）'
} else {
  Info "数据库 $dbName 已存在（$tableCount 张表），跳过建库和演示数据，不会覆盖现有数据。"
  Warn '如需彻底重建，请先手动执行：DROP DATABASE lab_manage; 再重新运行本脚本。'
}

Step '生成本地环境变量文件'
if ((Test-Path $envFile) -and -not $ForceEnv) {
  Info "已存在：$envFile"
  Info '如需重新生成，请加 -ForceEnv（会重新生成 JWT_SECRET，已登录的令牌将失效）。'
} else {
  if (-not $JwtSecret) {
    $bytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $JwtSecret = [Convert]::ToBase64String($bytes)
  }
  if ($JwtSecret.Length -lt 32) { Fail 'JWT_SECRET 至少需要 32 字节。' }
  $dbUrl = "jdbc:mysql://${DbHost}:$DbPort/${dbName}?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
  $lines = @(
    '# 本地开发环境变量（由 scripts/init.ps1 生成，已被 .gitignore 忽略，请勿提交）',
    'SERVER_PORT=8080',
    "DB_URL=$dbUrl",
    "DB_USERNAME=$DbUser",
    "DB_PASSWORD=$DbPassword",
    "JWT_SECRET=$JwtSecret",
    'JWT_TTL_HOURS=12',
    'UPLOAD_DIR=uploads'
  )
  [System.IO.File]::WriteAllText($envFile, (($lines -join "`r`n") + "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
  Info "已写入：$envFile（JWT_SECRET 为随机 48 字节，不显示在终端）"
}
New-Item -ItemType Directory -Force -Path (Join-Path $backend 'uploads') | Out-Null

if (-not $SkipInstall) {
  Step '安装前端依赖'
  if (Test-Path (Join-Path $frontend 'node_modules\vite')) {
    Info 'node_modules 已存在，跳过 npm ci'
  } else {
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { Fail '找不到 npm，请安装 Node.js 20+。' }
    Push-Location $frontend
    try {
      & npm ci
      if ($LASTEXITCODE -ne 0) { Fail 'npm ci 失败。' }
    } finally {
      Pop-Location
    }
    Info '前端依赖安装完成'
  }
}

if (-not $SkipBuild) {
  Step '构建后端 jar'
  $backendRunning = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
  if ($backendRunning) {
    Warn '检测到 8080 端口已有服务在运行，jar 可能被占用，跳过构建。'
    Warn '如需重新构建，请先停止后端：powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -Stop'
  } else {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Fail '找不到 mvn，请安装 Maven 3.8+，或加 -SkipBuild 跳过构建。' }
    Push-Location $backend
    try {
      & mvn -s maven-settings.xml -q package -DskipTests
      if ($LASTEXITCODE -ne 0) { Fail '后端构建失败，请检查 Maven 输出。' }
    } finally {
      Pop-Location
    }
    Info '后端构建完成：backend\target\lab-equipment-system-1.0.0.jar'
  }
}

Step '初始化完成'
Write-Host @"
    数据库    : $dbName @ ${DbHost}:$DbPort
    环境变量  : $envFile
    后端 jar  : backend\target\lab-equipment-system-1.0.0.jar
    前端依赖  : frontend\node_modules

    下一步启动项目：
      powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1

    演示账号（密码均为 LabDemo123!）：
      student   普通用户
      labadmin  实验室管理员（智能制造实验室）
      admin     系统管理员
      readonly  只读管理员
"@
