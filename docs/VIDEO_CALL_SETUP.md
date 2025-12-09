# 视频通话功能配置说明

## 当前状态

视频通话功能的代码已经实现，但由于无法找到可用的 WebRTC Android 库，相关文件已被暂时禁用（重命名为 `.disabled` 后缀），以确保项目能够正常编译。

**已禁用的文件：**
- `app/src/main/java/com/tongxun/ui/video/VideoCallActivity.kt.disabled`
- `app/src/main/java/com/tongxun/ui/video/VideoCallViewModel.kt.disabled`
- `app/src/main/java/com/tongxun/webrtc/WebRTCManager.kt.disabled`
- `docs/activity_video_call.xml.disabled`（已移出 res 目录）

## 已实现的功能

1. ✅ **客户端代码**
   - `WebRTCManager.kt` - WebRTC 连接管理器
   - `VideoCallActivity.kt` - 视频通话界面
   - `VideoCallViewModel.kt` - 视频通话状态管理
   - 视频通话 UI 布局和资源文件

2. ✅ **服务器端代码**
   - WebSocket 信令处理（`video_call`, `video_call_sdp`, `video_call_ice` 等事件）

## 如何启用视频通话功能

### 方案 1：使用 Google WebRTC（推荐用于生产环境）

1. **从源码编译 WebRTC**
   ```bash
   # 克隆 WebRTC 源码
   git clone https://webrtc.googlesource.com/src.git
   cd src
   
   # 按照官方文档编译 Android 版本
   # 参考：https://webrtc.googlesource.com/src/+/main/docs/native-code/android/
   ```

2. **将编译好的 AAR 文件添加到项目**
   - 将生成的 `libwebrtc.aar` 放到 `app/libs/` 目录
   - 在 `app/build.gradle` 中添加：
     ```gradle
     dependencies {
         implementation files('libs/libwebrtc.aar')
     }
     ```

### 方案 2：使用第三方 WebRTC SDK

可以考虑使用以下商业或开源 SDK：

1. **Agora SDK**（商业，有免费额度）
   ```gradle
   implementation 'io.agora.rtc:full-sdk:4.x.x'
   ```

2. **腾讯云 TRTC**（商业，有免费额度）
   ```gradle
   implementation 'com.tencent.liteav:LiteAVSDK_TRTC:latest'
   ```

3. **声网 Agora**（开源版本）
   - 需要从官方获取 SDK

### 方案 3：使用 JitPack 上的 WebRTC 库（如果可用）

如果找到可用的 JitPack 库，可以：

1. 在 `app/build.gradle` 中取消注释：
   ```gradle
   // WebRTC for video calling
   implementation 'com.github.xxx:webrtc-android:version'
   ```

2. 取消注释以下文件中的相关代码：
   - `app/src/main/java/com/tongxun/ui/chat/ChatActivity.kt`
   - `app/src/main/AndroidManifest.xml`

## 启用步骤

1. **恢复禁用的文件**
   - 将 `.disabled` 后缀的文件重命名回原始名称：
     - `app/src/main/java/com/tongxun/ui/video/VideoCallActivity.kt.disabled` → `VideoCallActivity.kt`
     - `app/src/main/java/com/tongxun/ui/video/VideoCallViewModel.kt.disabled` → `VideoCallViewModel.kt`
     - `app/src/main/java/com/tongxun/webrtc/WebRTCManager.kt.disabled` → `WebRTCManager.kt`
     - `docs/activity_video_call.xml.disabled` → `app/src/main/res/layout/activity_video_call.xml`（需要移回 res 目录）

2. **取消注释 WebRTC 依赖**
   - 编辑 `app/build.gradle`，取消注释 WebRTC 依赖行

3. **取消注释客户端代码**
   - 编辑 `app/src/main/java/com/tongxun/ui/chat/ChatActivity.kt`
   - 取消注释 `setupToolbar()` 中的视频通话按钮代码
   - 取消注释 `onPrepareOptionsMenu()` 中的视频通话菜单代码
   - 取消注释 `startVideoCall()` 方法

4. **取消注释 AndroidManifest**
   - 编辑 `app/src/main/AndroidManifest.xml`
   - 取消注释 `VideoCallActivity` 的注册

5. **清理并重新构建**
   - 在 Android Studio 中执行 "Build" > "Clean Project"
   - 然后执行 "Build" > "Rebuild Project"
   - 或者运行 `./gradlew clean build`

## 注意事项

1. **权限要求**
   - 需要 `CAMERA` 和 `RECORD_AUDIO` 权限（已在 AndroidManifest 中声明）

2. **网络要求**
   - 需要稳定的网络连接
   - 建议配置 TURN 服务器以提高连接成功率

3. **STUN/TURN 服务器**
   - 当前使用 Google 公共 STUN 服务器
   - 生产环境建议配置自己的 TURN 服务器

## 测试

启用后，可以在单聊界面点击工具栏的视频通话按钮来测试功能。

