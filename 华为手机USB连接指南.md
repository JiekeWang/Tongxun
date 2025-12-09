# 华为手机 USB 连接和测试指南

## 第一步：在手机上启用 USB 调试

### 1.1 启用开发者选项
1. 打开手机的 **设置**
2. 找到 **关于手机** 或 **我的设备**
3. 连续点击 **版本号** 7次，直到提示"您已成为开发者"
4. 返回设置，找到 **开发者选项** 或 **系统** → **开发者选项**

### 1.2 启用 USB 调试
1. 进入 **开发者选项**
2. 找到 **USB 调试** 并打开
3. 找到 **USB 配置** 或 **默认 USB 配置**，选择 **文件传输** 或 **MTP**
4. （可选）打开 **仅充电模式下允许 ADB 调试**（如果存在）

### 1.3 安装华为手机驱动（如果需要）
如果电脑无法识别手机，可能需要安装：
- **华为手机助手（HiSuite）**：https://consumer.huawei.com/cn/support/hisuite/
- 或者让 Windows 自动安装驱动

## 第二步：连接手机到电脑

### 2.1 使用 USB 线连接
1. 使用 USB 数据线连接手机和电脑
2. 在手机上选择 **文件传输** 或 **MTP** 模式
3. 首次连接时，手机会弹出授权提示，点击 **允许** 或 **确定**

### 2.2 验证连接
在 PowerShell 中执行：
```powershell
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
```

应该能看到你的设备：
```
List of devices attached
ABC123XYZ456    device
```

如果显示 `unauthorized`，需要在手机上点击授权提示。

## 第三步：使用快速部署脚本安装应用

### 3.1 使用指定设备安装
```powershell
.\quick-deploy.ps1 -NoBuild -DeviceId "ABC123XYZ456" -Launch
```

### 3.2 或者让脚本自动选择
如果有多个设备，脚本会提示选择：
```powershell
.\quick-deploy.ps1 -NoBuild -Launch
```

然后输入设备对应的序号。

## 第四步：测试语音消息功能

### 4.1 登录应用
1. 打开应用（如果使用 `-Launch` 参数会自动打开）
2. 使用账号登录

### 4.2 测试发送语音消息（发送方）
1. 进入任意聊天界面（单聊或群聊）
2. 点击语音按钮（麦克风图标）
3. 录制几秒语音（建议 2-5 秒）
4. 点击停止按钮
5. **立即点击语音消息播放**，验证是否能立即播放

### 4.3 测试接收语音消息（接收方）
1. 在另一个设备上登录另一个账号
2. 进入同一个聊天界面
3. 等待收到语音消息
4. 点击语音消息播放，验证是否能正常播放

### 4.4 验证播放逻辑
- **发送方**：应该能立即播放（使用本地录制文件）
- **接收方**：应该能正常播放（从服务器下载后播放）

## 第五步：查看日志（如遇问题）

### 5.1 过滤应用日志
```powershell
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s ABC123XYZ456 logcat -s ChatViewModel:V VoiceRecordDialog:V ChatActivity:V MessageRepositoryImpl:V UploadRepository:V AudioPlayer:V *:E
```

### 5.2 或者使用 PowerShell 过滤
```powershell
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s ABC123XYZ456 logcat | Select-String -Pattern "ChatViewModel|VoiceRecordDialog|ChatActivity|AudioPlayer|UploadRepository"
```

## 常见问题

### Q1: 设备显示 "unauthorized"
- 在手机上查看是否有授权提示，点击"允许"
- 尝试断开 USB 线重新连接
- 在开发者选项中关闭并重新打开 USB 调试

### Q2: 设备列表中没有设备
- 检查 USB 线是否支持数据传输（不是只能充电的线）
- 尝试更换 USB 端口
- 检查手机是否选择了正确的 USB 模式（文件传输/MTP）
- 安装华为手机助手（HiSuite）以确保驱动正确

### Q3: 连接不稳定
- 尝试使用原装 USB 线
- 使用电脑后置 USB 端口（通常更稳定）
- 在开发者选项中打开 **USB 调试（安全设置）**（如果存在）

### Q4: 华为手机特殊设置
某些华为手机可能需要：
- 关闭 **仅充电模式下允许 ADB 调试**（如果开启后有问题）
- 打开 **USB 调试（安全设置）**
- 允许 **通过 USB 验证应用**

## 快速命令参考

```powershell
# 查看所有设备
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices

# 安装 APK 到指定设备（替换设备ID）
.\quick-deploy.ps1 -NoBuild -DeviceId "ABC123XYZ456" -Launch

# 查看日志（替换设备ID）
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" -s ABC123XYZ456 logcat -s ChatViewModel VoiceRecordDialog ChatActivity

# 重启 ADB 服务（如果连接有问题）
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" kill-server
& "C:\Users\56466\AppData\Local\Android\Sdk\platform-tools\adb.exe" start-server
```

## 华为手机特殊说明

### HarmonyOS 系统
如果手机运行的是 HarmonyOS（鸿蒙系统）：
- USB 调试功能可能位置不同
- 可能需要额外的授权步骤
- 建议使用华为手机助手进行首次连接

### EMUI 系统
如果手机运行的是 EMUI：
- USB 调试通常在 **开发者选项** 中
- 可能需要打开 **USB 调试（安全设置）**
- 某些版本可能需要关闭 **仅充电模式下允许 ADB 调试**

