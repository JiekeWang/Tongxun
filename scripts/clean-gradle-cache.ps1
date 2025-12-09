# Gradle 缓存清理脚本
# 使用方法: 右键点击脚本 -> 使用 PowerShell 运行

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Gradle 缓存清理工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查并停止 Gradle 守护进程
Write-Host "步骤 1: 检查 Gradle 守护进程..." -ForegroundColor Yellow
$gradleProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*gradle*" }
if ($gradleProcesses) {
    Write-Host "发现 Gradle 守护进程，正在停止..." -ForegroundColor Yellow
    try {
        $gradleProcesses | Stop-Process -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
        Write-Host "Gradle 守护进程已停止" -ForegroundColor Green
    } catch {
        Write-Host "无法停止所有 Gradle 进程，请手动关闭 IDE" -ForegroundColor Red
    }
} else {
    Write-Host "未发现运行中的 Gradle 守护进程" -ForegroundColor Green
}

Write-Host ""

# 定义要清理的路径
$gradleHome = "$env:USERPROFILE\.gradle"
$cachesPath = "$gradleHome\caches"
$daemonPath = "$gradleHome\daemon"
$wrapperDistsPath = "$gradleHome\wrapper\dists"

Write-Host "步骤 2: 清理 Gradle 缓存..." -ForegroundColor Yellow
Write-Host ""

# 清理 caches 目录
if (Test-Path $cachesPath) {
    Write-Host "正在清理缓存目录: $cachesPath" -ForegroundColor Yellow
    try {
        # 删除特定版本缓存（8.13、8.11.1、8.9 和 8.5）
        $versionPaths = @("$cachesPath\8.13", "$cachesPath\8.11.1", "$cachesPath\8.9", "$cachesPath\8.5")
        foreach ($versionPath in $versionPaths) {
            if (Test-Path $versionPath) {
                Write-Host "删除版本缓存: $versionPath" -ForegroundColor Yellow
                Remove-Item -Path $versionPath -Recurse -Force -ErrorAction SilentlyContinue
                if (-not (Test-Path $versionPath)) {
                    Write-Host "  ✓ 已删除" -ForegroundColor Green
                } else {
                    Write-Host "  ✗ 删除失败，文件可能被占用" -ForegroundColor Red
                }
            }
        }
        
        Write-Host "缓存清理完成" -ForegroundColor Green
    } catch {
        Write-Host "清理缓存时出错: $_" -ForegroundColor Red
        Write-Host "请确保已关闭 Android Studio/IDE 和所有相关进程" -ForegroundColor Red
    }
} else {
    Write-Host "缓存目录不存在: $cachesPath" -ForegroundColor Yellow
}

Write-Host ""

# 清理 daemon 目录
if (Test-Path $daemonPath) {
    Write-Host "正在清理守护进程目录: $daemonPath" -ForegroundColor Yellow
    try {
        Remove-Item -Path "$daemonPath\*" -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "守护进程目录清理完成" -ForegroundColor Green
    } catch {
        Write-Host "清理守护进程目录时出错: $_" -ForegroundColor Red
    }
} else {
    Write-Host "守护进程目录不存在: $daemonPath" -ForegroundColor Yellow
}

Write-Host ""

# 清理 wrapper dists（可选）
Write-Host "是否清理 Gradle Wrapper 下载缓存? (这将需要重新下载 Gradle)" -ForegroundColor Yellow
$cleanWrapper = Read-Host "输入 y 清理，其他键跳过"
if ($cleanWrapper -eq "y" -or $cleanWrapper -eq "Y") {
    if (Test-Path $wrapperDistsPath) {
        Write-Host "正在清理 Wrapper 下载缓存: $wrapperDistsPath" -ForegroundColor Yellow
        try {
            Remove-Item -Path $wrapperDistsPath -Recurse -Force -ErrorAction SilentlyContinue
            Write-Host "Wrapper 下载缓存清理完成" -ForegroundColor Green
        } catch {
            Write-Host "清理 Wrapper 缓存时出错: $_" -ForegroundColor Red
        }
    } else {
        Write-Host "Wrapper 下载缓存目录不存在: $wrapperDistsPath" -ForegroundColor Yellow
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
Write-Host "3. Gradle 会自动下载新版本并重新构建缓存" -ForegroundColor White
Write-Host ""

