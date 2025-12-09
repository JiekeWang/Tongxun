# 部署服务端代码到服务器
# 使用方法：在PowerShell中执行 .\deploy-server.ps1

param(
    [string]$ServerIP = "47.116.197.230",
    [string]$ServerUser = "root",
    [string]$ServerPassword = "232629wh@",
    [string]$RemotePath = "/var/www/tongxun/server",
    [switch]$AutoRestart = $false
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  部署服务端代码到服务器" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "服务器: ${ServerUser}@${ServerIP}" -ForegroundColor Yellow
Write-Host "远程路径: ${RemotePath}" -ForegroundColor Yellow
Write-Host ""

# 检查本地 server 目录
$localServerPath = "server"
if (-not (Test-Path $localServerPath)) {
    Write-Host "错误: 未找到 server 目录！" -ForegroundColor Red
    Write-Host "请确保在项目根目录（D:\Tongxun）执行此脚本" -ForegroundColor Red
    exit 1
}

# 定义要上传的文件列表
$filesToUpload = @(
    @{Local="server/src/websocket/socketHandler.js"; Remote="src/websocket/socketHandler.js"; Desc="WebSocket处理文件"},
    @{Local="server/src/routes/auth.js"; Remote="src/routes/auth.js"; Desc="认证路由"},
    @{Local="server/src/routes/friend.js"; Remote="src/routes/friend.js"; Desc="好友路由"},
    @{Local="server/src/routes/message.js"; Remote="src/routes/message.js"; Desc="消息路由"},
    @{Local="server/src/routes/user.js"; Remote="src/routes/user.js"; Desc="用户路由"},
    @{Local="server/src/routes/upload.js"; Remote="src/routes/upload.js"; Desc="上传路由"},
    @{Local="server/src/routes/group.js"; Remote="src/routes/group.js"; Desc="群组路由"},
    @{Local="server/src/middleware/auth.js"; Remote="src/middleware/auth.js"; Desc="认证中间件"},
    @{Local="server/src/config/database.js"; Remote="src/config/database.js"; Desc="数据库配置"},
    @{Local="server/src/config/redis.js"; Remote="src/config/redis.js"; Desc="Redis配置"},
    @{Local="server/src/index.js"; Remote="src/index.js"; Desc="服务器入口文件"},
    @{Local="server/src/utils/logger.js"; Remote="src/utils/logger.js"; Desc="日志工具"},
    @{Local="server/package.json"; Remote="package.json"; Desc="依赖配置"},
    @{Local="server/ecosystem.config.js"; Remote="ecosystem.config.js"; Desc="PM2配置"}
)

Write-Host "准备上传以下文件：" -ForegroundColor Green
$filesToUpload | ForEach-Object {
    if (Test-Path $_.Local) {
        Write-Host "  ✓ $($_.Desc) - $($_.Local)" -ForegroundColor White
    } else {
        Write-Host "  ✗ $($_.Desc) - $($_.Local) (文件不存在，将跳过)" -ForegroundColor Yellow
    }
}
Write-Host ""

# 确认上传
$confirm = Read-Host "确认上传？(y/n)"
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "已取消上传" -ForegroundColor Yellow
    exit 0
}

Write-Host ""
Write-Host "开始上传..." -ForegroundColor Cyan
Write-Host "提示: 密码是: $ServerPassword" -ForegroundColor Gray
Write-Host ""

$successCount = 0
$failCount = 0

foreach ($file in $filesToUpload) {
    if (-not (Test-Path $file.Local)) {
        Write-Host "⚠️  跳过不存在的文件: $($file.Local)" -ForegroundColor Yellow
        continue
    }
    
    $remoteFullPath = "${RemotePath}/$($file.Remote)"
    Write-Host "[$successCount/$($filesToUpload.Count)] 上传: $($file.Desc)" -ForegroundColor Cyan
    
    scp "$($file.Local)" "${ServerUser}@${ServerIP}:${remoteFullPath}"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  ✅ 上传成功" -ForegroundColor Green
        $successCount++
    } else {
        Write-Host "  ❌ 上传失败 (退出代码: $LASTEXITCODE)" -ForegroundColor Red
        $failCount++
    }
    Write-Host ""
}

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  上传完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "成功: $successCount 个文件" -ForegroundColor Green
Write-Host "失败: $failCount 个文件" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host ""

if ($failCount -eq 0) {
    Write-Host "✅ 所有文件上传成功！" -ForegroundColor Green
    Write-Host ""
    
    Write-Host "下一步操作（SSH登录服务器执行）：" -ForegroundColor Yellow
    Write-Host "1. ssh ${ServerUser}@${ServerIP}" -ForegroundColor White
    Write-Host "   密码: $ServerPassword" -ForegroundColor Gray
    Write-Host ""
    Write-Host "2. cd ${RemotePath}" -ForegroundColor White
    Write-Host ""
    Write-Host "3. npm install --production  (如果有新依赖)" -ForegroundColor White
    Write-Host ""
    Write-Host "4. pm2 restart tongxun-server" -ForegroundColor White
    Write-Host "   (或使用: pm2 reload tongxun-server)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "5. pm2 logs tongxun-server  (查看日志确认)" -ForegroundColor White
    Write-Host ""
    
    if ($AutoRestart) {
        Write-Host "尝试自动重启服务..." -ForegroundColor Cyan
        # 注意：Windows PowerShell 可能不支持直接执行 SSH 命令
        # 可以使用 plink (PuTTY) 或其他工具
        Write-Host "提示: Windows 需要安装 OpenSSH 或 PuTTY 才能自动执行远程命令" -ForegroundColor Yellow
    }
} else {
    Write-Host "⚠️  部分文件上传失败，请检查错误信息并重试" -ForegroundColor Yellow
}

Write-Host ""

