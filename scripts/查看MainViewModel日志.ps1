# 查看 MainViewModel 和 MainActivity 日志的 PowerShell 脚本

Write-Host "正在清空日志..." -ForegroundColor Yellow
adb logcat -c

Write-Host "`n开始查看日志（按 Ctrl+C 停止）..." -ForegroundColor Green
Write-Host "查找标签: MainActivity, MainViewModel, WebSocketManager" -ForegroundColor Cyan
Write-Host "查找关键词: 🔥, WebSocket, 连接" -ForegroundColor Cyan
Write-Host "`n" -ForegroundColor White

# 查看所有相关日志
adb logcat -s MainActivity:V MainViewModel:V WebSocketManager:V | Select-String -Pattern "🔥|WebSocket|连接|MainViewModel|MainActivity" -Context 0,2

