# 上传项目到 GitHub 的自动化脚本
# 使用方法：.\upload-to-github.ps1 -RepositoryUrl "https://github.com/用户名/仓库名.git"

param(
    [Parameter(Mandatory=$true)]
    [string]$RepositoryUrl,
    
    [string]$GitHubEmail = "1043580366@qq.com",
    
    [string]$CommitMessage = "Initial commit: 通讯应用项目"
)

Write-Host "`n=== 上传项目到 GitHub ===" -ForegroundColor Green

# 检查 Git 是否安装
Write-Host "`n[1/7] 检查 Git 是否安装..." -ForegroundColor Yellow
try {
    $gitVersion = git --version
    Write-Host "✅ Git 已安装: $gitVersion" -ForegroundColor Green
} catch {
    Write-Host "❌ Git 未安装或不在 PATH 中" -ForegroundColor Red
    Write-Host "`n请先安装 Git：" -ForegroundColor Yellow
    Write-Host "1. 访问 https://git-scm.com/download/win" -ForegroundColor Cyan
    Write-Host "2. 下载并安装 Git for Windows" -ForegroundColor Cyan
    Write-Host "3. 重启 PowerShell 后再次运行此脚本" -ForegroundColor Cyan
    exit 1
}

# 检查是否已经是 Git 仓库
Write-Host "`n[2/7] 检查 Git 仓库状态..." -ForegroundColor Yellow
if (Test-Path ".git") {
    Write-Host "✅ Git 仓库已存在" -ForegroundColor Green
    $isNewRepo = $false
} else {
    Write-Host "📦 初始化新的 Git 仓库..." -ForegroundColor Cyan
    git init
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Git 仓库初始化成功" -ForegroundColor Green
        $isNewRepo = $true
    } else {
        Write-Host "❌ Git 仓库初始化失败" -ForegroundColor Red
        exit 1
    }
}

# 配置 Git 用户信息
Write-Host "`n[3/7] 配置 Git 用户信息..." -ForegroundColor Yellow
git config user.email $GitHubEmail
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 用户邮箱配置成功: $GitHubEmail" -ForegroundColor Green
} else {
    Write-Host "⚠️ 用户邮箱配置失败，继续执行..." -ForegroundColor Yellow
}

# 检查远程仓库配置
Write-Host "`n[4/7] 检查远程仓库配置..." -ForegroundColor Yellow
$remoteUrl = git remote get-url origin 2>$null
if ($remoteUrl) {
    Write-Host "当前远程仓库: $remoteUrl" -ForegroundColor Cyan
    $updateRemote = Read-Host "是否更新远程仓库地址？(Y/N)"
    if ($updateRemote -eq "Y" -or $updateRemote -eq "y") {
        git remote set-url origin $RepositoryUrl
        Write-Host "✅ 远程仓库地址已更新" -ForegroundColor Green
    }
} else {
    Write-Host "添加远程仓库: $RepositoryUrl" -ForegroundColor Cyan
    git remote add origin $RepositoryUrl
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ 远程仓库添加成功" -ForegroundColor Green
    } else {
        Write-Host "❌ 远程仓库添加失败" -ForegroundColor Red
        exit 1
    }
}

# 添加文件
Write-Host "`n[5/7] 添加文件到暂存区..." -ForegroundColor Yellow
git add .
if ($LASTEXITCODE -eq 0) {
    $fileCount = (git status --short | Measure-Object -Line).Lines
    Write-Host "✅ 已添加 $fileCount 个文件到暂存区" -ForegroundColor Green
} else {
    Write-Host "❌ 添加文件失败" -ForegroundColor Red
    exit 1
}

# 提交代码
Write-Host "`n[6/7] 提交代码..." -ForegroundColor Yellow
git commit -m $CommitMessage
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ 代码提交成功" -ForegroundColor Green
} else {
    Write-Host "⚠️ 代码提交失败或没有更改" -ForegroundColor Yellow
}

# 检查当前分支
$currentBranch = git branch --show-current
if (-not $currentBranch) {
    # 如果没有分支，创建 main 分支
    git branch -M main
    $currentBranch = "main"
}

# 推送到 GitHub
Write-Host "`n[7/7] 推送到 GitHub..." -ForegroundColor Yellow
Write-Host "⚠️ 重要提示：" -ForegroundColor Yellow
Write-Host "   GitHub 现在使用 Personal Access Token (PAT) 而不是密码" -ForegroundColor Yellow
Write-Host "   用户名: $GitHubEmail 或您的 GitHub 用户名" -ForegroundColor Cyan
Write-Host "   密码: 使用您的 Personal Access Token" -ForegroundColor Cyan
Write-Host "`n如果还没有创建 Token，请访问：" -ForegroundColor Yellow
Write-Host "   https://github.com/settings/tokens" -ForegroundColor Cyan
Write-Host "`n按 Enter 继续推送，或按 Ctrl+C 取消..." -ForegroundColor Yellow
Read-Host

Write-Host "正在推送到 $currentBranch 分支..." -ForegroundColor Cyan
git push -u origin $currentBranch

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅✅✅ 代码已成功推送到 GitHub！" -ForegroundColor Green
    Write-Host "仓库地址: $RepositoryUrl" -ForegroundColor Cyan
} else {
    Write-Host "`n❌ 推送失败" -ForegroundColor Red
    Write-Host "可能的原因：" -ForegroundColor Yellow
    Write-Host "1. 认证失败 - 请确认使用 Personal Access Token" -ForegroundColor Yellow
    Write-Host "2. 远程仓库不存在 - 请先在 GitHub 上创建仓库" -ForegroundColor Yellow
    Write-Host "3. 网络问题 - 请检查网络连接" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n=== 完成 ===" -ForegroundColor Green

