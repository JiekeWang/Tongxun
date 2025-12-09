# 修复 PowerShell 执行策略问题

## 问题说明

Windows PowerShell 默认不允许运行脚本，这是安全机制。

## 解决方案

### 方案1：临时允许（推荐，最安全）

**在当前 PowerShell 窗口中执行：**

```powershell
# 仅对当前会话临时允许
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
```

然后运行脚本：
```powershell
.\upload-server-safe.ps1 -ServerIP "47.116.197.230"
```

**优点：** 只影响当前窗口，关闭后恢复原设置  
**缺点：** 每次新窗口都需要执行

---

### 方案2：仅当前用户允许（推荐）

**在 PowerShell（不需要管理员）中执行：**

```powershell
# 仅对当前用户允许本地脚本运行
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**说明：**
- `RemoteSigned`：本地脚本可以运行，远程脚本需要签名
- `CurrentUser`：只影响当前用户，不需要管理员权限

然后运行脚本：
```powershell
.\upload-server-safe.ps1 -ServerIP "47.116.197.230"
```

---

### 方案3：管理员权限全局允许

**在 PowerShell（以管理员身份运行）中执行：**

```powershell
# 全局允许脚本运行（需要管理员权限）
Set-ExecutionPolicy RemoteSigned
```

**注意：** 需要管理员权限，会影响所有用户

---

### 方案4：使用批处理脚本（无需修改策略）

如果不想修改执行策略，使用批处理版本：

```cmd
upload-server-safe.bat 47.116.197.230
```

批处理脚本（`.bat`）不受 PowerShell 执行策略限制。

---

### 方案5：使用 Git Bash（推荐，跨平台）

在 Git Bash 中执行：

```bash
cd /e/Tongxun

# 使用 rsync（如果已安装）
rsync -avz --exclude='.env' \
          --exclude='node_modules' \
          --exclude='logs' \
          --exclude='*.log' \
          server/ root@47.116.197.230:/var/www/tongxun/server/
```

或者使用 tar：

```bash
cd /e/Tongxun

# 创建排除列表
echo ".env" > exclude.txt
echo "node_modules/" >> exclude.txt
echo "logs/" >> exclude.txt
echo "*.log" >> exclude.txt

# 压缩并上传
tar --exclude-from=exclude.txt -czf server.tar.gz server/
scp server.tar.gz root@47.116.197.230:/tmp/
rm -f exclude.txt server.tar.gz

# 在服务器上解压
ssh root@47.116.197.230 "cd /var/www/tongxun/server && tar -xzf /tmp/server.tar.gz && rm -f /tmp/server.tar.gz"
```

---

## 查看当前执行策略

```powershell
Get-ExecutionPolicy -List
```

输出示例：
```
        Scope ExecutionPolicy
        ----- ---------------
MachinePolicy       Undefined
   UserPolicy       Undefined
      Process       Undefined
  CurrentUser       RemoteSigned  ← 当前用户的策略
 LocalMachine       Undefined
```

---

## 推荐的设置

**对于个人开发：**
```powershell
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**优点：**
- ✅ 不需要管理员权限
- ✅ 只影响当前用户
- ✅ 允许本地脚本，阻止未签名的远程脚本
- ✅ 安全性好

---

## 快速设置脚本

创建一个 `setup-powershell.ps1` 文件：

```powershell
# 设置执行策略为 RemoteSigned（仅当前用户）
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser -Force

Write-Host "✅ PowerShell 执行策略已设置" -ForegroundColor Green
Write-Host "现在可以运行上传脚本了：" -ForegroundColor Cyan
Write-Host "  .\upload-server-safe.ps1 -ServerIP `"47.116.197.230`"" -ForegroundColor Yellow
```

运行设置脚本：
```powershell
# 即使执行策略限制，也可以用这种方式运行
powershell -ExecutionPolicy Bypass -File setup-powershell.ps1
```

---

## 常见问题

### Q: 为什么需要修改执行策略？
A: Windows 默认阻止运行脚本以防止恶意脚本执行，这是安全机制。

### Q: 修改执行策略安全吗？
A: 使用 `RemoteSigned -Scope CurrentUser` 是安全的，只允许本地脚本和签名的远程脚本。

### Q: 可以只临时允许吗？
A: 可以，使用 `-Scope Process` 参数，只影响当前 PowerShell 窗口。

### Q: 不想修改策略怎么办？
A: 使用批处理脚本（`.bat`）或 Git Bash，它们不受 PowerShell 执行策略限制。

