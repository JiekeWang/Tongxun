# 清理 Android 构建缓存脚本
Write-Host "开始清理 Android 构建缓存..." -ForegroundColor Yellow
Write-Host ""

# 需要清理的目录
$dirs = @(
    "app\build",
    "app\build\tmp\kapt3",
    "app\build\generated\source\kapt",
    "app\build\generated\source\kaptKotlin",
    ".gradle\caches",
    "build"
)

$cleanedCount = 0
foreach ($dir in $dirs) {
    if (Test-Path $dir) {
        try {
            Remove-Item -Path $dir -Recurse -Force -ErrorAction SilentlyContinue
            Write-Host "✓ 已清理: $dir" -ForegroundColor Green
            $cleanedCount++
        } catch {
            Write-Host "✗ 清理失败: $dir - $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "- 不存在: $dir" -ForegroundColor Gray
    }
}

Write-Host ""
if ($cleanedCount -gt 0) {
    Write-Host "构建缓存清理完成！已清理 $cleanedCount 个目录。" -ForegroundColor Green
} else {
    Write-Host "没有需要清理的缓存目录。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "请在 Android Studio 中执行以下操作：" -ForegroundColor Cyan
Write-Host "1. File -> Invalidate Caches / Restart -> Invalidate and Restart" -ForegroundColor Cyan
Write-Host "2. File -> Sync Project with Gradle Files" -ForegroundColor Cyan
Write-Host "3. 重新构建项目 (Build -> Rebuild Project)" -ForegroundColor Cyan

