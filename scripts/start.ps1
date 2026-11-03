#Requires -Version 5.1
<#
.SYNOPSIS
  启动或停止实验室设备管理系统（后端 8080 + 前端 5173）。
.DESCRIPTION
  从 backend\.env.local 读取数据库与 JWT 配置（由 scripts\init.ps1 生成），
  以后台进程启动后端 jar 与 Vite，并等待端口就绪；日志写入 backend\logs 与 frontend\logs。
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -Stop
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -Build
#>
[CmdletBinding()]
param(
  [switch]$Stop,
  [switch]$Build,
  [switch]$NoFrontend,
  [string]$EnvFile
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$backend = Join-Path $root 'backend'
$frontend = Join-Path $root 'frontend'
$backendLogs = Join-Path $backend 'logs'
$frontendLogs = Join-Path $frontend 'logs'
if (-not $EnvFile) { $EnvFile = Join-Path $backend '.env.local' }

function Step([string]$text) { Write-Host "`n==> $text" -ForegroundColor Cyan }
function Info([string]$text) { Write-Host "    $text" }
function Warn([string]$text) { Write-Host "    [提示] $text" -ForegroundColor Yellow }
function Fail([string]$text) { Write-Host "`n[错误] $text" -ForegroundColor Red; exit 1 }

function Get-Listener([int]$port) {
  return Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Stop-Port([int]$port) {
  $listener = Get-Listener $port
  if (-not $listener) {
    Info "端口 $port 未在监听"
    return
  }
  $proc = Get-Process -Id $listener.OwningProcess -ErrorAction SilentlyContinue
  if ($proc) {
    Info "停止 $($proc.ProcessName)（PID $($proc.Id)，端口 $port）"
    Stop-Process -Id $proc.Id -Force
    Start-Sleep -Milliseconds 800
  }
}

function Wait-Http([string]$url, [int]$seconds) {
  $deadline = (Get-Date).AddSeconds($seconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 2
      if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) { return $true }
    } catch {
      # 服务尚未就绪，继续等待
    }
    Start-Sleep -Milliseconds 800
  }
  return $false
}

function Show-LogTail([string]$path, [int]$lines = 20) {
  if (Test-Path $path) {
    Write-Host "    --- $path（最后 $lines 行）---"
    Get-Content $path -Tail $lines | ForEach-Object { Write-Host "    $_" }
  }
}

function Test-BootJar([string]$path) {
  if (-not (Test-Path $path)) { return $false }
  try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $path).Path)
    try {
      $entry = $zip.Entries | Where-Object { $_.FullName -eq 'META-INF/MANIFEST.MF' }
      if (-not $entry) { return $false }
      $reader = New-Object System.IO.StreamReader($entry.Open())
      try { $manifest = $reader.ReadToEnd() } finally { $reader.Close() }
      return ($manifest -match 'Main-Class')
    } finally {
      $zip.Dispose()
    }
  } catch {
    return $false
  }
}

if ($Stop) {
  Step '停止前后端'
  Stop-Port 8080
  Stop-Port 5173
  Remove-Item (Join-Path $backendLogs 'backend.pid') -ErrorAction SilentlyContinue
  Remove-Item (Join-Path $frontendLogs 'vite.pid') -ErrorAction SilentlyContinue
  Step '已停止'
  exit 0
}

Step '读取本地环境变量'
if (-not (Test-Path $EnvFile)) {
  Fail "找不到环境变量文件：$EnvFile`n        请先运行：powershell -ExecutionPolicy Bypass -File .\scripts\init.ps1"
}
Get-Content $EnvFile -Encoding UTF8 | ForEach-Object {
  $line = $_.Trim()
  if ($line -and -not $line.StartsWith('#')) {
    $parts = $line -split '=', 2
    if ($parts.Count -eq 2) {
      $name = $parts[0].Trim()
      $value = $parts[1].Trim()
      if ($name) { Set-Item -Path ("Env:" + $name) -Value $value }
    }
  }
}
if (-not $env:JWT_SECRET) { Fail "环境变量文件缺少 JWT_SECRET：$EnvFile" }
if (-not $env:DB_URL) { Fail "环境变量文件缺少 DB_URL：$EnvFile" }
Info "已加载：$EnvFile"

$jar = Join-Path $backend 'target\lab-equipment-system-1.0.0.jar'
if ($Build -or -not (Test-BootJar $jar)) {
  Step '构建后端 jar'
  if (Get-Listener 8080) {
    Warn '端口 8080 已在监听，jar 被占用，跳过构建（如需重建请先执行 start.ps1 -Stop）'
  } else {
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Fail '找不到 mvn，无法构建后端。' }
    Push-Location $backend
    try {
      & mvn -s maven-settings.xml -q package -DskipTests
      if ($LASTEXITCODE -ne 0) { Fail '后端构建失败，请检查 Maven 输出。' }
    } finally {
      Pop-Location
    }
    if (-not (Test-BootJar $jar)) { Fail "构建后的 jar 缺少主清单：$jar" }
  }
}

if (-not $NoFrontend -and -not (Test-Path (Join-Path $frontend 'node_modules\vite'))) {
  Step '安装前端依赖'
  if (-not (Get-Command npm -ErrorAction SilentlyContinue)) { Fail '找不到 npm，请安装 Node.js 20+。' }
  Push-Location $frontend
  try {
    & npm ci
    if ($LASTEXITCODE -ne 0) { Fail 'npm ci 失败。' }
  } finally {
    Pop-Location
  }
}

Step '启动后端'
if (Get-Listener 8080) {
  Warn '端口 8080 已在监听，跳过后端启动（如需重启请先执行 start.ps1 -Stop）'
} else {
  if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail '找不到 java，请安装 JDK 17。' }
  New-Item -ItemType Directory -Force -Path $backendLogs | Out-Null
  $outLog = Join-Path $backendLogs 'backend.out.log'
  $errLog = Join-Path $backendLogs 'backend.err.log'
  $proc = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$jar`"") -WorkingDirectory $backend `
    -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
  Set-Content -Path (Join-Path $backendLogs 'backend.pid') -Value $proc.Id
  if (-not (Wait-Http 'http://127.0.0.1:8080/v3/api-docs' 120)) {
    Show-LogTail $errLog
    Show-LogTail $outLog
    Fail '后端启动失败或超时，请查看上面的日志。'
  }
  Info '后端已就绪：http://localhost:8080'
}

if (-not $NoFrontend) {
  Step '启动前端'
  if (Get-Listener 5173) {
    Warn '端口 5173 已在监听，跳过前端启动（如需重启请先执行 start.ps1 -Stop）'
  } else {
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Fail '找不到 node，请安装 Node.js 20+。' }
    $vite = Join-Path $frontend 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path $vite)) { Fail "找不到 Vite：$vite，请先安装前端依赖。" }
    New-Item -ItemType Directory -Force -Path $frontendLogs | Out-Null
    $outLog = Join-Path $frontendLogs 'vite.out.log'
    $errLog = Join-Path $frontendLogs 'vite.err.log'
    $proc = Start-Process -FilePath 'node' -ArgumentList @("`"$vite`"", '--host', '0.0.0.0') -WorkingDirectory $frontend `
      -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru -WindowStyle Hidden
    Set-Content -Path (Join-Path $frontendLogs 'vite.pid') -Value $proc.Id
    if (-not (Wait-Http 'http://127.0.0.1:5173/' 60)) {
      Show-LogTail $errLog
      Show-LogTail $outLog
      Fail '前端启动失败或超时，请查看上面的日志。'
    }
    Info '前端已就绪：http://localhost:5173'
  }
}

Step '启动完成'
Write-Host @"
    前端页面  : http://localhost:5173
    后端接口  : http://localhost:8080
    Swagger   : http://localhost:8080/swagger-ui.html
    后端日志  : backend\logs\backend.out.log / backend.err.log
    前端日志  : frontend\logs\vite.out.log / vite.err.log
    演示账号  : student / labadmin / admin / readonly（密码 LabDemo123!）
    停止服务  : powershell -ExecutionPolicy Bypass -File .\scripts\start.ps1 -Stop
"@
