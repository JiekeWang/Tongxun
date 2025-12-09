# 快速上传代码到服务器脚本
# 使用方法：在PowerShell中执行 .\upload-to-server.ps1

$serverIP = "47.116.197.230"
$serverUser = "root"
$localPath = "E:\Tongxun\server"
$remotePath = "/var/www/tongxun"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  上传代码到服务器" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "服务器: $serverUser@$serverIP" -ForegroundColor Yellow
Write-Host "本地路径: $localPath" -ForegroundColor Yellow
Write-Host "远程路径: $remotePath/server" -ForegroundColor Yellow
Write-Host ""

# 检查本地路径
if (-not (Test-Path $localPath)) {
    Write-Host "错误: 本地路径不存在: $localPath" -ForegroundColor Red
    exit 1
}

Write-Host "开始上传..." -ForegroundColor Green
Write-Host "注意: node_modules目录很大，建议在服务器上重新安装" -ForegroundColor Yellow
Write-Host ""

# 上传server目录
scp -r "$localPath" ${serverUser}@${serverIP}:${remotePath}/

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "  上传完成！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "下一步操作：" -ForegroundColor Yellow
    Write-Host "1. SSH登录服务器: ssh $serverUser@$serverIP" -ForegroundColor White
    Write-Host "2. 进入项目目录: cd $remotePath/server" -ForegroundColor White
    Write-Host "3. 安装依赖: npm install" -ForegroundColor White
    Write-Host "4. 创建.env文件: nano .env" -ForegroundColor White
    Write-Host "5. 启动服务: pm2 start ecosystem.config.js" -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "上传失败！请检查：" -ForegroundColor Red
    Write-Host "1. 服务器IP是否正确" -ForegroundColor White
    Write-Host "2. 服务器密码是否正确" -ForegroundColor White
    Write-Host "3. 网络连接是否正常" -ForegroundColor White
    Write-Host "4. 是否安装了SCP工具" -ForegroundColor White
}

