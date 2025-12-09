# 上传服务端代码文件到服务器
# 使用方法：在PowerShell中执行 .\upload-server-files.ps1 [文件1] [文件2] ...
# 如果不指定文件，则上传所有修改过的文件

param(
    [string[]]$Files = @(),
    [string]$ServerIP = "47.116.197.230",
    [string]$ServerUser = "root",
    [string]$ServerPassword = "232629wh@",
    [string]$RemotePath = "/var/www/tongxun/server",
    [switch]$RestartService = $false
)

$ErrorActionPreference = "Continue"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  上传服务端代码文件" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "服务器: ${ServerUser}@${ServerIP}" -ForegroundColor Yellow
Write-Host "远程路径: ${RemotePath}" -ForegroundColor Yellow
Write-Host ""

# 如果未指定文件，上传最近修改的关键文件
if ($Files.Count -eq 0) {
    Write-Host "未指定文件，将上传最近修改的服务端文件..." -ForegroundColor Yellow
    
    # 检查哪些文件被修改了（通过 git status 或文件时间戳）
    $keyFiles = @(
        "server/src/websocket/socketHandler.js",
        "server/src/routes/auth.js",
        "server/src/routes/friend.js",
        "server/src/routes/message.js",
        "server/src/routes/user.js",
        "server/src/routes/upload.js",
        "server/src/routes/group.js",
        "server/src/middleware/auth.js",
        "server/src/config/database.js",
        "server/src/config/redis.js",
        "server/src/index.js",
        "server/package.json",
        "server/ecosystem.config.js"
    )
    
    $Files = $keyFiles | Where-Object { Test-Path $_ }
    
    Write-Host "找到以下文件将上传:" -ForegroundColor Green
    $Files | ForEach-Object { Write-Host "  - $_" -ForegroundColor White }
    Write-Host ""
}

# 上传文件函数
function Upload-ServerFile {
    param(
        [string]$LocalFile,
        [string]$RemoteFile,
        [string]$Description
    )
    
    if (-not (Test-Path $LocalFile)) {
        Write-Host "⚠️  文件不存在，跳过: $LocalFile" -ForegroundColor Yellow
        return $false
    }
    
    Write-Host "正在上传: $Description" -ForegroundColor Cyan
    Write-Host "  本地: $LocalFile" -ForegroundColor Gray
    Write-Host "  远程: $RemoteFile" -ForegroundColor Gray
    
    # 使用 scp 上传（需要手动输入密码）
    scp "$LocalFile" "${ServerUser}@${ServerIP}:${RemoteFile}"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ $Description 上传成功" -ForegroundColor Green
        return $true
    } else {
        Write-Host "❌ $Description 上传失败 (退出代码: $LASTEXITCODE)" -ForegroundColor Red
        return $false
    }
}

# 批量上传文件
$successCount = 0
$failCount = 0

foreach ($file in $Files) {
    if (-not $file.StartsWith("server/")) {
        Write-Host "⚠️  跳过非服务端文件: $file" -ForegroundColor Yellow
        continue
    }
    
    # 计算远程路径
    $relativePath = $file -replace "^server/", ""
    $remoteFile = "${RemotePath}/${relativePath}"
    
    # 获取文件描述
    $fileName = Split-Path $file -Leaf
    $fileDescription = "$fileName"
    
    if (Upload-ServerFile -LocalFile $file -RemoteFile $remoteFile -Description $fileDescription) {
        $successCount++
    } else {
        $failCount++
    }
    
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  上传结果统计" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "成功: $successCount 个文件" -ForegroundColor Green
Write-Host "失败: $failCount 个文件" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host ""

if ($failCount -eq 0) {
    Write-Host "✅ 所有文件上传完成！" -ForegroundColor Green
    Write-Host ""
    
    if ($RestartService) {
        Write-Host "正在尝试自动重启服务..." -ForegroundColor Yellow
        Write-Host "提示: 需要手动SSH登录服务器执行: pm2 restart tongxun-server" -ForegroundColor Yellow
    } else {
        Write-Host "下一步操作（请SSH登录服务器执行）：" -ForegroundColor Yellow
        Write-Host "1. ssh ${ServerUser}@${ServerIP}" -ForegroundColor White
        Write-Host "2. cd ${RemotePath}" -ForegroundColor White
        Write-Host "3. npm install --production  (如果有新依赖)" -ForegroundColor White
        Write-Host "4. pm2 restart tongxun-server" -ForegroundColor White
        Write-Host "5. pm2 logs tongxun-server  (查看日志)" -ForegroundColor White
    }
} else {
    Write-Host "⚠️  部分文件上传失败，请检查错误信息" -ForegroundColor Yellow
}

Write-Host ""
