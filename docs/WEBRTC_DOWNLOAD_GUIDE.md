# WebRTC Android 库下载指南

## 推荐方案：使用预编译 AAR 文件（最简单）

### 方案 1：从 GitHub 下载预编译库（推荐）

#### 选项 A：使用 webrtc-sdk 项目（推荐）

1. **访问 GitHub 仓库**
   - 仓库地址：https://github.com/webrtc-sdk/android
   - 或者搜索 "webrtc-sdk android" 在 GitHub 上

2. **下载 AAR 文件**
   - 进入 Releases 页面
   - 下载最新版本的 `webrtc-android-xxx.aar` 文件
   - 或者从 `app/build/outputs/aar/` 目录获取

3. **放置到项目**
   - 将下载的 AAR 文件重命名为 `libwebrtc.aar`
   - 放到项目的 `app/libs/` 目录（如果没有则创建）

4. **配置 build.gradle**
   ```gradle
   dependencies {
       implementation files('libs/libwebrtc.aar')
   }
   ```

#### 选项 B：使用 Google 官方预编译版本

1. **访问 WebRTC 官方构建**
   - 访问：https://chromium.googlesource.com/external/webrtc/+/HEAD
   - 或者使用 Maven Central（如果可用）

2. **下载方式**
   - 需要从源码构建，或者寻找社区维护的预编译版本

### 方案 2：使用 Maven 依赖（如果可用）

在 `app/build.gradle` 中添加：

```gradle
repositories {
    mavenCentral()
    google()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // 尝试使用以下之一（需要验证可用性）：
    // implementation 'org.webrtc:google-webrtc:1.0.32006'
    // 或者
    // implementation 'com.github.webrtc-sdk:android:114.5735.08'
}
```

**注意**：之前尝试过这些依赖，但可能无法解析。建议优先使用方案 1（手动下载 AAR）。

### 方案 3：从源码编译（最复杂，但最灵活）

如果您需要自定义功能，可以从源码编译：

1. **安装 depot_tools**
   ```bash
   git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
   export PATH=$PATH:/path/to/depot_tools
   ```

2. **获取源码**
   ```bash
   mkdir webrtc_android
   cd webrtc_android
   fetch --nohooks webrtc_android
   cd src
   ```

3. **编译 Android 版本**
   ```bash
   gn gen out/Default --args='target_os="android" target_cpu="arm64"'
   ninja -C out/Default
   ```

4. **生成 AAR**
   - 使用构建脚本生成 AAR 文件
   - 参考：https://webrtc.googlesource.com/src/+/main/docs/native-code/android/

## 快速开始（推荐步骤）

### 步骤 1：创建 libs 目录

```bash
# 在项目根目录执行
mkdir -p app/libs
```

### 步骤 2：下载 AAR 文件

**推荐下载源：**

1. **GitHub Releases**（优先）
   - 搜索 "webrtc android aar" 在 GitHub
   - 查找有 Releases 的仓库，下载最新的 AAR 文件

2. **Maven Central**（如果可用）
   - 访问：https://mvnrepository.com/artifact/org.webrtc/google-webrtc
   - 下载对应版本的 AAR 文件

3. **社区维护版本**
   - 搜索 "webrtc android prebuilt aar"
   - 选择可信的社区维护版本

### 步骤 3：配置项目

1. **将 AAR 文件放到 `app/libs/` 目录**
   - 文件名建议：`libwebrtc.aar`

2. **修改 `app/build.gradle`**
   ```gradle
   dependencies {
       // 其他依赖...
       
       // WebRTC 库（本地 AAR）
       implementation files('libs/libwebrtc.aar')
   }
   ```

3. **同步项目**
   - 在 Android Studio 中点击 "Sync Now"

### 步骤 4：验证

1. 项目应该可以正常编译
2. 检查 `WebRTCManager.kt` 中的导入是否正常
3. 运行项目，测试视频通话功能

## 具体下载链接（需要验证）

由于 WebRTC 官方不直接提供预编译的 AAR，建议：

1. **GitHub 搜索**
   - 在 GitHub 搜索 "webrtc android aar"
   - 查找有活跃维护的项目

2. **社区资源**
   - WebRTC 中文网：https://webrtc.org.cn/
   - 可能有预编译版本下载

3. **自己编译**
   - 如果找不到预编译版本，可以自己从源码编译

## 注意事项

1. **版本兼容性**
   - 确保下载的 AAR 版本与您的 Android 项目兼容
   - 建议使用较新的稳定版本

2. **架构支持**
   - 确保 AAR 支持您需要的 CPU 架构（arm64-v8a, armeabi-v7a, x86, x86_64）

3. **文件大小**
   - WebRTC AAR 文件通常较大（几十 MB），这是正常的

4. **许可证**
   - WebRTC 使用 BSD 许可证，可以商用

## 如果找不到预编译版本

如果找不到可用的预编译 AAR，您可以：

1. **使用商业 SDK**（快速但有限制）
   - Agora SDK
   - 腾讯云 TRTC
   - 声网等

2. **从源码编译**（复杂但灵活）
   - 按照方案 3 的步骤操作

3. **继续使用占位实现**（开发阶段）
   - 当前代码使用占位实现，可以正常编译
   - 等找到 WebRTC 库后再替换

