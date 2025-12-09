# 强制清理 Gradle 缓存脚本
# 此脚本会停止所有 Java/Gradle 进程并清理缓存

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "强制清理 Gradle 缓存工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 步骤 1: 停止所有 Java 进程
Write-Host "步骤 1: 停止所有 Java/Gradle 相关进程..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue
$studioProcesses = Get-Process -Name "studio64","idea64" -ErrorAction SilentlyContinue

if ($javaProcesses) {
    Write-Host "发现 $($javaProcesses.Count) 个 Java 进程" -ForegroundColor Yellow
    foreach ($proc in $javaProcesses) {
        try {
            Write-Host "  停止进程: $($proc.ProcessName) (PID: $($proc.Id))" -ForegroundColor Gray
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host "  无法停止进程 $($proc.Id): $_" -ForegroundColor Red
        }
    }
    Start-Sleep -Seconds 2
    Write-Host "Java 进程已停止" -ForegroundColor Green
} else {
    Write-Host "未发现 Java 进程" -ForegroundColor Green
}

if ($studioProcesses) {
    Write-Host "发现 $($studioProcesses.Count) 个 IDE 进程" -ForegroundColor Yellow
    foreach ($proc in $studioProcesses) {
        try {
            Write-Host "  停止进程: $($proc.ProcessName) (PID: $($proc.Id))" -ForegroundColor Gray
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        } catch {
            Write-Host "  无法停止进程 $($proc.Id): $_" -ForegroundColor Red
        }
    }
    Start-Sleep -Seconds 2
    Write-Host "IDE 进程已停止" -ForegroundColor Green
} else {
    Write-Host "未发现 IDE 进程" -ForegroundColor Green
}

Write-Host ""

# 步骤 2: 等待文件释放
Write-Host "步骤 2: 等待文件释放..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

Write-Host ""

# 步骤 3: 清理缓存
Write-Host "步骤 3: 清理 Gradle 缓存..." -ForegroundColor Yellow
$gradleHome = "$env:USERPROFILE\.gradle"
$cachesPath = "$gradleHome\caches"

# 清理特定目录
$cleanPaths = @(
    "$cachesPath\8.11.1",
    "$cachesPath\8.13",
    "$cachesPath\8.9",
    "$cachesPath\8.5",
    "$cachesPath\jars-9",
    "$cachesPath\transforms"
)

foreach ($path in $cleanPaths) {
    if (Test-Path $path) {
        Write-Host "正在清理: $path" -ForegroundColor Yellow
        try {
            # 先尝试删除文件
            Get-ChildItem -Path $path -Recurse -File -ErrorAction SilentlyContinue | 
                ForEach-Object {
                    try {
                        Remove-Item $_.FullName -Force -ErrorAction Stop
                    } catch {
                        Write-Host "  无法删除文件: $($_.FullName)" -ForegroundColor Red
                    }
                }
            
            # 再删除目录
            Remove-Item -Path $path -Recurse -Force -ErrorAction SilentlyContinue
            
            if (-not (Test-Path $path)) {
                Write-Host "  ✓ 清理成功" -ForegroundColor Green
            } else {
                Write-Host "  ✗ 部分文件可能仍被占用" -ForegroundColor Red
            }
        } catch {
            Write-Host "  清理失败: $_" -ForegroundColor Red
        }
    } else {
        Write-Host "目录不存在: $path" -ForegroundColor Gray
    }
}

Write-Host ""

# 步骤 4: 清理守护进程
$daemonPath = "$gradleHome\daemon"
if (Test-Path $daemonPath) {
    Write-Host "步骤 4: 清理守护进程目录..." -ForegroundColor Yellow
    try {
        Remove-Item -Path "$daemonPath\*" -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "守护进程目录清理完成" -ForegroundColor Green
    } catch {
        Write-Host "清理守护进程目录时出错: $_" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "清理完成！" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步操作:" -ForegroundColor Yellow
Write-Host "1. 重新打开 Android Studio/IDE" -ForegroundColor White
Write-Host "2. 同步项目 (Sync Project with Gradle Files)" -ForegroundColor White
Write-Host "3. Gradle 会自动下载 8.9 版本并重新构建缓存" -ForegroundColor White
Write-Host ""

