@echo off
echo 正在清理项目构建文件...
echo.

if exist "app\build" (
    rmdir /S /Q "app\build"
    echo [OK] 已清理 app\build
) else (
    echo [-] app\build 不存在
)

if exist "build" (
    rmdir /S /Q "build"
    echo [OK] 已清理 build
) else (
    echo [-] build 不存在
)

if exist ".gradle" (
    rmdir /S /Q ".gradle"
    echo [OK] 已清理 .gradle
) else (
    echo [-] .gradle 不存在
)

echo.
echo 清理完成！
echo.
pause

