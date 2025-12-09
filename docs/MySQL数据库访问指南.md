# MySQL数据库访问指南

## 🔐 命令行访问（最常用）

### 基本登录

```bash
# 使用root用户登录
mysql -u root -p

# 系统会提示输入密码
# Enter password: （输入你设置的MySQL密码）
```

### 登录后常用命令

```sql
-- 查看所有数据库
SHOW DATABASES;

-- 使用某个数据库
USE tongxun;

-- 查看当前数据库的所有表
SHOW TABLES;

-- 查看表结构
DESCRIBE users;
-- 或
SHOW COLUMNS FROM users;

-- 查看表数据
SELECT * FROM users LIMIT 10;

-- 退出MySQL
EXIT;
-- 或
QUIT;
```

### 直接执行SQL命令（不进入交互模式）

```bash
# 执行单个SQL命令
mysql -u root -p -e "SHOW DATABASES;"

# 执行SQL文件
mysql -u root -p < script.sql

# 执行SQL并输出到文件
mysql -u root -p -e "SELECT * FROM users;" > output.txt
```

---

## 🖥️ 图形化工具访问（推荐）

### 方法1：使用Navicat（Windows/Mac）

1. **下载安装Navicat**
   - 访问：https://www.navicat.com/
   - 下载Navicat for MySQL

2. **配置连接**
   - 打开Navicat
   - 点击"连接" → "MySQL"
   - 配置信息：
     ```
     连接名: 阿里云ECS
     主机: your-server-ip（你的服务器公网IP）
     端口: 3306
     用户名: root
     密码: 你的MySQL密码
     ```

3. **注意**：需要配置MySQL允许远程连接（见下方）

### 方法2：使用DBeaver（免费，跨平台）

1. **下载安装DBeaver**
   - 访问：https://dbeaver.io/
   - 下载Community版本（免费）

2. **配置连接**
   - 新建连接 → MySQL
   - 配置信息同上

### 方法3：使用MySQL Workbench（官方工具）

1. **下载安装**
   - 访问：https://dev.mysql.com/downloads/workbench/

2. **配置连接**
   - 新建连接
   - 配置信息同上

### 方法4：使用VS Code插件

1. **安装插件**
   - 在VS Code中搜索并安装 "MySQL" 插件

2. **配置连接**
   - 点击插件图标
   - 添加新连接
   - 输入连接信息

---

## 🌐 配置MySQL允许远程访问

### 默认情况下，MySQL只允许本地访问。要使用图形化工具，需要配置远程访问：

#### 步骤1：修改MySQL配置

```bash
# 编辑MySQL配置文件
nano /etc/my.cnf
# 或
vi /etc/my.cnf
```

**找到 `[mysqld]` 部分，确保有以下配置：**
```ini
[mysqld]
bind-address = 0.0.0.0  # 允许所有IP访问，或指定特定IP
```

**保存后重启MySQL：**
```bash
systemctl restart mysqld
```

#### 步骤2：创建远程访问用户（推荐）

```bash
# 登录MySQL
mysql -u root -p
```

```sql
-- 创建远程访问用户（替换your_password为强密码）
CREATE USER 'tongxun_remote'@'%' IDENTIFIED BY 'your_strong_password';

-- 授权（只授权tongxun数据库）
GRANT ALL PRIVILEGES ON tongxun.* TO 'tongxun_remote'@'%';

-- 刷新权限
FLUSH PRIVILEGES;

-- 查看用户
SELECT User, Host FROM mysql.user;

-- 退出
EXIT;
```

#### 步骤3：配置防火墙

```bash
# 开放3306端口
firewall-cmd --permanent --add-port=3306/tcp
firewall-cmd --reload

# 验证端口开放
firewall-cmd --list-ports | grep 3306
```

#### 步骤4：配置阿里云安全组

1. 登录阿里云控制台
2. 进入ECS → 安全组
3. 添加入站规则：
   - 端口：3306
   - 协议：TCP
   - 授权对象：你的IP地址（或0.0.0.0/0，不推荐）

---

## 🔒 安全建议

### ⚠️ 重要：远程访问安全配置

1. **不要使用root用户远程访问**
   - 创建专用用户
   - 只授权必要的数据库

2. **限制访问IP**
   ```sql
   -- 只允许特定IP访问
   CREATE USER 'tongxun_remote'@'your-ip-address' IDENTIFIED BY 'password';
   GRANT ALL PRIVILEGES ON tongxun.* TO 'tongxun_remote'@'your-ip-address';
   FLUSH PRIVILEGES;
   ```

3. **使用强密码**
   - 至少16位
   - 包含大小写字母、数字、特殊字符

4. **定期更改密码**
   ```sql
   ALTER USER 'tongxun_remote'@'%' IDENTIFIED BY 'new_strong_password';
   FLUSH PRIVILEGES;
   ```

5. **考虑使用SSH隧道**（更安全）
   - 不直接暴露3306端口
   - 通过SSH隧道连接

---

## 🔗 使用SSH隧道连接（最安全）

### 不开放3306端口，通过SSH隧道访问

#### Windows使用PuTTY

1. **下载PuTTY**
   - https://www.putty.org/

2. **配置SSH隧道**
   - 打开PuTTY
   - Connection → SSH → Tunnels
   - 添加端口转发：
     ```
     Source port: 3307（本地端口）
     Destination: localhost:3306（服务器MySQL端口）
     ```
   - 选择 "Local" 和 "Auto"
   - 点击 "Add"

3. **连接服务器**
   - Session → 输入服务器IP
   - 保存会话并连接

4. **在Navicat/DBeaver中配置**
   - 主机：localhost 或 127.0.0.1
   - 端口：3307（SSH隧道本地端口）
   - 用户名：root
   - 密码：MySQL密码

#### Windows使用PowerShell（无需额外软件）

```powershell
# 在PowerShell中执行
ssh -L 3307:localhost:3306 root@your-server-ip

# 保持这个窗口打开，然后在数据库工具中连接：
# 主机: localhost
# 端口: 3307
```

#### Mac/Linux使用终端

```bash
# 创建SSH隧道
ssh -L 3307:localhost:3306 root@your-server-ip

# 保持终端打开，在数据库工具中连接：
# 主机: localhost
# 端口: 3307
```

---

## 📊 常用数据库操作

### 查看数据库信息

```sql
-- 查看所有数据库
SHOW DATABASES;

-- 查看当前数据库
SELECT DATABASE();

-- 查看数据库大小
SELECT 
    table_schema AS '数据库',
    ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS '大小(MB)'
FROM information_schema.tables
WHERE table_schema = 'tongxun'
GROUP BY table_schema;
```

### 查看表信息

```sql
-- 使用数据库
USE tongxun;

-- 查看所有表
SHOW TABLES;

-- 查看表结构
DESCRIBE users;
SHOW CREATE TABLE users;

-- 查看表数据量
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM friends;
SELECT COUNT(*) FROM messages;
```

### 数据查询示例

```sql
-- 查看用户列表
SELECT user_id, phone_number, nickname, created_at FROM users LIMIT 10;

-- 查看好友关系
SELECT * FROM friends LIMIT 10;

-- 查看消息
SELECT * FROM messages ORDER BY timestamp DESC LIMIT 10;

-- 查看好友请求
SELECT * FROM friend_requests WHERE status = 'PENDING';
```

### 数据备份

```bash
# 备份整个数据库
mysqldump -u root -p tongxun > tongxun_backup_$(date +%Y%m%d).sql

# 备份特定表
mysqldump -u root -p tongxun users friends > tables_backup.sql

# 恢复数据库
mysql -u root -p tongxun < tongxun_backup_20231204.sql
```

---

## 🛠️ 故障排查

### 无法连接MySQL

```bash
# 1. 检查MySQL是否运行
systemctl status mysqld

# 2. 检查端口是否监听
netstat -tulpn | grep 3306
ss -tulpn | grep 3306

# 3. 检查防火墙
firewall-cmd --list-ports | grep 3306

# 4. 检查MySQL错误日志
tail -f /var/log/mysqld.log
# 或
journalctl -u mysqld -n 50
```

### 忘记密码

```bash
# 1. 停止MySQL
systemctl stop mysqld

# 2. 安全模式启动
mysqld_safe --skip-grant-tables --skip-networking &

# 3. 登录（无需密码）
mysql -u root

# 4. 重置密码
USE mysql;
ALTER USER 'root'@'localhost' IDENTIFIED BY 'NewPassword123!';
FLUSH PRIVILEGES;
EXIT;

# 5. 重启MySQL
pkill mysqld
systemctl start mysqld
```

---

## ✅ 快速访问检查清单

- [ ] MySQL服务运行：`systemctl status mysqld`
- [ ] 可以本地登录：`mysql -u root -p`
- [ ] 数据库已创建：`SHOW DATABASES;` 能看到 `tongxun`
- [ ] 如果需要远程访问：
  - [ ] MySQL配置已修改（bind-address）
  - [ ] 远程用户已创建
  - [ ] 防火墙已开放3306端口
  - [ ] 阿里云安全组已配置
- [ ] 图形化工具可以连接（如果配置了远程访问）

---

## 📝 推荐配置

**最安全的配置：**
1. 不开放3306端口到公网
2. 使用SSH隧道连接
3. 创建专用数据库用户
4. 只授权必要的权限

**快速开发配置：**
1. 开放3306端口（仅限你的IP）
2. 创建远程访问用户
3. 使用图形化工具直接连接

