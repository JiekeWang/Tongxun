# 快速修复 GitHub 访问不稳定问题

## 方法一：使用脚本自动修复（推荐）

以**管理员身份**运行 PowerShell，然后执行：

```powershell
cd D:\Tongxun
.\fix-github-access.ps1
```

脚本会自动：
1. 检查并优化 DNS 设置
2. 检查系统时间
3. 测试 GitHub 连接
4. 可选：添加 GitHub 到 hosts 文件
5. 刷新 DNS 缓存

## 方法二：手动设置 DNS

### Windows 10/11 设置步骤：

1. **打开网络设置**
   - 按 `Win + I` 打开设置
   - 点击 "网络和 Internet"
   - 点击 "更改适配器选项"

2. **修改网络适配器**
   - 右键点击当前使用的网络连接（Wi-Fi 或以太网）
   - 选择 "属性"
   - 双击 "Internet 协议版本 4 (TCP/IPv4)"

3. **设置 DNS**
   - 选择 "使用下面的 DNS 服务器地址"
   - 首选 DNS 服务器：`8.8.8.8` 或 `1.1.1.1` 或 `223.5.5.5`
   - 备用 DNS 服务器：`8.8.4.4` 或 `1.0.0.1` 或 `223.6.6.6`
   - 点击 "确定"

### 推荐的 DNS 服务器：

- **Google DNS**（全球稳定）：
  - 首选：`8.8.8.8`
  - 备用：`8.8.4.4`

- **Cloudflare DNS**（速度快）：
  - 首选：`1.1.1.1`
  - 备用：`1.0.0.1`

- **阿里云 DNS**（国内优化）：
  - 首选：`223.5.5.5`
  - 备用：`223.6.6.6`

## 方法三：修改 hosts 文件

1. **以管理员身份打开记事本**
   - 按 `Win + X`，选择 "Windows PowerShell (管理员)"
   - 输入：`notepad C:\Windows\System32\drivers\etc\hosts`

2. **添加以下内容**（在文件末尾）：
```
# GitHub 域名映射
140.82.112.3 github.com
140.82.112.4 api.github.com
185.199.108.153 assets-cdn.github.com
```

3. **保存文件**

4. **刷新 DNS 缓存**：
```powershell
ipconfig /flushdns
```

## 方法四：同步系统时间

系统时间不正确会导致 SSL 证书验证失败。

1. **自动同步时间**：
   - 按 `Win + I` 打开设置
   - 点击 "时间和语言"
   - 点击 "日期和时间"
   - 确保 "自动设置时间" 已开启

2. **手动同步**：
   - 右键点击任务栏时间
   - 选择 "调整日期/时间"
   - 点击 "立即同步"

## 方法五：使用代理或 VPN

如果以上方法都不行，可以考虑：

1. **使用 VPN**（推荐用于国内用户）
2. **配置代理**：
   - 浏览器设置 → 代理设置
   - 或使用系统代理设置

## 方法六：使用 GitHub 镜像站

如果 GitHub 主站无法访问，可以使用镜像站：

- **GitHub 镜像**：https://hub.fastgit.xyz
- **GitHub 加速**：https://github.com.cnpmjs.org

**注意**：镜像站可能不支持所有功能，建议仅用于查看代码。

## 验证修复

修复后，测试连接：

```powershell
# 测试 GitHub 主站
Invoke-WebRequest -Uri "https://github.com" -UseBasicParsing

# 测试 API
Invoke-WebRequest -Uri "https://api.github.com" -UseBasicParsing
```

## 常见问题

### Q: 修改 DNS 后仍然无法访问？
A: 
1. 清除浏览器缓存
2. 重启浏览器
3. 检查防火墙设置
4. 尝试使用其他浏览器

### Q: hosts 文件修改后无效？
A:
1. 确保以管理员身份编辑
2. 检查文件格式（每行一个条目）
3. 刷新 DNS 缓存：`ipconfig /flushdns`
4. 重启浏览器

### Q: 系统时间同步失败？
A:
1. 检查网络连接
2. 手动设置正确时间
3. 重启时间服务：
   ```powershell
   net stop w32time
   net start w32time
   ```

## 完成后的操作

修复完成后：
1. 重启浏览器
2. 访问 https://github.com/new 创建仓库
3. 继续上传代码流程

