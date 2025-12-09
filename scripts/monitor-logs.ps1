# 日志监控脚本
# 用于实时监控应用日志并分析问题

$adbPath = "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$device = "192.168.4.28:43271"
$logFile = "app-logs-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"

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

Write-ColorOutput "`n=== 应用日志监控工具 ===" "Cyan"
Write-ColorOutput "设备: $device" "Green"
Write-ColorOutput "日志文件: $logFile" "Green"
Write-ColorOutput "`n正在清空日志缓存..." "Yellow"
& $adbPath -s $device logcat -c | Out-Null

Write-ColorOutput "`n开始实时监控..." "Green"
Write-ColorOutput "监控的标签: MessageAdapter, ChatActivity, ChatViewModel, UserRepositoryImpl, ConversationRepository, ConversationAdapter" "Cyan"
Write-ColorOutput "以及所有错误和警告日志" "Cyan"
Write-ColorOutput "`n请现在操作应用（打开单聊界面等）..." "Yellow"
Write-ColorOutput "按 Ctrl+C 停止监控并分析问题`n" "Yellow"

# 启动日志监控，同时输出到控制台和文件
try {
    & $adbPath -s $device logcat -s `
        "MessageAdapter:*" `
        "ChatActivity:*" `
        "ChatViewModel:*" `
        "UserRepositoryImpl:*" `
        "ConversationRepository:*" `
        "ConversationAdapter:*" `
        "*:E" `
        "*:W" | Tee-Object -FilePath $logFile
} catch {
    Write-ColorOutput "`n监控已停止" "Yellow"
    Write-ColorOutput "日志已保存到: $logFile" "Green"
}

