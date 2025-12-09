# APK Installation Script
# Supports both USB and Wireless Debugging
# Usage:
#   .\install-apk.ps1                    # Use default port 43271
#   .\install-apk.ps1 -Port 43271       # Specify port number
#   .\install-apk.ps1 -Port 43271 -Ip 192.168.4.28  # Specify IP and port

param(
    [Parameter(Mandatory=$false)]
    [string]$Port = "43271",  # Default port
    
    [Parameter(Mandatory=$false)]
    [string]$Ip = "192.168.4.28"  # Default IP
)

$debugApk = "app\build\outputs\apk\debug\app-debug.apk"
$releaseApk = "app\build\outputs\apk\release\app-release.apk"

# Wireless debugging device info
$wirelessDeviceIp = $Ip
$wirelessDevicePort = $Port
$wirelessDeviceAddress = "$wirelessDeviceIp`:$wirelessDevicePort"

Write-Host "Wireless Device IP: $wirelessDeviceIp" -ForegroundColor Cyan
Write-Host "Wireless Device Port: $wirelessDevicePort" -ForegroundColor Cyan
Write-Host ""

Write-Host "Searching for APK files..." -ForegroundColor Yellow

if (Test-Path $debugApk) {
    $apkPath = Resolve-Path $debugApk
    Write-Host "`nFound Debug APK: $apkPath" -ForegroundColor Green
    Write-Host "File size: $([math]::Round((Get-Item $apkPath).Length / 1MB, 2)) MB" -ForegroundColor Cyan
    
    # Find ADB path
    $adbPath = "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    
    if (-not (Test-Path $adbPath)) {
        # Try other possible paths
        $possiblePaths = @(
            "$env:ANDROID_HOME\platform-tools\adb.exe",
            "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
            "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe"
        )
        
        foreach ($path in $possiblePaths) {
            if (Test-Path $path) {
                $adbPath = $path
                break
            }
        }
    }
    
    if (Test-Path $adbPath) {
        Write-Host "`nUsing ADB: $adbPath" -ForegroundColor Green
        
        # Try to connect wireless debugging device if not connected
        Write-Host "`nChecking wireless debugging device connection..." -ForegroundColor Yellow
        $devices = & $adbPath devices 2>&1
        
        if ($devices -notmatch $wirelessDeviceAddress) {
            Write-Host "Wireless device not connected, connecting..." -ForegroundColor Yellow
            & $adbPath disconnect $wirelessDeviceAddress 2>&1 | Out-Null
            $connectOutput = & $adbPath connect $wirelessDeviceAddress 2>&1
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Wireless device connected successfully!" -ForegroundColor Green
            } else {
                Write-Host "Wireless device connection failed, will try USB devices" -ForegroundColor Yellow
            }
        }
        
        # Re-check device list
        Write-Host "`nChecking connected devices..." -ForegroundColor Yellow
        $devices = & $adbPath devices
        Write-Host $devices
        
        $connectedDevices = & $adbPath devices | Select-Object -Skip 1 | Where-Object { $_ -match "device$" }
        
        if ($connectedDevices) {
            $targetDevice = $null
            
            # Prefer wireless debugging device
            if ($connectedDevices -match $wirelessDeviceAddress) {
                $targetDevice = $wirelessDeviceAddress
                Write-Host "`nUsing wireless device: $targetDevice" -ForegroundColor Green
            } else {
                # Use first available device
                $targetDevice = ($connectedDevices[0] -split '\s+')[0]
                Write-Host "`nUsing device: $targetDevice" -ForegroundColor Green
            }
            
            Write-Host "Do you want to install APK? (Y/N): " -ForegroundColor Cyan -NoNewline
            $response = Read-Host
            
            if ($response -eq "Y" -or $response -eq "y") {
                Write-Host "Installing APK to device: $targetDevice..." -ForegroundColor Yellow
                & $adbPath -s $targetDevice install -r $apkPath
                if ($LASTEXITCODE -eq 0) {
                    Write-Host "`nAPK installed successfully!" -ForegroundColor Green
                } else {
                    Write-Host "`nAPK installation failed, please check error messages" -ForegroundColor Red
                }
            }
        } else {
            Write-Host "`nNo connected devices detected" -ForegroundColor Yellow
            Write-Host "Please ensure:" -ForegroundColor Cyan
            Write-Host "1. Phone is connected via USB, or" -ForegroundColor White
            Write-Host "2. Phone has wireless debugging enabled (IP: $wirelessDeviceIp, Port: $wirelessDevicePort)" -ForegroundColor White
            Write-Host "3. USB debugging mode is enabled" -ForegroundColor White
            Write-Host "4. This computer is authorized for debugging" -ForegroundColor White
        }
    } else {
        Write-Host "`nADB not found, APK file path copied to clipboard" -ForegroundColor Yellow
    }
    
    Write-Host "`nAPK file path copied to clipboard" -ForegroundColor Green
    $apkPath | Set-Clipboard
    Write-Host "You can manually copy APK to phone for installation" -ForegroundColor Cyan
} elseif (Test-Path $releaseApk) {
    $apkPath = Resolve-Path $releaseApk
    Write-Host "`nFound Release APK: $apkPath" -ForegroundColor Green
    Write-Host "File size: $([math]::Round((Get-Item $apkPath).Length / 1MB, 2)) MB" -ForegroundColor Cyan
    $apkPath | Set-Clipboard
} else {
    Write-Host "`nAPK file not found!" -ForegroundColor Red
    Write-Host "`nPlease generate APK in Android Studio first:" -ForegroundColor Yellow
    Write-Host "Build -> Build Bundle(s) / APK(s) -> Build APK(s)" -ForegroundColor Cyan
    Write-Host "`nOr use Gradle panel:" -ForegroundColor Yellow
    Write-Host "app -> Tasks -> build -> assembleDebug" -ForegroundColor Cyan
}
