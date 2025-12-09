# 清理 Android 项目构建文件
Write-Host "正在清理项目构建文件..." -ForegroundColor Yellow
Write-Host ""

$directories = @(
    "app\build",
    "build",
    ".gradle"
)

$cleaned = 0
foreach ($dir in $directories) {
    if (Test-Path $dir) {
        try {
            Remove-Item -Path $dir -Recurse -Force -ErrorAction Stop
            Write-Host "✓ 已清理: $dir" -ForegroundColor Green
            $cleaned++
        } catch {
            Write-Host "✗ 清理失败: $dir - $($_.Exception.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "- 不存在: $dir" -ForegroundColor Gray
    }
}

Write-Host ""
if ($cleaned -gt 0) {
    Write-Host "清理完成！已清理 $cleaned 个目录。" -ForegroundColor Green
} else {
    Write-Host "没有需要清理的目录。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "现在可以重新构建项目了。" -ForegroundColor Cyan

