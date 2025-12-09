# 连接 Android 设备脚本（无线调试）
# 使用方法：
#   .\connect-device.ps1                    # 使用默认端口 43271
#   .\connect-device.ps1 -Port 43271       # 指定端口号
#   .\connect-device.ps1 -Port 43271 -Ip 192.168.4.28  # 指定IP和端口

param(
    [Parameter(Mandatory=$false)]
    [string]$Port = "43271",  # 默认端口
    
    [Parameter(Mandatory=$false)]
    [string]$Ip = "192.168.4.28"  # 默认IP
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Android Device Connection Tool" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 设备信息
$deviceIp = $Ip
$devicePort = $Port
$deviceAddress = "$deviceIp`:$devicePort"

Write-Host "Device IP: $deviceIp" -ForegroundColor Cyan
Write-Host "Device Port: $devicePort" -ForegroundColor Cyan
Write-Host "Device Address: $deviceAddress" -ForegroundColor Cyan
Write-Host ""

# 检查 adb 是否可用
$adbPath = "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe"

if (-not (Test-Path $adbPath)) {
    # 尝试其他可能的路径
    $possiblePaths = @(
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    )
    
    $found = $false
    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            $adbPath = $path
            $found = $true
            break
        }
    }
    
    if (-not $found) {
        Write-Host "错误: 未找到 adb 命令" -ForegroundColor Red
        Write-Host "请确保 Android SDK Platform Tools 已安装" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host "使用 ADB: $adbPath" -ForegroundColor Green
Write-Host ""

# Check currently connected devices
Write-Host "Checking connected devices..." -ForegroundColor Yellow
$devices = & $adbPath devices
Write-Host $devices

# Check if device is already connected
if ($devices -match $deviceAddress) {
    Write-Host "`nDevice already connected: $deviceAddress" -ForegroundColor Green
    Write-Host "Device status: $($devices | Select-String $deviceAddress)" -ForegroundColor Cyan
} else {
    Write-Host "`nDevice not connected, connecting..." -ForegroundColor Yellow
    Write-Host "Connection address: $deviceAddress" -ForegroundColor Cyan
    
    # Disconnect existing connection if any
    & $adbPath disconnect $deviceAddress 2>&1 | Out-Null
    
    # Connect device
    Write-Host "Connecting..." -ForegroundColor Yellow
    $connectOutput = & $adbPath connect $deviceAddress 2>&1
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Connected successfully!" -ForegroundColor Green
        Write-Host $connectOutput
    } else {
        Write-Host "Connection failed!" -ForegroundColor Red
        Write-Host $connectOutput
        Write-Host "`nPlease ensure:" -ForegroundColor Yellow
        Write-Host "1. Phone and computer are on the same WiFi network" -ForegroundColor White
        Write-Host "2. Phone has wireless debugging enabled" -ForegroundColor White
        Write-Host "3. Port number is correct (current: $devicePort)" -ForegroundColor White
        exit 1
    }
}

# Check device list again
Write-Host "`nCurrently connected devices:" -ForegroundColor Cyan
$devices = & $adbPath devices
Write-Host $devices

# Verify device is available
$deviceStatus = & $adbPath -s $deviceAddress get-state 2>&1
if ($deviceStatus -match "device") {
    Write-Host "`nDevice status is normal, ready to use!" -ForegroundColor Green
} else {
    Write-Host "`nWarning: Device status is abnormal" -ForegroundColor Yellow
    Write-Host "Status: $deviceStatus" -ForegroundColor White
}

Write-Host "`nDevice address: $deviceAddress" -ForegroundColor Cyan
Write-Host "You can use the following command to operate the device:" -ForegroundColor Yellow
Write-Host "  adb -s $deviceAddress <command>" -ForegroundColor White

