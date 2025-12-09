# 修复 GitHub 访问不稳定问题的脚本

Write-Host "`n=== 修复 GitHub 访问稳定性 ===" -ForegroundColor Green

# 1. 检查 DNS 设置
Write-Host "`n[1/5] 检查 DNS 设置..." -ForegroundColor Yellow
$dnsServers = (Get-DnsClientServerAddress -AddressFamily IPv4 | Where-Object {$_.InterfaceAlias -notlike "*Loopback*"} | Select-Object -First 1).ServerAddresses
Write-Host "当前 DNS 服务器: $($dnsServers -join ', ')" -ForegroundColor Cyan

# 推荐使用稳定的 DNS
Write-Host "`n推荐使用以下 DNS 服务器以提高 GitHub 访问稳定性：" -ForegroundColor Yellow
Write-Host "1. Google DNS: 8.8.8.8, 8.8.4.4" -ForegroundColor Cyan
Write-Host "2. Cloudflare DNS: 1.1.1.1, 1.0.0.1" -ForegroundColor Cyan
Write-Host "3. 阿里云 DNS: 223.5.5.5, 223.6.6.6" -ForegroundColor Cyan

$changeDns = Read-Host "`n是否要更改 DNS 设置？(Y/N)"
if ($changeDns -eq "Y" -or $changeDns -eq "y") {
    $dnsChoice = Read-Host "选择 DNS (1=Google, 2=Cloudflare, 3=阿里云)"
    $adapter = Get-NetAdapter | Where-Object {$_.Status -eq "Up" -and $_.InterfaceDescription -notlike "*Loopback*"} | Select-Object -First 1
    
    if ($adapter) {
        try {
            switch ($dnsChoice) {
                "1" {
                    Set-DnsClientServerAddress -InterfaceIndex $adapter.InterfaceIndex -ServerAddresses "8.8.8.8", "8.8.4.4"
                    Write-Host "已设置为 Google DNS" -ForegroundColor Green
                }
                "2" {
                    Set-DnsClientServerAddress -InterfaceIndex $adapter.InterfaceIndex -ServerAddresses "1.1.1.1", "1.0.0.1"
                    Write-Host "已设置为 Cloudflare DNS" -ForegroundColor Green
                }
                "3" {
                    Set-DnsClientServerAddress -InterfaceIndex $adapter.InterfaceIndex -ServerAddresses "223.5.5.5", "223.6.6.6"
                    Write-Host "已设置为阿里云 DNS" -ForegroundColor Green
                }
                default {
                    Write-Host "无效选择，跳过 DNS 设置" -ForegroundColor Red
                }
            }
        } catch {
            Write-Host "DNS 设置失败，可能需要管理员权限" -ForegroundColor Red
            Write-Host "请以管理员身份运行此脚本" -ForegroundColor Yellow
        }
    }
}

# 2. 检查系统时间
Write-Host "`n[2/5] 检查系统时间..." -ForegroundColor Yellow
$systemTime = Get-Date
$internetTime = try {
    (Invoke-WebRequest -Uri "http://worldtimeapi.org/api/timezone/Asia/Shanghai" -UseBasicParsing -TimeoutSec 5).Content | ConvertFrom-Json | Select-Object -ExpandProperty datetime
} catch {
    $null
}

if ($internetTime) {
    $internetTimeObj = [DateTime]::Parse($internetTime)
    $timeDiff = [Math]::Abs(($systemTime - $internetTimeObj).TotalSeconds)
    if ($timeDiff -gt 60) {
        Write-Host "系统时间可能不准确，差异: $([Math]::Round($timeDiff)) 秒" -ForegroundColor Yellow
        Write-Host "建议同步系统时间" -ForegroundColor Yellow
    } else {
        Write-Host "系统时间准确" -ForegroundColor Green
    }
} else {
    Write-Host "无法检查系统时间" -ForegroundColor Yellow
}

# 3. 测试 GitHub 连接
Write-Host "`n[3/5] 测试 GitHub 连接..." -ForegroundColor Yellow
$githubUrls = @(
    "https://github.com",
    "https://api.github.com",
    "https://github.com/new"
)

foreach ($url in $githubUrls) {
    try {
        $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 10 -ErrorAction Stop
        Write-Host "$url - 状态码: $($response.StatusCode)" -ForegroundColor Green
    } catch {
        Write-Host "$url - 连接失败: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 4. 添加 GitHub 到 hosts 文件（可选）
Write-Host "`n[4/5] 检查 hosts 文件..." -ForegroundColor Yellow
$hostsFile = "$env:SystemRoot\System32\drivers\etc\hosts"
$githubHosts = @"
# GitHub 域名映射（用于提高访问稳定性）
140.82.112.3 github.com
140.82.112.4 api.github.com
185.199.108.153 assets-cdn.github.com
"@

$addHosts = Read-Host "是否要添加 GitHub 到 hosts 文件？(Y/N)"
if ($addHosts -eq "Y" -or $addHosts -eq "y") {
    try {
        $currentHosts = Get-Content $hostsFile -ErrorAction SilentlyContinue
        if ($currentHosts -notlike "*github.com*") {
            Add-Content -Path $hostsFile -Value "`n$githubHosts" -ErrorAction Stop
            Write-Host "已添加 GitHub 到 hosts 文件" -ForegroundColor Green
            Write-Host "需要刷新 DNS 缓存" -ForegroundColor Yellow
        } else {
            Write-Host "hosts 文件中已存在 GitHub 条目" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "无法修改 hosts 文件，可能需要管理员权限" -ForegroundColor Red
        Write-Host "请以管理员身份运行此脚本" -ForegroundColor Yellow
    }
}

# 5. 刷新 DNS 缓存
Write-Host "`n[5/5] 刷新 DNS 缓存..." -ForegroundColor Yellow
try {
    ipconfig /flushdns | Out-Null
    Write-Host "DNS 缓存已刷新" -ForegroundColor Green
} catch {
    Write-Host "DNS 缓存刷新失败，可能需要管理员权限" -ForegroundColor Red
}

Write-Host "`n=== 完成 ===" -ForegroundColor Green
Write-Host "`n建议操作：" -ForegroundColor Yellow
Write-Host "1. 重启浏览器" -ForegroundColor Cyan
Write-Host "2. 清除浏览器缓存" -ForegroundColor Cyan
Write-Host "3. 如果仍有问题，尝试使用 VPN 或代理" -ForegroundColor Cyan
