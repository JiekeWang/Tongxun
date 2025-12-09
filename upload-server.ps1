# 上传服务端代码到服务器
# 使用方法：在PowerShell中执行 .\upload-server.ps1

param(
    [string]$ServerIP = "47.116.197.230",
    [string]$ServerUser = "root",
    [string]$ServerPassword = "232629wh@",
    [string]$RemotePath = "/var/www/tongxun/server"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  上传服务端代码到服务器" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "服务器: ${ServerUser}@${ServerIP}" -ForegroundColor Yellow
Write-Host "远程路径: ${RemotePath}" -ForegroundColor Yellow
Write-Host ""

# 检查本地 server 目录是否存在
$localServerPath = "server"
if (-not (Test-Path $localServerPath)) {
    Write-Host "错误: 未找到 server 目录！" -ForegroundColor Red
    Write-Host "请确保在项目根目录（D:\Tongxun）执行此脚本" -ForegroundColor Red
    exit 1
}

# 检查是否安装了 sshpass（用于自动输入密码）或使用 expect
# Windows 可能需要安装 OpenSSH 或使用其他方法

Write-Host "开始上传服务端代码..." -ForegroundColor Green
Write-Host ""
Write-Host "注意: 将上传以下目录和文件:" -ForegroundColor Yellow
Write-Host "  - src/ (所有源代码)" -ForegroundColor White
Write-Host "  - package.json" -ForegroundColor White
Write-Host "  - package-lock.json" -ForegroundColor White
Write-Host "  - ecosystem.config.js" -ForegroundColor White
Write-Host "  - *.md (文档文件)" -ForegroundColor White
Write-Host ""
Write-Host "排除的文件:" -ForegroundColor Yellow
Write-Host "  - node_modules/ (太大，将在服务器上安装)" -ForegroundColor White
Write-Host "  - .env (敏感信息，不上传)" -ForegroundColor White
Write-Host "  - logs/ (日志文件)" -ForegroundColor White
Write-Host ""

# 方法1: 使用 scp 上传（需要手动输入密码）
# 如果安装了 sshpass，可以使用自动输入密码
Write-Host "正在上传文件..." -ForegroundColor Cyan

# 上传 src 目录
Write-Host "`n[1/4] 上传 src/ 目录..." -ForegroundColor Yellow
$srcUpload = scp -r "${localServerPath}/src" "${ServerUser}@${ServerIP}:${RemotePath}/"
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ src/ 上传失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ src/ 上传成功" -ForegroundColor Green

# 上传 package.json
Write-Host "`n[2/4] 上传 package.json..." -ForegroundColor Yellow
$packageUpload = scp "${localServerPath}/package.json" "${ServerUser}@${ServerIP}:${RemotePath}/"
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ package.json 上传失败" -ForegroundColor Red
    exit 1
}
Write-Host "✅ package.json 上传成功" -ForegroundColor Green

# 上传 package-lock.json
Write-Host "`n[3/4] 上传 package-lock.json..." -ForegroundColor Yellow
$packageLockUpload = scp "${localServerPath}/package-lock.json" "${ServerUser}@${ServerIP}:${RemotePath}/"
if ($LASTEXITCODE -ne 0) {
    Write-Host "⚠️ package-lock.json 上传失败（非关键）" -ForegroundColor Yellow
} else {
    Write-Host "✅ package-lock.json 上传成功" -ForegroundColor Green
}

# 上传 ecosystem.config.js
Write-Host "`n[4/4] 上传 ecosystem.config.js..." -ForegroundColor Yellow
if (Test-Path "${localServerPath}/ecosystem.config.js") {
    $ecosystemUpload = scp "${localServerPath}/ecosystem.config.js" "${ServerUser}@${ServerIP}:${RemotePath}/"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "⚠️ ecosystem.config.js 上传失败（非关键）" -ForegroundColor Yellow
    } else {
        Write-Host "✅ ecosystem.config.js 上传成功" -ForegroundColor Green
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ 文件上传完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "下一步操作（请SSH登录服务器执行）：" -ForegroundColor Yellow
Write-Host "1. SSH登录: ssh ${ServerUser}@${ServerIP}" -ForegroundColor White
Write-Host "2. 进入目录: cd ${RemotePath}" -ForegroundColor White
Write-Host "3. 安装依赖: npm install --production" -ForegroundColor White
Write-Host "4. 重启服务: pm2 restart tongxun-server" -ForegroundColor White
Write-Host "   (或使用: pm2 reload tongxun-server)" -ForegroundColor White
Write-Host "5. 查看日志: pm2 logs tongxun-server" -ForegroundColor White
Write-Host ""

