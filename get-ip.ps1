# 获取当前网络连接的 IP 地址
# 用于配置手机热点连接时的服务器地址

Write-Host "正在检测网络配置..." -ForegroundColor Cyan
Write-Host ""

# 获取所有活动的网络适配器及其IP
$adapters = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notlike "127.*" -and 
    $_.IPAddress -notlike "169.254.*" -and
    $_.PrefixOrigin -ne "WellKnown"
} | Select-Object IPAddress, InterfaceAlias, PrefixOrigin

if ($adapters) {
    Write-Host "找到以下网络连接：" -ForegroundColor Green
    Write-Host ""
    
    foreach ($adapter in $adapters) {
        Write-Host "  适配器: $($adapter.InterfaceAlias)" -ForegroundColor Yellow
        Write-Host "  IP地址: $($adapter.IPAddress)" -ForegroundColor White
        Write-Host ""
    }
    
    # 显示最可能的IP（通常是WLAN或以太网）
    $mainIP = $adapters | Where-Object { 
        $_.InterfaceAlias -like "*WLAN*" -or 
        $_.InterfaceAlias -like "*Wi-Fi*" -or
        $_.InterfaceAlias -like "*以太网*" -or
        $_.InterfaceAlias -like "*Ethernet*"
    } | Select-Object -First 1
    
    if ($mainIP) {
        Write-Host "=" * 50 -ForegroundColor Cyan
        Write-Host "推荐使用的服务器地址：" -ForegroundColor Green
        Write-Host "  http://$($mainIP.IPAddress):3000/api/" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "请在 NetworkModule.kt 中更新 BASE_URL：" -ForegroundColor Cyan
        Write-Host "  const val BASE_URL = \"http://$($mainIP.IPAddress):3000/api/\"" -ForegroundColor White
        Write-Host "=" * 50 -ForegroundColor Cyan
    }
} else {
    Write-Host "未找到活动的网络连接！" -ForegroundColor Red
}

Write-Host ""
Write-Host "按任意键退出..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

