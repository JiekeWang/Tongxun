# 添加防火墙规则，允许 3000 端口入站连接
# 需要以管理员身份运行此脚本

Write-Host "正在添加防火墙规则..." -ForegroundColor Yellow

try {
    # 检查是否已存在规则
    $existingRule = Get-NetFirewallRule -DisplayName "Node.js Server" -ErrorAction SilentlyContinue
    
    if ($existingRule) {
        Write-Host "防火墙规则已存在，正在启用..." -ForegroundColor Green
        Enable-NetFirewallRule -DisplayName "Node.js Server"
    } else {
        # 创建新规则
        New-NetFirewallRule -DisplayName "Node.js Server" -Direction Inbound -LocalPort 3000 -Protocol TCP -Action Allow
        Write-Host "防火墙规则创建成功！" -ForegroundColor Green
    }
    
    # 验证规则
    $rule = Get-NetFirewallRule -DisplayName "Node.js Server"
    Write-Host "`n规则详情：" -ForegroundColor Cyan
    Write-Host "  名称: $($rule.DisplayName)" -ForegroundColor White
    Write-Host "  状态: $($rule.Enabled)" -ForegroundColor White
    Write-Host "  方向: $($rule.Direction)" -ForegroundColor White
    Write-Host "  操作: $($rule.Action)" -ForegroundColor White
    
} catch {
    Write-Host "`n错误: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "`n请确保以管理员身份运行 PowerShell！" -ForegroundColor Yellow
    Write-Host "右键点击 PowerShell，选择'以管理员身份运行'" -ForegroundColor Yellow
}

Write-Host "`n按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

