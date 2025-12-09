# WebRTC 库配置指南

## 当前状态

视频通话功能的代码已经实现并启用，但需要配置 WebRTC 库依赖才能正常编译和运行。

## 方案 1：使用 Google WebRTC（推荐用于生产环境）

### 步骤 1：从源码编译 WebRTC

1. **安装依赖工具**
   ```bash
   # 安装 depot_tools
   git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
   export PATH=$PATH:/path/to/depot_tools
   ```

2. **获取 WebRTC 源码**
   ```bash
   mkdir webrtc_android
   cd webrtc_android
   fetch --nohooks webrtc_android
   cd src
   ```

3. **编译 Android 版本**
   ```bash
   # 生成构建文件
   gn gen out/Default --args='target_os="android" target_cpu="arm64"'
   
   # 编译
   ninja -C out/Default
   ```

4. **生成 AAR 文件**
   - 编译完成后，在 `out/Default` 目录找到生成的库文件
   - 或者使用官方提供的构建脚本

5. **添加到项目**
   - 将生成的 `libwebrtc.aar` 放到 `app/libs/` 目录
   - 在 `app/build.gradle` 中添加：
     ```gradle
     dependencies {
         implementation files('libs/libwebrtc.aar')
     }
     ```

### 参考文档
- 官方文档：https://webrtc.googlesource.com/src/+/main/docs/native-code/android/

## 方案 2：使用第三方维护的预编译库

### 选项 A：使用 JitPack 上的库（如果可用）

在 `app/build.gradle` 中添加：
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    // 需要找到可用的库，例如：
    // implementation 'com.github.xxx:webrtc-android:version'
}
```

### 选项 B：手动下载预编译 AAR

1. 从可信来源下载 WebRTC Android AAR 文件
2. 放到 `app/libs/` 目录
3. 在 `app/build.gradle` 中添加：
   ```gradle
   dependencies {
       implementation files('libs/webrtc-android.aar')
   }
   ```

## 方案 3：使用商业 SDK（快速集成）

### Agora SDK
```gradle
dependencies {
    implementation 'io.agora.rtc:full-sdk:4.x.x'
}
```
- 优点：集成简单，文档完善，有免费额度
- 缺点：商业产品，有使用限制

### 腾讯云 TRTC
```gradle
dependencies {
    implementation 'com.tencent.liteav:LiteAVSDK_TRTC:latest'
}
```
- 优点：功能完善，有免费额度
- 缺点：需要修改代码以适配 SDK

## 方案 4：临时占位实现（用于开发测试）

如果暂时无法获取 WebRTC 库，可以创建一个占位实现：

1. **创建占位类**
   - 在 `app/src/main/java/com/tongxun/webrtc/` 创建占位类
   - 实现基本的接口，但不包含实际的 WebRTC 功能

2. **修改 WebRTCManager**
   - 使用占位实现，让代码可以编译通过
   - 实际功能需要等 WebRTC 库配置完成后再启用

## 当前项目配置

在 `app/build.gradle` 中，WebRTC 依赖已被注释。您需要：

1. 选择一个方案
2. 按照方案配置 WebRTC 库
3. 取消注释 `app/build.gradle` 中的 WebRTC 依赖（如果使用 Maven 库）
4. 同步项目并重新编译

## 验证

配置完成后，验证步骤：

1. 项目可以正常编译
2. 在单聊界面可以看到视频通话按钮
3. 点击视频通话按钮可以打开视频通话界面
4. 可以正常发起和接收视频通话

## 注意事项

1. **权限**：确保在 `AndroidManifest.xml` 中声明了 `CAMERA` 和 `RECORD_AUDIO` 权限
2. **网络**：WebRTC 需要稳定的网络连接
3. **STUN/TURN 服务器**：生产环境建议配置自己的 TURN 服务器
4. **测试**：在不同网络环境下测试通话质量

## 推荐方案

- **开发阶段**：使用商业 SDK（如 Agora）快速验证功能
- **生产环境**：从 Google WebRTC 源码编译，获得更好的控制和定制能力

