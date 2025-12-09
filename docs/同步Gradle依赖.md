# 同步 Gradle 依赖指南

## 方法一：Android Studio（推荐）

### 自动同步
1. **打开项目**：在 Android Studio 中打开项目
2. **自动提示**：修改 `build.gradle` 后，Android Studio 会在顶部显示黄色提示条
3. **点击 "Sync Now"**：点击提示条中的 "Sync Now" 按钮

### 手动同步
1. **菜单方式**：
   - 点击菜单栏：`File` → `Sync Project with Gradle Files`
   - 或使用快捷键：`Ctrl + Shift + O`（Windows/Linux）或 `Cmd + Shift + O`（Mac）

2. **工具栏方式**：
   - 点击工具栏右侧的 "Sync Project with Gradle Files" 图标（🔄）

3. **右键菜单**：
   - 在项目树中右键点击 `build.gradle` 文件
   - 选择 `Sync Gradle Files`

## 方法二：命令行

### Windows PowerShell
```powershell
cd E:\Tongxun
.\gradlew.bat build --refresh-dependencies
```

### Windows CMD
```cmd
cd E:\Tongxun
gradlew.bat build --refresh-dependencies
```

### Linux/Mac
```bash
cd /path/to/Tongxun
./gradlew build --refresh-dependencies
```

## 方法三：Gradle 面板

1. **打开 Gradle 面板**：
   - 点击右侧边栏的 "Gradle" 标签
   - 或菜单：`View` → `Tool Windows` → `Gradle`

2. **刷新依赖**：
   - 点击 Gradle 面板顶部的刷新图标（🔄）
   - 或右键点击项目名称，选择 `Refresh Gradle Project`

## 验证依赖是否同步成功

### 在 Android Studio 中
1. 打开 `app/build.gradle` 文件
2. 查看依赖列表，确认 `io.socket:socket.io-client:2.1.0` 已添加
3. 如果没有错误提示，说明同步成功

### 在命令行中
```powershell
# 查看依赖树（确认 Socket.IO 库已下载）
.\gradlew.bat app:dependencies | Select-String "socket"
```

## 常见问题

### 1. 同步失败 - 网络问题
**问题**：无法下载依赖
**解决**：
- 检查网络连接
- 配置代理（如果需要）
- 使用国内镜像源（如阿里云 Maven 镜像）

### 2. 同步失败 - 版本冲突
**问题**：依赖版本冲突
**解决**：
- 查看错误信息，找到冲突的依赖
- 在 `build.gradle` 中排除冲突的依赖
- 或统一使用相同版本

### 3. 同步很慢
**问题**：首次同步或网络慢
**解决**：
- 耐心等待（首次同步需要下载大量依赖）
- 使用国内镜像源加速
- 检查网络连接速度

## 配置国内镜像源（可选）

如果下载依赖很慢，可以在 `build.gradle` 中添加国内镜像：

```gradle
repositories {
    // 阿里云镜像（推荐）
    maven { url 'https://maven.aliyun.com/repository/public' }
    maven { url 'https://maven.aliyun.com/repository/google' }
    maven { url 'https://maven.aliyun.com/repository/central' }
    
    // 或者使用其他镜像
    // maven { url 'https://maven.aliyun.com/repository/jcenter' }
    
    google()
    mavenCentral()
}
```

## 检查 Socket.IO 依赖

同步完成后，可以通过以下方式确认 Socket.IO 库已添加：

1. **查看依赖树**：
   ```powershell
   .\gradlew.bat app:dependencies | Select-String "socket.io"
   ```

2. **在 Android Studio 中**：
   - 打开 `app/build.gradle`
   - 查看依赖列表，应该看到：
     ```gradle
     implementation ('io.socket:socket.io-client:2.1.0') {
         exclude group: 'org.json', module: 'json'
     }
     ```

3. **检查外部库**：
   - 在 Android Studio 中：`File` → `Project Structure` → `Dependencies`
   - 查看 `app` 模块的依赖，确认 `socket.io-client` 已列出

## 下一步

同步完成后：
1. ✅ 确认没有错误提示
2. ✅ 确认 Socket.IO 库已添加
3. ✅ 重新编译项目：`Build` → `Rebuild Project`
4. ✅ 运行应用测试 WebSocket 连接

