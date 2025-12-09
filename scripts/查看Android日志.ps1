# 查看 Android Logcat 日志脚本
# 使用方法：在PowerShell中执行 .\查看Android日志.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Android Logcat 日志查看工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 adb 是否可用
$adbCheck = adb version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "错误: 未找到 adb 命令" -ForegroundColor Red
    Write-Host "请确保 Android SDK Platform Tools 已安装并添加到 PATH" -ForegroundColor Yellow
    exit 1
}

# 检查设备连接
Write-Host "检查设备连接..." -ForegroundColor Yellow
$devices = adb devices
if ($devices -notmatch "device$") {
    Write-Host "错误: 未检测到已连接的设备" -ForegroundColor Red
    Write-Host "请确保:" -ForegroundColor Yellow
    Write-Host "1. 设备已通过USB连接" -ForegroundColor White
    Write-Host "2. 已开启USB调试" -ForegroundColor White
    Write-Host "3. 已授权此电脑调试" -ForegroundColor White
    exit 1
}

Write-Host "设备已连接" -ForegroundColor Green
Write-Host ""

# 显示菜单
Write-Host "请选择操作:" -ForegroundColor Cyan
Write-Host "1. 查看实时日志（所有日志）" -ForegroundColor White
Write-Host "2. 查看应用相关日志（过滤 MainViewModel、MessageRepositoryImpl、WebSocketManager）" -ForegroundColor White
Write-Host "3. 查看 WebSocket 相关日志" -ForegroundColor White
Write-Host "4. 查看消息接收相关日志" -ForegroundColor White
Write-Host "5. 保存日志到文件" -ForegroundColor White
Write-Host "6. 清空日志缓冲区" -ForegroundColor White
Write-Host ""

$choice = Read-Host "请输入选项 (1-6)"

switch ($choice) {
    "1" {
        Write-Host "开始查看实时日志（按 Ctrl+C 退出）..." -ForegroundColor Green
        adb logcat
    }
    "2" {
        Write-Host "开始查看应用相关日志（按 Ctrl+C 退出）..." -ForegroundColor Green
        adb logcat -s MainViewModel:V MessageRepositoryImpl:V WebSocketManager:V MainActivity:V
    }
    "3" {
        Write-Host "开始查看 WebSocket 相关日志（按 Ctrl+C 退出）..." -ForegroundColor Green
        adb logcat | Select-String -Pattern "WebSocket|socket|ws"
    }
    "4" {
        Write-Host "开始查看消息接收相关日志（按 Ctrl+C 退出）..." -ForegroundColor Green
        adb logcat -s MainViewModel:V MessageRepositoryImpl:V WebSocketManager:V | Select-String -Pattern "消息|message|Message"
    }
    "5" {
        $fileName = Read-Host "请输入文件名（默认: logcat-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt）"
        if ([string]::IsNullOrWhiteSpace($fileName)) {
            $fileName = "logcat-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
        }
        Write-Host "开始保存日志到 $fileName（按 Ctrl+C 停止）..." -ForegroundColor Green
        adb logcat > $fileName
        Write-Host "日志已保存到: $fileName" -ForegroundColor Green
    }
    "6" {
        Write-Host "清空日志缓冲区..." -ForegroundColor Yellow
        adb logcat -c
        Write-Host "日志缓冲区已清空" -ForegroundColor Green
    }
    default {
        Write-Host "无效选项" -ForegroundColor Red
    }
}

