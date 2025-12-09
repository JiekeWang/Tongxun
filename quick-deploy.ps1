# 快速部署脚本 - 自动构建并安装 APK 到真机
# 使用方法: .\quick-deploy.ps1

param(
    [switch]$NoBuild,  # 跳过构建，只安装已存在的 APK
    [switch]$Launch,   # 安装后自动启动应用
    [string]$DeviceId   # 指定设备 ID（可选，如 "192.168.4.28:37157"）
)

$ErrorActionPreference = "Stop"

# 颜色输出函数
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) {
        Write-Output $args
    }
    $host.UI.RawUI.ForegroundColor = $fc
}

# 查找 ADB 路径
function Find-AdbPath {
    $possiblePaths = @(
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    )
    
    foreach ($path in $possiblePaths) {
        if (Test-Path $path) {
            return $path
        }
    }
    
    Write-ColorOutput Red "Error: ADB not found. Please set ANDROID_HOME environment variable"
    Write-ColorOutput Yellow "Or ensure ADB is in one of the following paths:"
    foreach ($path in $possiblePaths) {
        Write-Output "  - $path"
    }
    exit 1
}

$adbPath = Find-AdbPath
Write-ColorOutput Green "Found ADB: $adbPath"

# 检查设备连接
function Get-ConnectedDevices {
    $output = & $adbPath devices 2>&1
    $devices = @()
    
    foreach ($line in $output) {
        # 使用正则表达式匹配设备行
        $pattern = '^\s*([^\s]+)\s+(device|unauthorized)$'
        $match = [regex]::Match($line, $pattern)
        if ($match.Success) {
            $deviceId = $match.Groups[1].Value
            $status = $match.Groups[2].Value
            if ($status -eq "device") {
                $devices += $deviceId
            }
        }
    }
    
    return $devices
}

Write-ColorOutput Yellow "`nChecking connected devices..."
$devices = Get-ConnectedDevices

if ($devices.Count -eq 0) {
    Write-ColorOutput Red "Error: No connected devices detected"
    Write-ColorOutput Yellow "`nPlease ensure:"
    Write-Output "  1. Phone is connected via USB or wireless debugging is configured"
    Write-Output "  2. USB debugging is enabled"
    Write-Output "  3. This computer is authorized for debugging"
    Write-Output "  4. If using wireless debugging, run: adb connect <IP:PORT>"
    exit 1
}

Write-ColorOutput Green "Found $($devices.Count) device(s):"
foreach ($device in $devices) {
    Write-Output "  - $device"
}

# 选择设备
$targetDevice = $null
if ($DeviceId) {
    if ($devices -contains $DeviceId) {
        $targetDevice = $DeviceId
        Write-ColorOutput Green "Using specified device: $targetDevice"
    } else {
        Write-ColorOutput Red "Error: Specified device '$DeviceId' is not connected"
        exit 1
    }
} elseif ($devices.Count -eq 1) {
    $targetDevice = $devices[0]
    Write-ColorOutput Green "Auto-selected device: $targetDevice"
} else {
    Write-ColorOutput Yellow "`nPlease select a device to install:"
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Output "  [$i] $($devices[$i])"
    }
    $selection = Read-Host "Enter number (0-$($devices.Count - 1))"
    try {
        $index = [int]$selection
        if ($index -ge 0 -and $index -lt $devices.Count) {
            $targetDevice = $devices[$index]
        } else {
            Write-ColorOutput Red "Error: Invalid selection"
            exit 1
        }
    } catch {
        Write-ColorOutput Red "Error: Invalid input"
        exit 1
    }
}

# 构建 APK
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not $NoBuild) {
    Write-ColorOutput Yellow "`nStarting APK build..."
    Write-ColorOutput Cyan "This may take a few minutes..."
    
    # 检查是否有 Gradle Wrapper
    $gradleCmd = $null
    if (Test-Path "gradlew.bat") {
        $gradleCmd = ".\gradlew.bat"
    } elseif (Test-Path "gradlew") {
        $gradleCmd = ".\gradlew"
    } elseif (Get-Command "gradle" -ErrorAction SilentlyContinue) {
        $gradleCmd = "gradle"
    } else {
        Write-ColorOutput Red "Error: Gradle not found. Please install Gradle or use Android Studio to build."
        Write-ColorOutput Yellow "You can also use: .\quick-deploy.ps1 -NoBuild (to skip build and only install existing APK)"
        exit 1
    }
    
    try {
        & $gradleCmd assembleDebug --no-daemon 2>&1 | ForEach-Object {
            if ($_ -match "BUILD SUCCESSFUL") {
                Write-ColorOutput Green $_
            } elseif ($_ -match "BUILD FAILED|FAILURE|ERROR") {
                Write-ColorOutput Red $_
            } else {
                Write-Output $_
            }
        }
        
        if ($LASTEXITCODE -ne 0) {
            Write-ColorOutput Red "`nError: Build failed!"
            exit 1
        }
        
        Write-ColorOutput Green "`nAPK build successful!"
    } catch {
        Write-ColorOutput Red "`nError: Build process failed: $_"
        exit 1
    }
} else {
    Write-ColorOutput Yellow "`nSkipping build step"
}

# 检查 APK 是否存在
if (-not (Test-Path $apkPath)) {
    Write-ColorOutput Red "`nError: APK file not found: $apkPath"
    Write-ColorOutput Yellow "Please build APK first or remove -NoBuild parameter"
    exit 1
}

$apkInfo = Get-Item $apkPath
$apkSize = [math]::Round($apkInfo.Length / 1MB, 2)
Write-ColorOutput Green "`nFound APK: $apkPath"
Write-ColorOutput Cyan "File size: $apkSize MB"
Write-ColorOutput Cyan "Modified: $($apkInfo.LastWriteTime)"

# 安装 APK
Write-ColorOutput Yellow "`nInstalling APK to device: $targetDevice..."
$installOutput = & $adbPath -s $targetDevice install -r $apkPath 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-ColorOutput Green "`nAPK installed successfully!"
    
    # 可选：启动应用
    if ($Launch) {
        Write-ColorOutput Yellow "`nLaunching app..."
        & $adbPath -s $targetDevice shell am start -n com.tongxun/.ui.auth.LoginActivity
        if ($LASTEXITCODE -eq 0) {
            Write-ColorOutput Green "App launched"
        } else {
            Write-ColorOutput Yellow "Warning: Failed to launch app, please open manually"
        }
    }
    
    Write-ColorOutput Green "`nDeployment completed!"
} else {
    Write-ColorOutput Red "`nError: APK installation failed!"
    Write-Output $installOutput
    exit 1
}

