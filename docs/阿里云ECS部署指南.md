# 阿里云ECS服务器部署指南

## 📋 部署前准备

### 1. 服务器信息确认
- [ ] ECS实例已创建
- [ ] 公网IP已分配
- [ ] 安全组已配置
- [ ] 服务器操作系统（推荐：Ubuntu 20.04/22.04 或 CentOS 7/8）

### 2. 本地准备
- [ ] 项目代码已提交到Git仓库（可选，方便服务器拉取）
- [ ] 数据库备份（如果有现有数据）
- [ ] 服务器SSH密钥或密码

---

## 🚀 部署流程

### 第一步：服务器环境准备

#### 1.1 连接服务器
```bash
# 使用SSH连接服务器
ssh root@your-server-ip
# 或使用密钥
ssh -i your-key.pem root@your-server-ip
```

#### 1.2 更新系统
```bash
# Ubuntu/Debian
apt update && apt upgrade -y

# CentOS/RHEL
yum update -y
```

#### 1.3 安装Node.js（推荐使用nvm）
```bash
# 安装nvm
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash

# 重新加载shell配置
source ~/.bashrc

# 安装Node.js LTS版本
nvm install 18
nvm use 18
nvm alias default 18

# 验证安装
node -v
npm -v
```

#### 1.4 安装MySQL

**Ubuntu/Debian系统：**
```bash
apt install mysql-server -y
systemctl start mysql
systemctl enable mysql
mysql_secure_installation
```

**CentOS/RHEL系统：**
```bash
# CentOS 7
yum install mysql-server -y

# CentOS 8/9 或 Rocky Linux
dnf install mysql-server -y
# 或
yum install mysql-server -y

# 启动MySQL服务（注意：CentOS中服务名是mysqld，不是mysql）
systemctl start mysqld
systemctl enable mysqld

# 查看MySQL初始密码（CentOS 7可能需要）
grep 'temporary password' /var/log/mysqld.log

# 安全配置（设置root密码）
mysql_secure_installation
```

#### 1.5 安装Redis

**Ubuntu/Debian系统：**
```bash
apt install redis-server -y
systemctl start redis
systemctl enable redis
redis-cli ping
```

**CentOS/RHEL系统：**
```bash
# 先安装EPEL仓库（CentOS 7需要）
yum install epel-release -y

# 安装Redis
yum install redis -y
# 或 CentOS 8/9
dnf install redis -y

# 启动Redis服务
systemctl start redis
systemctl enable redis

# 验证Redis
redis-cli ping
```

#### 1.6 安装PM2（进程管理）
```bash
npm install -g pm2
```

#### 1.7 安装Nginx（反向代理，可选）

**Ubuntu/Debian系统：**
```bash
apt install nginx -y
systemctl start nginx
systemctl enable nginx
```

**CentOS/RHEL系统：**
```bash
yum install nginx -y
# 或 CentOS 8/9
dnf install nginx -y

# 启动Nginx
systemctl start nginx
systemctl enable nginx
```

#### 1.8 安装Git（用于克隆代码）

**Ubuntu/Debian系统：**
```bash
apt install git -y
```

**CentOS/RHEL系统：**
```bash
yum install git -y
```

---

### 第二步：上传项目代码

#### 2.1 方式一：使用Git（推荐）
```bash
# 安装Git
apt install git -y  # Ubuntu/Debian
# 或
yum install git -y  # CentOS/RHEL

# 创建项目目录
mkdir -p /var/www/tongxun
cd /var/www/tongxun

# 克隆项目（如果有Git仓库）
git clone your-repo-url server

# 或直接上传server目录
```

#### 2.2 方式二：使用SCP上传
```bash
# 在本地执行
scp -r server root@your-server-ip:/var/www/tongxun/
```

#### 2.3 方式三：使用FTP/SFTP工具
- 使用FileZilla、WinSCP等工具上传server目录到 `/var/www/tongxun/`

---

### 第三步：配置服务器端

#### 3.1 进入项目目录
```bash
cd /var/www/tongxun/server
```

#### 3.2 安装依赖
```bash
npm install
```

#### 3.3 配置环境变量
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑环境变量
nano .env
```

**`.env` 文件配置示例：**
```env
# 服务器配置
NODE_ENV=production
PORT=3000

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=your_mysql_password
DB_NAME=tongxun

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT配置
JWT_SECRET=your-very-secure-secret-key-change-this-in-production
JWT_EXPIRES_IN=7d

# WebSocket配置
WS_PATH=/ws

# 日志配置
LOG_LEVEL=info
LOG_DIR=./logs
```

#### 3.4 创建数据库
```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE tongxun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 创建数据库用户（可选，更安全）
CREATE USER 'tongxun_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON tongxun.* TO 'tongxun_user'@'localhost';
FLUSH PRIVILEGES;

# 退出MySQL
EXIT;
```

#### 3.5 初始化数据库表
```bash
# 项目启动时会自动创建表，或手动运行初始化脚本
npm run init-db
# 或直接启动，会自动创建表
```

---

### 第四步：配置防火墙和安全组

#### 4.1 阿里云安全组配置
1. 登录阿里云控制台
2. 进入ECS实例 → 安全组
3. 添加入站规则：
   - **端口3000**：TCP，允许，来源：0.0.0.0/0（或限制为特定IP）
   - **端口80**：TCP，允许，来源：0.0.0.0/0（如果使用Nginx）
   - **端口443**：TCP，允许，来源：0.0.0.0/0（如果使用HTTPS）
   - **端口22**：TCP，允许，来源：你的IP（SSH访问）

#### 4.2 服务器防火墙配置

**Ubuntu/Debian系统 (UFW)：**
```bash
ufw allow 22/tcp
ufw allow 3000/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw enable
```

**CentOS/RHEL系统 (firewalld)：**
```bash
# 检查防火墙状态
systemctl status firewalld

# 如果防火墙未运行，启动它
systemctl start firewalld
systemctl enable firewalld

# 开放端口
firewall-cmd --permanent --add-port=22/tcp
firewall-cmd --permanent --add-port=3000/tcp
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=443/tcp

# 重载防火墙规则
firewall-cmd --reload

# 查看开放的端口
firewall-cmd --list-ports
```

---

### 第五步：启动服务

#### 5.1 使用PM2启动（推荐）
```bash
cd /var/www/tongxun/server

# 启动服务
pm2 start src/index.js --name tongxun-server

# 或使用ecosystem文件（推荐）
# 创建 ecosystem.config.js
cat > ecosystem.config.js << EOF
module.exports = {
  apps: [{
    name: 'tongxun-server',
    script: 'src/index.js',
    instances: 1,
    exec_mode: 'fork',
    env: {
      NODE_ENV: 'production',
      PORT: 3000
    },
    error_file: './logs/pm2-error.log',
    out_file: './logs/pm2-out.log',
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
    merge_logs: true,
    autorestart: true,
    max_memory_restart: '1G'
  }]
}
EOF

# 使用ecosystem文件启动
pm2 start ecosystem.config.js

# 保存PM2配置
pm2 save

# 设置开机自启
pm2 startup
# 执行输出的命令
```

#### 5.2 验证服务运行
```bash
# 查看PM2进程
pm2 list

# 查看日志
pm2 logs tongxun-server

# 测试API
curl http://localhost:3000/api/health
# 或
curl http://your-server-ip:3000/api/health
```

---

### 第六步：配置Nginx反向代理（可选但推荐）

#### 6.1 创建Nginx配置
```bash
nano /etc/nginx/sites-available/tongxun
```

**配置内容：**
```nginx
server {
    listen 80;
    server_name your-domain.com;  # 替换为你的域名或IP

    # 日志
    access_log /var/log/nginx/tongxun-access.log;
    error_log /var/log/nginx/tongxun-error.log;

    # API代理
    location /api/ {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_cache_bypass $http_upgrade;
    }

    # WebSocket代理
    location /ws {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;
    }

    # 健康检查
    location /health {
        proxy_pass http://localhost:3000/health;
    }
}
```

#### 6.2 启用配置
```bash
# 创建软链接
ln -s /etc/nginx/sites-available/tongxun /etc/nginx/sites-enabled/

# 测试配置
nginx -t

# 重载Nginx
systemctl reload nginx
```

---

### 第七步：配置SSL证书（HTTPS，可选但推荐）

#### 7.1 使用Let's Encrypt免费证书
```bash
# 安装Certbot
apt install certbot python3-certbot-nginx -y  # Ubuntu/Debian
# 或
yum install certbot python3-certbot-nginx -y  # CentOS/RHEL

# 获取证书（需要域名已解析到服务器IP）
certbot --nginx -d your-domain.com

# 自动续期
certbot renew --dry-run
```

---

### 第八步：修改Android客户端配置

#### 8.1 修改BASE_URL
**文件**: `app/src/main/java/com/tongxun/data/remote/NetworkModule.kt`

```kotlin
// 方式一：直接使用IP（不推荐，仅测试用）
const val BASE_URL = "http://your-server-ip:3000/api/"

// 方式二：使用域名（推荐）
const val BASE_URL = "https://your-domain.com/api/"

// 方式三：使用Nginx代理（如果配置了Nginx）
const val BASE_URL = "http://your-domain.com/api/"
```

#### 8.2 重新编译APK
```bash
# 在Android Studio中
# Build → Generate Signed Bundle / APK
# 或
./gradlew assembleRelease
```

---

### 第九步：监控和维护

#### 9.1 PM2常用命令
```bash
# 查看状态
pm2 status

# 查看日志
pm2 logs tongxun-server

# 重启服务
pm2 restart tongxun-server

# 停止服务
pm2 stop tongxun-server

# 删除服务
pm2 delete tongxun-server

# 监控
pm2 monit
```

#### 9.2 日志查看
```bash
# PM2日志
pm2 logs tongxun-server

# 应用日志
tail -f /var/www/tongxun/server/logs/combined.log

# Nginx日志
tail -f /var/log/nginx/tongxun-access.log
tail -f /var/log/nginx/tongxun-error.log
```

#### 9.3 数据库备份
```bash
# 创建备份脚本
cat > /root/backup-db.sh << EOF
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/root/backups"
mkdir -p $BACKUP_DIR
mysqldump -u root -p your_password tongxun > $BACKUP_DIR/tongxun_$DATE.sql
# 保留最近7天的备份
find $BACKUP_DIR -name "tongxun_*.sql" -mtime +7 -delete
EOF

chmod +x /root/backup-db.sh

# 设置定时任务（每天凌晨2点备份）
crontab -e
# 添加：0 2 * * * /root/backup-db.sh
```

---

## 🔒 安全建议

1. **修改默认端口**：考虑将3000端口改为其他端口
2. **使用强密码**：数据库、Redis、JWT密钥等
3. **限制SSH访问**：只允许特定IP访问22端口
4. **定期更新**：保持系统和依赖包更新
5. **启用HTTPS**：使用SSL证书加密传输
6. **配置防火墙**：只开放必要端口
7. **日志监控**：定期检查日志，发现异常
8. **数据备份**：定期备份数据库

---

## 📝 部署检查清单

- [ ] Node.js已安装并验证
- [ ] MySQL已安装并创建数据库
- [ ] Redis已安装并运行
- [ ] PM2已安装
- [ ] 项目代码已上传到服务器
- [ ] 依赖已安装（npm install）
- [ ] 环境变量已配置（.env文件）
- [ ] 数据库表已创建
- [ ] 安全组已配置（端口3000、80、443）
- [ ] 防火墙已配置
- [ ] 服务已启动（PM2）
- [ ] 服务可访问（curl测试）
- [ ] Nginx已配置（如果使用）
- [ ] SSL证书已配置（如果使用HTTPS）
- [ ] Android客户端BASE_URL已更新
- [ ] 客户端可正常连接服务器
- [ ] 日志正常记录

---

## 🐛 常见问题

### 1. 服务无法启动
```bash
# 检查端口是否被占用
netstat -tulpn | grep 3000

# 检查日志
pm2 logs tongxun-server
tail -f /var/www/tongxun/server/logs/combined.log
```

### 2. 数据库连接失败
```bash
# 检查MySQL是否运行
systemctl status mysql

# 测试连接
mysql -u root -p -e "SHOW DATABASES;"

# 检查.env配置
cat /var/www/tongxun/server/.env
```

### 3. WebSocket连接失败
- 检查Nginx配置中的WebSocket代理设置
- 检查防火墙和安全组是否开放端口
- 检查服务器端Socket.IO配置

### 4. 客户端无法连接
- 检查BASE_URL是否正确
- 检查服务器IP/域名是否可访问
- 检查安全组是否开放端口
- 检查防火墙规则

---

## 📞 需要帮助？

如果遇到问题，请检查：
1. PM2日志：`pm2 logs tongxun-server`
2. 应用日志：`/var/www/tongxun/server/logs/combined.log`
3. Nginx日志：`/var/log/nginx/tongxun-error.log`
4. 系统日志：`journalctl -u nginx` 或 `dmesg`

