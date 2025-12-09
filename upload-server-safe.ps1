# 安全上传服务器代码脚本（排除敏感文件）
# 使用方法: .\upload-server-safe.ps1 -ServerIP "47.116.197.230"

param(
    [Parameter(Mandatory=$true)]
    [string]$ServerIP,
    
    [Parameter(Mandatory=$false)]
    [string]$ServerPath = "/var/www/tongxun"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "安全上传服务器代码" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查是否在项目根目录
if (-not (Test-Path "server")) {
    Write-Host "错误: 未找到 server 目录！" -ForegroundColor Red
    Write-Host "请确保在项目根目录（E:\Tongxun）执行此脚本" -ForegroundColor Yellow
    exit 1
}

# 创建临时目录
$TempDir = "server_upload_temp"
if (Test-Path $TempDir) {
    Remove-Item -Recurse -Force $TempDir
}
New-Item -ItemType Directory -Path $TempDir | Out-Null

Write-Host "正在准备上传文件（排除敏感文件）..." -ForegroundColor Green

# 复制 server 目录，排除敏感文件和目录
$excludePatterns = @(".env", "node_modules", "logs", "*.log", ".DS_Store", ".git", ".gitignore")
$serverPath = (Resolve-Path "server").Path

Get-ChildItem -Path "server" -Recurse | Where-Object {
    $relativePath = $_.FullName.Replace("$serverPath\", "")
    $shouldExclude = $false
    foreach ($pattern in $excludePatterns) {
        if ($relativePath -like "*\$pattern" -or $relativePath -like "$pattern*" -or $relativePath -eq $pattern) {
            $shouldExclude = $true
            break
        }
    }
    -not $shouldExclude
} | ForEach-Object {
    $relativePath = $_.FullName.Replace("$serverPath\", "")
    $targetPath = Join-Path $TempDir $relativePath
    
    if ($_.PSIsContainer) {
        if (-not (Test-Path $targetPath)) {
            New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
        }
    } else {
        $targetDir = Split-Path $targetPath -Parent
        if (-not (Test-Path $targetDir)) {
            New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
        }
        Copy-Item $_.FullName -Destination $targetPath -Force
    }
}

Write-Host "文件准备完成" -ForegroundColor Green
Write-Host ""

# 显示将要上传的文件大小
$totalSize = (Get-ChildItem -Path $TempDir -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "上传文件大小: $([math]::Round($totalSize, 2)) MB" -ForegroundColor Cyan
Write-Host ""

# 确认上传
$confirm = Read-Host "确认上传到服务器 $ServerIP ? (y/n)"
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "已取消上传" -ForegroundColor Yellow
    Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
    exit 0
}

Write-Host ""
Write-Host "开始上传..." -ForegroundColor Green
Write-Host "目标: root@${ServerIP}:${ServerPath}/server/" -ForegroundColor Gray
Write-Host ""

try {
    # 直接上传目录内容
    scp -r "${TempDir}/*" "root@${ServerIP}:${ServerPath}/server/"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "上传完成" -ForegroundColor Green
        
        # 清理本地临时文件
        Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
        
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "上传完成！请在服务器上执行以下命令：" -ForegroundColor Green
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host ""
        Write-Host "ssh root@$ServerIP" -ForegroundColor Yellow
        Write-Host "cd $ServerPath/server" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "# 检查 .env 文件" -ForegroundColor Gray
        Write-Host "cat .env | grep DB_USER" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "# 如果 .env 被覆盖，恢复它" -ForegroundColor Gray
        Write-Host "chmod +x restore-env.sh && ./restore-env.sh" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "# 安装依赖" -ForegroundColor Gray
        Write-Host "npm install" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "# 重启服务" -ForegroundColor Gray
        Write-Host "pm2 restart tongxun-server" -ForegroundColor Yellow
        Write-Host ""
    } else {
        throw "上传失败，退出代码: $LASTEXITCODE"
    }
} catch {
    Write-Host "错误: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "如果上传失败，请检查：" -ForegroundColor Yellow
    Write-Host "1. 服务器IP是否正确" -ForegroundColor Yellow
    Write-Host "2. SSH连接是否正常" -ForegroundColor Yellow
    Write-Host "3. 是否安装了 scp 命令" -ForegroundColor Yellow
    Write-Host ""
    
    # 清理本地临时文件
    if (Test-Path $TempDir) {
        Remove-Item -Recurse -Force $TempDir -ErrorAction SilentlyContinue
    }
    
    exit 1
}

Write-Host ""
Write-Host "注意: .env 文件不会被上传，确保服务器上的 .env 文件配置正确！" -ForegroundColor Yellow
Write-Host ""
