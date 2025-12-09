# 自动上传服务端代码到服务器（使用密码自动输入）
# 使用方法：在PowerShell中执行 .\upload-server-auto.ps1

param(
    [string]$ServerIP = "47.116.197.230",
    [string]$ServerUser = "root",
    [string]$ServerPassword = "232629wh@",
    [string]$RemotePath = "/var/www/tongxun/server"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  自动上传服务端代码到服务器" -ForegroundColor Cyan
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

# 检查是否安装了 sshpass（Linux/Unix）或使用其他方法
# Windows 可以使用 plink (PuTTY) 或直接使用 OpenSSH（需要手动输入密码）

# 尝试使用 sshpass（如果已安装）
$sshpassPath = Get-Command sshpass -ErrorAction SilentlyContinue

if ($sshpassPath) {
    Write-Host "检测到 sshpass，使用自动密码输入" -ForegroundColor Green
    $useSshpass = $true
} else {
    Write-Host "未检测到 sshpass，将使用交互式密码输入" -ForegroundColor Yellow
    Write-Host "提示: 密码是: $ServerPassword" -ForegroundColor Yellow
    $useSshpass = $false
}

Write-Host ""
Write-Host "开始上传服务端代码..." -ForegroundColor Green
Write-Host ""

# 上传函数（支持 sshpass 或手动输入）
function Upload-File {
    param(
        [string]$LocalFile,
        [string]$RemoteFile,
        [string]$Description
    )
    
    Write-Host "正在上传: $Description" -ForegroundColor Yellow
    
    if ($useSshpass) {
        $env:SSHPASS = $ServerPassword
        sshpass -e scp -o StrictHostKeyChecking=no "$LocalFile" "${ServerUser}@${ServerIP}:${RemoteFile}"
    } else {
        scp "$LocalFile" "${ServerUser}@${ServerIP}:${RemoteFile}"
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ $Description 上传成功" -ForegroundColor Green
        return $true
    } else {
        Write-Host "❌ $Description 上传失败" -ForegroundColor Red
        return $false
    }
}

function Upload-Directory {
    param(
        [string]$LocalDir,
        [string]$RemoteDir,
        [string]$Description
    )
    
    Write-Host "正在上传: $Description" -ForegroundColor Yellow
    
    if ($useSshpass) {
        $env:SSHPASS = $ServerPassword
        sshpass -e scp -r -o StrictHostKeyChecking=no "$LocalDir" "${ServerUser}@${ServerIP}:${RemoteDir}"
    } else {
        scp -r "$LocalDir" "${ServerUser}@${ServerIP}:${RemoteDir}"
    }
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ $Description 上传成功" -ForegroundColor Green
        return $true
    } else {
        Write-Host "❌ $Description 上传失败" -ForegroundColor Red
        return $false
    }
}

# 1. 上传 src 目录
$success = Upload-Directory "${localServerPath}/src" "${RemotePath}/" "src/ 目录"
if (-not $success) {
    Write-Host "`n上传失败！请检查网络连接和服务器状态" -ForegroundColor Red
    exit 1
}

# 2. 上传 package.json
$success = Upload-File "${localServerPath}/package.json" "${RemotePath}/package.json" "package.json"
if (-not $success) {
    exit 1
}

# 3. 上传 package-lock.json（可选）
if (Test-Path "${localServerPath}/package-lock.json") {
    Upload-File "${localServerPath}/package-lock.json" "${RemotePath}/package-lock.json" "package-lock.json" | Out-Null
}

# 4. 上传 ecosystem.config.js（可选）
if (Test-Path "${localServerPath}/ecosystem.config.js") {
    Upload-File "${localServerPath}/ecosystem.config.js" "${RemotePath}/ecosystem.config.js" "ecosystem.config.js" | Out-Null
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  ✅ 所有文件上传完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "下一步操作（自动执行或手动执行）：" -ForegroundColor Yellow

# 尝试自动执行后续命令
Write-Host "`n正在尝试自动重启服务..." -ForegroundColor Cyan

if ($useSshpass) {
    $env:SSHPASS = $ServerPassword
    $installCmd = "cd ${RemotePath} && npm install --production"
    $restartCmd = "pm2 restart tongxun-server || pm2 reload tongxun-server"
    
    Write-Host "正在安装依赖..." -ForegroundColor Yellow
    sshpass -e ssh -o StrictHostKeyChecking=no "${ServerUser}@${ServerIP}" "$installCmd"
    
    Write-Host "正在重启服务..." -ForegroundColor Yellow
    sshpass -e ssh -o StrictHostKeyChecking=no "${ServerUser}@${ServerIP}" "$restartCmd"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ 服务重启成功" -ForegroundColor Green
    } else {
        Write-Host "⚠️ 自动重启失败，请手动执行" -ForegroundColor Yellow
    }
} else {
    Write-Host "需要手动SSH登录服务器执行以下命令：" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "手动执行命令：" -ForegroundColor Yellow
Write-Host "1. ssh ${ServerUser}@${ServerIP}" -ForegroundColor White
Write-Host "2. cd ${RemotePath}" -ForegroundColor White
Write-Host "3. npm install --production" -ForegroundColor White
Write-Host "4. pm2 restart tongxun-server" -ForegroundColor White
Write-Host "5. pm2 logs tongxun-server" -ForegroundColor White
Write-Host ""

