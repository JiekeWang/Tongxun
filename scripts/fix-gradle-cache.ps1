# Gradle 缓存清理脚本
# 用于解决 "Could not move temporary workspace" 错误

Write-Host "正在清理 Gradle 缓存..." -ForegroundColor Yellow

# 停止所有 Gradle 和 Java 进程
Write-Host "`n1. 停止 Gradle 和 Java 进程..." -ForegroundColor Cyan
Get-Process | Where-Object {$_.ProcessName -like "*gradle*" -or $_.ProcessName -like "*java*"} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

# 清理 Gradle daemon PID 文件
Write-Host "2. 清理 Gradle daemon PID 文件..." -ForegroundColor Cyan
if (Test-Path "C:\Users\user\.gradle\daemon") {
    Get-ChildItem "C:\Users\user\.gradle\daemon" -Filter "*.pid" | Remove-Item -Force -ErrorAction SilentlyContinue
}

# 清理所有临时 transform 目录
Write-Host "3. 清理临时 transform 缓存目录..." -ForegroundColor Cyan
$transformsPath = "C:\Users\user\.gradle\caches"
if (Test-Path $transformsPath) {
    # 清理所有版本的临时 transform 目录
    Get-ChildItem -Path $transformsPath -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $versionPath = Join-Path $_.FullName "transforms"
        if (Test-Path $versionPath) {
            $tempDirs = Get-ChildItem -Path $versionPath -Directory -ErrorAction SilentlyContinue | Where-Object { 
                $_.Name -match "-\w{8}-\w{4}-\w{4}-\w{4}-\w{12}$" 
            }
            if ($tempDirs) {
                $tempDirs | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
                Write-Host "   已清理 $($tempDirs.Count) 个临时目录" -ForegroundColor Gray
            }
        }
    }
}

# 清理特定问题的目录（如果存在）
Write-Host "4. 清理已知问题的缓存目录..." -ForegroundColor Cyan
$problemDirs = @(
    "C:\Users\user\.gradle\caches\8.13\transforms\b8fe4115bfdc252d055ae6c67e1d712d-57cc18ef-ee64-4c2e-9b3b-2b9165ce755e",
    "C:\Users\user\.gradle\caches\8.13\transforms\b8fe4115bfdc252d055ae6c67e1d712d"
)

foreach ($dir in $problemDirs) {
    if (Test-Path $dir) {
        Remove-Item -LiteralPath $dir -Recurse -Force -ErrorAction SilentlyContinue
        Write-Host "   已清理: $dir" -ForegroundColor Gray
    }
}

Write-Host "`n清理完成！" -ForegroundColor Green
Write-Host "`n建议：" -ForegroundColor Yellow
Write-Host "1. 将 C:\Users\user\.gradle 目录添加到杀毒软件的排除列表" -ForegroundColor White
Write-Host "2. 关闭可能访问该目录的文件管理器窗口" -ForegroundColor White
Write-Host "3. 重新尝试构建项目" -ForegroundColor White

