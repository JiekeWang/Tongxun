# 查看 WebSocket 日志指南

## 问题
日志中没有看到 WebSocket 相关的日志，可能的原因和解决方法。

## 解决步骤

### 1. 确保应用已重新运行

**重要**：修改代码后，必须重新运行应用才能看到新的日志。

**方法**：
1. 停止当前运行的应用（点击停止按钮 ⏹️）
2. 重新运行应用（点击运行按钮 ▶️）
3. 或使用快捷键：`Shift + F10`

### 2. 检查 Logcat 过滤器

**默认过滤器可能过滤掉了我们的日志**，需要调整：

#### 方法一：清除所有过滤器
1. 打开 Logcat（底部面板）
2. 点击过滤器下拉菜单
3. 选择 "No Filters" 或 "Show only selected application"

#### 方法二：使用自定义过滤器
1. 点击 Logcat 的过滤器图标（漏斗图标）
2. 创建新过滤器：
   - **Name**: WebSocket
   - **Log Tag**: `MainActivity|MainViewModel|WebSocketManager`
   - **Log Level**: Debug 及以上

#### 方法三：直接搜索
在 Logcat 搜索框中输入：
```
MainActivity OR MainViewModel OR WebSocketManager
```

### 3. 查看应用启动日志

WebSocket 初始化在应用启动时进行，需要查看完整的启动日志：

1. **清空 Logcat**：
   - 点击 Logcat 的清空按钮（垃圾桶图标）
   - 或按 `Ctrl + L`

2. **重新运行应用**：
   - 停止应用
   - 重新运行

3. **查看启动日志**：
   - 从应用启动开始查看
   - 应该能看到：
     - `=== MainActivity.onCreate() ===`
     - `=== MainActivity.initWebSocket() 开始 ===`
     - `=== MainViewModel.init() 开始 ===`
     - `=== WebSocketManager.initialize() 被调用 ===`

### 4. 检查日志级别

确保 Logcat 显示 Debug 级别的日志：

1. 在 Logcat 的日志级别下拉菜单中
2. 选择 "Debug" 或 "Verbose"
3. 不要选择 "Info"、"Warn" 或 "Error"（会过滤掉 Debug 日志）

### 5. 使用命令行查看（如果 IDE 看不到）

如果 Android Studio 的 Logcat 看不到日志，可以使用命令行：

```powershell
# 查看所有日志
adb logcat

# 只查看我们的日志
adb logcat -s MainActivity:V MainViewModel:V WebSocketManager:V

# 清空日志后重新查看
adb logcat -c
adb logcat -s MainActivity:V MainViewModel:V WebSocketManager:V
```

## 应该看到的日志顺序

正常启动时，应该按顺序看到：

```
1. === MainActivity.onCreate() ===
2. === MainActivity.initWebSocket() 开始 ===
3. 获取Token - token存在: true, token长度: xxx
4. 初始化WebSocket配置 - BASE_URL: ..., baseUrl: ...
5. === WebSocketManager.initialize() 被调用 ===
6. WebSocket初始化完成 - baseUrl: ..., wsUrl: ...
7. ✅ WebSocket配置完成，连接由MainViewModel管理
8. === MainViewModel.init() 开始 ===
9. === MainViewModel.setupWebSocketListener() 开始 ===
10. === WebSocketManager.connect() 被调用 ===
11. ✅ WebSocket URL已初始化 - URL: ...
12. 🚀 开始连接WebSocket - URL: ...
13. Socket.IO连接配置 - URL: ..., path: /ws, query: token=...
14. 已发起WebSocket连接请求
15. WebSocket连接成功
16. === MainViewModel.setupWebSocketListener() 结束 ===
```

## 如果仍然看不到日志

### 检查点 1：代码是否已保存
- 确保所有文件都已保存（`Ctrl + S`）
- 检查文件标签是否有 `*` 标记（表示未保存）

### 检查点 2：编译是否成功
- 查看 Build 输出窗口
- 确认没有编译错误
- 如果有错误，先修复错误

### 检查点 3：应用是否真的重新运行
- 查看 Logcat 中是否有应用启动的日志
- 如果没有，说明应用没有重新运行

### 检查点 4：设备连接
- 确认设备已连接（`adb devices`）
- 确认选择了正确的设备（Logcat 顶部）

## 快速测试命令

在 PowerShell 中执行：

```powershell
# 1. 清空日志
adb logcat -c

# 2. 查看 WebSocket 相关日志（实时）
adb logcat -s MainActivity:V MainViewModel:V WebSocketManager:V

# 3. 在另一个终端运行应用，或直接在 Android Studio 中运行
```

## 常见问题

### Q: 为什么看不到日志？
**A**: 可能的原因：
1. 应用没有重新运行（还在使用旧代码）
2. Logcat 过滤器过滤掉了日志
3. 日志级别设置太高（只显示 Error）
4. 代码没有保存或编译失败

### Q: 如何确认代码已更新？
**A**: 
1. 在代码中添加一个明显的日志，比如：
   ```kotlin
   Log.e(TAG, "🔥🔥🔥 这是测试日志 🔥🔥🔥")
   ```
2. 重新运行应用
3. 如果能看到这个日志，说明代码已更新

### Q: 日志太多怎么办？
**A**: 
1. 使用过滤器只显示我们需要的标签
2. 使用搜索功能搜索关键词
3. 清空日志后重新运行应用

