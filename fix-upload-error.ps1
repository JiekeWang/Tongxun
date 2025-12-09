# 修复错误上传的 PowerShell 脚本

Write-Host "=== 修复错误的上传操作 ===" -ForegroundColor Yellow
Write-Host ""

# 服务器信息
$serverIp = "47.116.197.230"
$serverUser = "root"

Write-Host "步骤1: 连接到服务器并删除错误上传的文件..." -ForegroundColor Cyan
Write-Host "如果出现 'Permission denied' 或其他错误，说明之前的命令可能没有成功执行" -ForegroundColor Gray
Write-Host ""

# SSH 命令删除错误上传的 server 目录（如果存在）
ssh "${serverUser}@${serverIp}" "rm -rf /var/www/tongxun/server/src/routes/server 2>/dev/null && echo '已删除错误上传的文件' || echo '文件不存在（之前的命令可能没有成功）'"

Write-Host ""
Write-Host "步骤2: 使用正确的命令上传 upload.js 文件..." -ForegroundColor Cyan
Write-Host ""

# 正确的上传命令：只上传 upload.js 文件
scp server/src/routes/upload.js "${serverUser}@${serverIp}:/var/www/tongxun/server/src/routes/"

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ 文件上传成功！" -ForegroundColor Green
    Write-Host ""
    Write-Host "步骤3: 重启服务器服务..." -ForegroundColor Cyan
    Write-Host "请在服务器上运行: pm2 restart tongxun-server" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "❌ 文件上传失败，请检查网络连接和权限" -ForegroundColor Red
}

