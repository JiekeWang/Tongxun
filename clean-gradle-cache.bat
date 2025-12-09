@echo off
echo 正在清理 Gradle 缓存...
echo.

REM 停止 Gradle 和 Java 进程
echo [1/4] 停止 Gradle 和 Java 进程...
taskkill /F /IM java.exe /T >nul 2>&1
taskkill /F /IM gradle.exe /T >nul 2>&1
timeout /t 2 /nobreak >nul

REM 清理特定问题的目录
echo [2/4] 清理有问题的 transform 目录...
if exist "C:\Users\user\.gradle\caches\8.13\transforms\0818a129a9a1efb54b5911737606f399-93d25bed-4440-4148-bd7b-748822e6f2b2" (
    rmdir /S /Q "C:\Users\user\.gradle\caches\8.13\transforms\0818a129a9a1efb54b5911737606f399-93d25bed-4440-4148-bd7b-748822e6f2b2" >nul 2>&1
)
if exist "C:\Users\user\.gradle\caches\8.13\transforms\0818a129a9a1efb54b5911737606f399" (
    rmdir /S /Q "C:\Users\user\.gradle\caches\8.13\transforms\0818a129a9a1efb54b5911737606f399" >nul 2>&1
)

REM 清理所有临时 transform 目录（使用 PowerShell）
echo [3/4] 清理所有临时 transform 目录...
powershell -Command "Get-ChildItem -Path 'C:\Users\user\.gradle\caches\8.13\transforms' -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match '-\w{8}-\w{4}-\w{4}-\w{4}-\w{12}$' } | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue"

REM 清理 daemon PID 文件
echo [4/4] 清理 Gradle daemon PID 文件...
if exist "C:\Users\user\.gradle\daemon\*.pid" (
    del /F /Q "C:\Users\user\.gradle\daemon\*.pid" >nul 2>&1
)

echo.
echo 清理完成！
echo.
echo 建议：
echo 1. 将 C:\Users\user\.gradle 添加到杀毒软件排除列表
echo 2. 重新尝试构建项目
echo.
pause

