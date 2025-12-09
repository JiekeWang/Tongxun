# 测试服务器连接
# 用于验证手机能否访问后端服务

$serverIP = "172.24.22.201"
$serverPort = 3000
$baseUrl = "http://${serverIP}:${serverPort}/api/"

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "服务器连接测试" -ForegroundColor Yellow
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host ""

# 1. 检查端口是否监听
Write-Host "1. 检查端口状态..." -ForegroundColor Green
$portStatus = netstat -ano | Select-String -Pattern ":3000.*LISTENING"
if ($portStatus) {
    Write-Host "   ✓ 端口 3000 正在监听" -ForegroundColor Green
    $pid = ($portStatus -split '\s+')[-1]
    Write-Host "   进程 ID: $pid" -ForegroundColor Gray
} else {
    Write-Host "   ✗ 端口 3000 未监听" -ForegroundColor Red
    Write-Host "   请启动后端服务: cd server && npm start" -ForegroundColor Yellow
}
Write-Host ""

# 2. 检查防火墙规则
Write-Host "2. 检查防火墙规则..." -ForegroundColor Green
$firewallRule = Get-NetFirewallRule -DisplayName "Node.js Server" -ErrorAction SilentlyContinue
if ($firewallRule) {
    $enabled = ($firewallRule | Get-NetFirewallPortFilter).LocalPort -eq 3000
    Write-Host "   ✓ 防火墙规则已配置" -ForegroundColor Green
} else {
    Write-Host "   ⚠ 防火墙规则未找到" -ForegroundColor Yellow
    Write-Host "   建议添加规则允许 3000 端口" -ForegroundColor Yellow
}
Write-Host ""

# 3. 测试本地连接
Write-Host "3. 测试本地连接..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri "${baseUrl}auth/login" -Method POST -Body '{}' -ContentType "application/json" -TimeoutSec 5 -ErrorAction Stop
    Write-Host "   ✓ 本地连接成功" -ForegroundColor Green
    Write-Host "   状态码: $($response.StatusCode)" -ForegroundColor Gray
} catch {
    if ($_.Exception.Response.StatusCode -eq 400 -or $_.Exception.Response.StatusCode -eq 401) {
        Write-Host "   ✓ 服务器响应正常（收到预期的错误响应）" -ForegroundColor Green
    } else {
        Write-Host "   ⚠ 连接测试: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}
Write-Host ""

# 4. 显示配置信息
Write-Host "4. 当前配置信息..." -ForegroundColor Green
Write-Host "   服务器 IP: $serverIP" -ForegroundColor White
Write-Host "   服务器端口: $serverPort" -ForegroundColor White
Write-Host "   API 地址: $baseUrl" -ForegroundColor White
Write-Host ""

# 5. 手机连接说明
Write-Host "5. 手机连接说明..." -ForegroundColor Green
Write-Host "   ✓ BASE_URL 已配置为: $baseUrl" -ForegroundColor White
Write-Host "   ✓ 确保手机和电脑在同一网络（Wi-Fi 或热点）" -ForegroundColor White
Write-Host "   ✓ 在手机浏览器测试: $baseUrl" -ForegroundColor White
Write-Host ""

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "测试完成！" -ForegroundColor Green
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host ""
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

