# 清理 Kapt 缓存
Write-Host "正在清理 Kapt 缓存..." -ForegroundColor Yellow
Write-Host ""

$directories = @(
    "app\build\tmp\kapt3",
    "app\build\generated\source\kapt",
    "app\build\generated\source\kaptKotlin",
    "app\build\tmp\kotlin-classes"
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
    Write-Host "Kapt 缓存清理完成！已清理 $cleaned 个目录。" -ForegroundColor Green
} else {
    Write-Host "没有需要清理的 Kapt 缓存目录。" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "现在可以重新构建项目了。" -ForegroundColor Cyan

