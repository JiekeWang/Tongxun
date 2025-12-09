# 查看应用日志脚本
# 用于快速查看 com.tongxun 应用的日志

$adbPath = "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.4.28:43271"

function Write-ColorOutput {
    param (
        [Parameter(Mandatory=$true)][string]$Message,
        [Parameter(Mandatory=$true)][string]$Color
    )
    Write-Host $Message -ForegroundColor $Color
}

if (-not (Test-Path $adbPath)) {
    Write-ColorOutput "错误: 未找到 ADB 命令" "Red"
    exit 1
}

Write-ColorOutput "`n=== 应用日志查看工具 ===" "Cyan"
Write-ColorOutput "设备: $device" "Green"
Write-ColorOutput "`n请选择查看模式:" "Yellow"
Write-ColorOutput "1. 查看所有应用日志（实时）" "White"
Write-ColorOutput "2. 查看用户信息相关日志（实时）" "White"
Write-ColorOutput "3. 查看关键标记日志（实时，包含 🔥🔥🔥）" "White"
Write-ColorOutput "4. 查看单聊用户信息加载日志（实时）" "White"
Write-ColorOutput "5. 查看最近的应用日志（历史，最后100行）" "White"
Write-ColorOutput "6. 搜索特定关键字" "White"
Write-ColorOutput "`n请输入选项 (1-6): " "Cyan" -NoNewline

$choice = Read-Host

switch ($choice) {
    "1" {
        Write-ColorOutput "`n开始查看所有应用日志（按 Ctrl+C 停止）..." "Green"
        & $adbPath -s $device logcat -s "com.tongxun:*" "*:E" "*:W" "*:I"
    }
    "2" {
        Write-ColorOutput "`n开始查看用户信息相关日志（按 Ctrl+C 停止）..." "Green"
        & $adbPath -s $device logcat -s "MessageAdapter:*" "ChatActivity:*" "ChatViewModel:*" "UserRepositoryImpl:*"
    }
    "3" {
        Write-ColorOutput "`n开始查看关键标记日志（按 Ctrl+C 停止）..." "Green"
        & $adbPath -s $device logcat | Select-String -Pattern "🔥|MessageAdapter|ChatActivity|ChatViewModel|UserRepositoryImpl|getUserInfo|用户信息|单聊|senderId"
    }
    "4" {
        Write-ColorOutput "`n开始查看单聊用户信息加载日志（按 Ctrl+C 停止）..." "Green"
        & $adbPath -s $device logcat | Select-String -Pattern "单聊|senderId|getUserInfo|用户信息|ReceivedTextViewHolder|displaySenderInfo"
    }
    "5" {
        Write-ColorOutput "`n查看最近的应用日志..." "Green"
        & $adbPath -s $device logcat -s "com.tongxun:*" "*:E" "*:W" "*:I" -d | Select-Object -Last 100
    }
    "6" {
        Write-ColorOutput "`n请输入要搜索的关键字: " "Cyan" -NoNewline
        $keyword = Read-Host
        Write-ColorOutput "`n开始搜索关键字 '$keyword'（按 Ctrl+C 停止）..." "Green"
        & $adbPath -s $device logcat | Select-String -Pattern $keyword
    }
    default {
        Write-ColorOutput "`n无效选项" "Red"
    }
}

