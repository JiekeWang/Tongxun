# 消息接收问题诊断脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  消息接收问题诊断" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 检查服务端日志
Write-Host "1. 检查服务端日志..." -ForegroundColor Yellow
Write-Host "   执行以下命令查看服务端日志：" -ForegroundColor White
Write-Host "   ssh root@47.116.197.230" -ForegroundColor Green
Write-Host "   pm2 logs --lines 100 | grep -E '发送消息|message|用户.*连接'" -ForegroundColor Green
Write-Host ""

# 2. 检查客户端日志
Write-Host "2. 检查客户端日志..." -ForegroundColor Yellow
Write-Host "   在 Android Studio Logcat 中搜索：" -ForegroundColor White
Write-Host "   - '🔥 收到message事件'" -ForegroundColor Green
Write-Host "   - '✅ 解析消息成功'" -ForegroundColor Green
Write-Host "   - '🔥🔥🔥 收到消息通知'" -ForegroundColor Green
Write-Host "   - '✅✅✅ 消息已保存到本地数据库'" -ForegroundColor Green
Write-Host ""

# 3. 关键检查点
Write-Host "3. 关键检查点：" -ForegroundColor Yellow
Write-Host "   [ ] 发送方是否看到 '✅ 已发送消息到服务器'" -ForegroundColor White
Write-Host "   [ ] 服务端是否看到 '发送消息给用户 - userId: [接收者ID]'" -ForegroundColor White
Write-Host "   [ ] 服务端是否看到 '消息已发送' 或 '用户离线'" -ForegroundColor White
Write-Host "   [ ] 接收方是否看到 '🔥 收到message事件'" -ForegroundColor White
Write-Host "   [ ] 接收方是否看到 '✅✅✅ 消息已保存到本地数据库'" -ForegroundColor White
Write-Host ""

# 4. 常见问题
Write-Host "4. 常见问题：" -ForegroundColor Yellow
Write-Host "   问题1: 接收者未连接" -ForegroundColor Red
Write-Host "   - 症状: 服务端日志显示 '用户 xxx 离线'" -ForegroundColor White
Write-Host "   - 解决: 确保接收者已登录并打开应用" -ForegroundColor Green
Write-Host ""
Write-Host "   问题2: 接收者连接但收不到消息" -ForegroundColor Red
Write-Host "   - 症状: 服务端显示已发送，但客户端没收到" -ForegroundColor White
Write-Host "   - 解决: 检查客户端事件监听是否正确" -ForegroundColor Green
Write-Host ""
Write-Host "   问题3: receiverId 不正确" -ForegroundColor Red
Write-Host "   - 症状: 服务端找不到接收者连接" -ForegroundColor White
Write-Host "   - 解决: 检查发送消息时使用的 receiverId" -ForegroundColor Green
Write-Host ""

# 5. 测试步骤
Write-Host "5. 测试步骤：" -ForegroundColor Yellow
Write-Host "   步骤1: 发送方发送一条消息" -ForegroundColor White
Write-Host "   步骤2: 查看发送方 Logcat，确认看到 '✅ 已发送消息到服务器'" -ForegroundColor White
Write-Host "   步骤3: 查看服务端日志，确认看到 '发送消息给用户'" -ForegroundColor White
Write-Host "   步骤4: 查看接收方 Logcat，确认看到 '🔥 收到message事件'" -ForegroundColor White
Write-Host "   步骤5: 查看接收方 Logcat，确认看到 '✅✅✅ 消息已保存到本地数据库'" -ForegroundColor White
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  诊断完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

