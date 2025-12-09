# 检查 Kapt 详细错误信息
Write-Host "检查 Kapt 编译错误..." -ForegroundColor Yellow
Write-Host ""

# 检查错误文件
$errorDirs = @(
    "app\build\tmp\kapt3\stubs\debug\error",
    "app\build\tmp\kapt3\classes\debug\error",
    "app\build\generated\source\kapt\debug\error"
)

foreach ($dir in $errorDirs) {
    if (Test-Path $dir) {
        Write-Host "检查目录: $dir" -ForegroundColor Cyan
        $files = Get-ChildItem -Path $dir -Recurse -File -ErrorAction SilentlyContinue
        if ($files) {
            foreach ($file in $files) {
                Write-Host "  发现文件: $($file.FullName)" -ForegroundColor Red
                $content = Get-Content -Path $file.FullName -Raw -ErrorAction SilentlyContinue
                if ($content) {
                    Write-Host "  内容预览:" -ForegroundColor Yellow
                    $content -split "`n" | Select-Object -First 30 | ForEach-Object {
                        Write-Host "    $_"
                    }
                }
            }
        } else {
            Write-Host "  目录为空" -ForegroundColor Gray
        }
    } else {
        Write-Host "目录不存在: $dir" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "检查日志文件..." -ForegroundColor Yellow

# 检查最新的构建日志
$logFiles = @(
    "build\reports\problems\problems-report.html",
    "app\build\tmp\kapt3\logs",
    ".gradle\buildOutputCleanup\cache.properties"
)

foreach ($file in $logFiles) {
    if (Test-Path $file) {
        Write-Host "找到日志文件: $file" -ForegroundColor Green
        if ($file -like "*.html") {
            $content = Get-Content -Path $file -Raw -ErrorAction SilentlyContinue
            if ($content) {
                # 提取错误信息
                $errorMatches = [regex]::Matches($content, "(?i)(error|exception|failure|failed)", "Multiline")
                if ($errorMatches.Count -gt 0) {
                    Write-Host "  发现 $($errorMatches.Count) 个可能的错误关键词" -ForegroundColor Red
                }
            }
        }
    }
}

Write-Host ""
Write-Host "建议操作:" -ForegroundColor Cyan
Write-Host "1. 在 Android Studio 中，打开 Build 窗口" -ForegroundColor White
Write-Host "2. 查看完整的错误堆栈，找到以 'Caused by:' 开头的行" -ForegroundColor White
Write-Host "3. 运行: Build -> Clean Project，然后 Build -> Assemble Project" -ForegroundColor White
