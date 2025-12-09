# 上传项目到 GitHub 指南

## 第一步：安装 Git

如果您的系统还没有安装 Git，请按照以下步骤安装：

### Windows 系统：
1. 访问 [Git 官网](https://git-scm.com/download/win)
2. 下载并安装 Git for Windows
3. 安装完成后，重启 PowerShell 或命令提示符

### 验证安装：
打开 PowerShell 或命令提示符，运行：
```powershell
git --version
```
如果显示版本号，说明安装成功。

## 第二步：在 GitHub 上创建仓库

1. 登录 [GitHub](https://github.com)
2. 点击右上角的 "+" 号，选择 "New repository"
3. 填写仓库名称（例如：tongxun）
4. 选择 Public 或 Private
5. **不要**勾选 "Initialize this repository with a README"
6. 点击 "Create repository"

## 第三步：配置 Git 并上传代码

### 重要提示：
GitHub 现在使用 **Personal Access Token (PAT)** 而不是密码进行身份验证。

### 创建 Personal Access Token：
1. 登录 GitHub
2. 点击右上角头像 → Settings
3. 左侧菜单选择 "Developer settings"
4. 选择 "Personal access tokens" → "Tokens (classic)"
5. 点击 "Generate new token (classic)"
6. 填写 Note（例如：Tongxun Project）
7. 选择过期时间
8. 勾选权限：至少需要 `repo` 权限
9. 点击 "Generate token"
10. **复制生成的 token**（只显示一次，请妥善保存）

### 在项目目录中执行以下命令：

```powershell
# 1. 进入项目目录
cd D:\Tongxun

# 2. 初始化 Git 仓库
git init

# 3. 配置用户信息（使用您的 GitHub 用户名和邮箱）
git config user.name "您的GitHub用户名"
git config user.email "1043580366@qq.com"

# 4. 添加所有文件
git add .

# 5. 提交代码
git commit -m "Initial commit: 通讯应用项目"

# 6. 添加远程仓库（将 YOUR_USERNAME 和 YOUR_REPO_NAME 替换为您的实际值）
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git

# 7. 推送到 GitHub（使用 Personal Access Token 作为密码）
git push -u origin main
```

**注意**：如果您的默认分支是 `master` 而不是 `main`，请使用：
```powershell
git push -u origin master
```

### 推送时的身份验证：
- 用户名：`1043580366@qq.com` 或您的 GitHub 用户名
- 密码：使用刚才创建的 **Personal Access Token**（不是您的 GitHub 密码）

## 第四步：验证上传

1. 访问您的 GitHub 仓库页面
2. 确认所有文件都已上传
3. 检查文件结构是否正确

## 常见问题

### 问题1：提示 "remote origin already exists"
**解决方案**：
```powershell
git remote remove origin
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO_NAME.git
```

### 问题2：推送时提示认证失败
**解决方案**：
- 确认使用的是 Personal Access Token 而不是密码
- 检查 token 是否过期
- 确认 token 有 `repo` 权限

### 问题3：提示 "fatal: not a git repository"
**解决方案**：
```powershell
cd D:\Tongxun
git init
```

### 问题4：想要更新代码
**解决方案**：
```powershell
git add .
git commit -m "更新说明"
git push
```

## 安全建议

1. **不要**将 Personal Access Token 提交到代码仓库
2. **不要**在公共仓库中提交敏感信息（如密码、API密钥等）
3. 定期更新 Personal Access Token
4. 使用 `.gitignore` 文件排除不需要上传的文件

## 后续操作

上传成功后，您可以：
- 在 GitHub 上查看代码
- 使用 GitHub Desktop 进行图形化操作
- 使用 Git 命令行进行版本控制
- 邀请协作者共同开发

