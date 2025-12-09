@echo off
REM 安全上传服务器代码脚本（批处理版本）
REM 使用方法: upload-server-safe.bat 47.116.197.230

setlocal enabledelayedexpansion

set "SERVER_IP=%~1"
set "SERVER_PATH=/var/www/tongxun"

if "%SERVER_IP%"=="" (
    echo 错误: 请提供服务器IP地址
    echo 使用方法: upload-server-safe.bat 47.116.197.230
    exit /b 1
)

echo ========================================
echo 安全上传服务器代码
echo ========================================
echo.

REM 检查是否在项目根目录
if not exist "server" (
    echo 错误: 未找到 server 目录！
    echo 请确保在项目根目录（E:\Tongxun）执行此脚本
    exit /b 1
)

REM 创建临时目录
set "TEMP_DIR=server_upload_temp"
if exist "%TEMP_DIR%" (
    rmdir /s /q "%TEMP_DIR%"
)
mkdir "%TEMP_DIR%"

echo 正在准备上传文件（排除敏感文件）...
echo.

REM 复制文件，排除敏感文件和目录
echo 正在复制文件（排除 .env, node_modules, logs 等）...
xcopy /E /I /Y /EXCLUDE:exclude_list.txt "server" "%TEMP_DIR%\server\" >nul 2>&1

REM 创建排除列表文件
(
    echo .env
    echo node_modules\
    echo logs\
    echo *.log
    echo .DS_Store
    echo .git\
    echo server_upload_temp\
    echo exclude_list.txt
) > exclude_list.txt

REM 手动删除应该排除的目录（xcopy排除可能不完全）
if exist "%TEMP_DIR%\server\.env" del /q "%TEMP_DIR%\server\.env"
if exist "%TEMP_DIR%\server\node_modules" rmdir /s /q "%TEMP_DIR%\server\node_modules"
if exist "%TEMP_DIR%\server\logs" rmdir /s /q "%TEMP_DIR%\server\logs"

echo.
echo 准备上传到服务器: %SERVER_IP%
echo.
set /p CONFIRM="确认上传? (y/n): "
if /i not "%CONFIRM%"=="y" (
    echo 已取消上传
    rmdir /s /q "%TEMP_DIR%"
    exit /b 0
)

echo.
echo 开始上传...
echo.

REM 使用 scp 上传（需要预先配置 SSH）
scp -r "%TEMP_DIR%\server\*" "root@%SERVER_IP%:%SERVER_PATH%/server/"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo 上传完成！
    echo ========================================
    echo.
    echo 请在服务器上执行以下命令：
    echo.
    echo ssh root@%SERVER_IP%
    echo cd %SERVER_PATH%/server
    echo.
    echo # 检查 .env 文件
    echo cat .env ^| grep DB_USER
    echo.
    echo # 如果 .env 被覆盖，恢复它
    echo chmod +x restore-env.sh ^&^& ./restore-env.sh
    echo.
    echo # 重启服务
    echo pm2 restart tongxun-server
    echo.
) else (
    echo.
    echo 错误: 上传失败
    echo 请检查：
    echo 1. 服务器IP是否正确
    echo 2. SSH连接是否正常
    echo 3. 是否安装了 scp 命令（Git Bash或OpenSSH）
    echo.
)

REM 清理临时文件
rmdir /s /q "%TEMP_DIR%"
if exist "exclude_list.txt" (
    del /q "exclude_list.txt"
)

echo.
echo 注意: .env 文件不会被上传，确保服务器上的 .env 文件配置正确！
echo.

