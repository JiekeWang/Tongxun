# 诊断登录问题的 PowerShell 脚本

Write-Host "=== 诊断登录问题 ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "请按以下步骤操作：" -ForegroundColor Yellow
Write-Host "1. 清空 Logcat 日志" -ForegroundColor White
Write-Host "2. 在登录页面输入手机号和密码" -ForegroundColor White
Write-Host "3. 点击登录按钮" -ForegroundColor White
Write-Host "4. 查看 Logcat 输出" -ForegroundColor White
Write-Host ""

Write-Host "在 Logcat 中搜索以下关键词：" -ForegroundColor Yellow
Write-Host "  - LoginViewModel" -ForegroundColor White
Write-Host "  - AuthRepository" -ForegroundColor White
Write-Host "  - login" -ForegroundColor White
Write-Host ""

Write-Host "应该看到的日志顺序：" -ForegroundColor Yellow
Write-Host "  1. LoginViewModel.login() 被调用" -ForegroundColor Green
Write-Host "  2. 输入参数 - phoneNumber长度: 11, password长度: X" -ForegroundColor Green
Write-Host "  3. 清理后 - phoneNumber长度: 11, password长度: X" -ForegroundColor Green
Write-Host "  4. 输入验证通过，开始登录" -ForegroundColor Green
Write-Host "  5. AuthRepository.login() 被调用" -ForegroundColor Green
Write-Host "  6. 验证通过，准备发送登录请求" -ForegroundColor Green
Write-Host "  7. 发送登录请求 - phoneNumber: xxx***, password长度: X" -ForegroundColor Green
Write-Host "  8. 收到登录响应 - token长度: X, userId: xxx" -ForegroundColor Green
Write-Host ""

Write-Host "如果看到错误：" -ForegroundColor Yellow
Write-Host "  - '手机号格式不正确' -> 检查手机号是否符合格式：1[3-9]xxxxxxxxx" -ForegroundColor Red
Write-Host "  - '手机号或密码不能为空' -> 检查输入框是否有值" -ForegroundColor Red
Write-Host "  - '密码长度至少6位' -> 检查密码长度" -ForegroundColor Red
Write-Host ""

Write-Host "开始监控 Logcat..." -ForegroundColor Cyan
Write-Host ""

# 清空 Logcat
adb logcat -c

# 监控 Logcat，过滤登录相关的日志
adb logcat | Select-String -Pattern "LoginViewModel|AuthRepository|login|手机号|密码" -Context 0,2

