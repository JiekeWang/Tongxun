# 生成 APK 的几种方法

## 方法 1：使用 Gradle 面板（最简单）

1. 在 Android Studio 右侧找到 **Gradle** 面板（如果没有，点击 View → Tool Windows → Gradle）
2. 展开项目结构：
   ```
   Tongxun
   └── app
       └── Tasks
           └── build
               ├── assembleDebug    ← 双击这个生成 Debug APK
               └── assembleRelease  ← 双击这个生成 Release APK
   ```
3. 双击 `assembleDebug`，等待构建完成

## 方法 2：使用 Android Studio 菜单

### 方式 A：
- **Build → Make Project** (Ctrl+F9)
- 然后 **Build → Rebuild Project**

### 方式 B：
- **Build → Build Project** (Ctrl+F9)

## 方法 3：使用 Terminal（在 Android Studio 中）

1. 点击底部 **Terminal** 标签
2. 运行以下命令：

```bash
# Windows
gradlew.bat assembleDebug

# 或者如果没有 gradlew.bat，使用完整路径
.\gradlew assembleDebug
```

## 方法 4：使用命令行（在项目根目录）

打开 PowerShell 或 CMD，进入项目目录，运行：

```powershell
# 如果项目有 gradlew.bat
.\gradlew.bat assembleDebug

# 或者使用全局 gradle（如果已安装）
gradle assembleDebug
```

## APK 生成后的位置

- **Debug APK**: `app\build\outputs\apk\debug\app-debug.apk`
- **Release APK**: `app\build\outputs\apk\release\app-release.apk`

## 安装到手机

1. **使用脚本**（推荐）：
   ```powershell
   .\install-apk.ps1
   ```

2. **手动安装**：
   - 将 APK 文件复制到手机
   - 在手机上打开文件管理器，找到 APK 文件
   - 点击安装（需要允许"安装未知来源应用"）

3. **使用 ADB**：
   ```powershell
   adb install app\build\outputs\apk\debug\app-debug.apk
   ```

## 注意事项

- 如果使用真机测试，需要修改 `app/src/main/java/com/tongxun/data/remote/NetworkModule.kt` 中的 `BASE_URL`
- 将 `10.0.2.2` 改为你电脑的局域网 IP 地址（如 `http://192.168.1.100:3000/api/`）

