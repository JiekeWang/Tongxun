# CentOS系统部署命令（阿里云ECS）

## 🔍 系统检测
```bash
# 查看系统版本
cat /etc/redhat-release
# 或
cat /etc/os-release
```

## 📦 安装软件包（CentOS使用yum）

### 1. 更新系统
```bash
yum update -y
```

### 2. 安装Node.js（使用nvm）
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

### 3. 安装MySQL
```bash
# CentOS 7
yum install mysql-server -y

# CentOS 8/9 或 Rocky Linux
dnf install mysql-server -y
# 或
yum install mysql-server -y

# 启动MySQL服务
systemctl start mysqld
systemctl enable mysqld

# 等待MySQL完全启动
sleep 5

# MySQL 8.0可能没有临时密码，直接尝试无密码登录
# 方法1：尝试无密码登录并设置密码
mysql -u root << EOF
ALTER USER 'root'@'localhost' IDENTIFIED BY 'your_strong_password';
FLUSH PRIVILEGES;
EXIT;
EOF

# 方法2：如果方法1失败，使用安全配置脚本
# mysql_secure_installation

# 方法3：查找日志（MySQL 8.0日志位置可能不同）
# journalctl -u mysqld | grep -i password
# 或
# mysql -u root -e "SHOW VARIABLES LIKE 'log_error';"
```

### 4. 安装Redis
```bash
# CentOS 7
yum install epel-release -y
yum install redis -y

# CentOS 8/9
dnf install redis -y
# 或
yum install redis -y

# 启动Redis服务
systemctl start redis
systemctl enable redis

# 验证Redis
redis-cli ping
# 应该返回: PONG
```

### 5. 安装PM2
```bash
npm install -g pm2
```

### 6. 安装Nginx（可选）
```bash
# CentOS 7
yum install nginx -y

# CentOS 8/9
dnf install nginx -y
# 或
yum install nginx -y

# 启动Nginx
systemctl start nginx
systemctl enable nginx
```

### 7. 安装Git（用于克隆代码）
```bash
yum install git -y
```

---

## 🔥 防火墙配置（CentOS使用firewalld）

```bash
# 检查防火墙状态
systemctl status firewalld

# 如果防火墙未运行，启动它
systemctl start firewalld
systemctl enable firewalld

# 开放端口
firewall-cmd --permanent --add-port=22/tcp    # SSH
firewall-cmd --permanent --add-port=3000/tcp  # API
firewall-cmd --permanent --add-port=80/tcp    # HTTP
firewall-cmd --permanent --add-port=443/tcp   # HTTPS

# 重载防火墙规则
firewall-cmd --reload

# 查看开放的端口
firewall-cmd --list-ports
```

---

## 📝 完整安装脚本（一键执行）

```bash
#!/bin/bash
# CentOS系统一键安装脚本

echo "开始安装环境..."

# 更新系统
yum update -y

# 安装基础工具
yum install -y git wget curl

# 安装Node.js (使用nvm)
echo "安装Node.js..."
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash
source ~/.bashrc
nvm install 18
nvm use 18
nvm alias default 18

# 安装MySQL
echo "安装MySQL..."
yum install -y mysql-server

# 初始化MySQL（如果还没有初始化）
if [ ! -d /var/lib/mysql/mysql ]; then
    mysqld --initialize --user=mysql
fi

# 启动MySQL服务
systemctl start mysqld
systemctl enable mysqld

# 等待MySQL启动
sleep 5

# 查找初始密码（MySQL 8.0）
if [ -f /var/log/mysqld.log ]; then
    TEMP_PASS=$(grep 'temporary password' /var/log/mysqld.log | awk '{print $NF}')
    if [ ! -z "$TEMP_PASS" ]; then
        echo "MySQL临时密码: $TEMP_PASS"
    fi
fi

# 安装Redis
echo "安装Redis..."
yum install -y epel-release
yum install -y redis
systemctl start redis
systemctl enable redis

# 安装PM2
echo "安装PM2..."
npm install -g pm2

# 安装Nginx
echo "安装Nginx..."
yum install -y nginx
systemctl start nginx
systemctl enable nginx

# 配置防火墙
echo "配置防火墙..."
systemctl start firewalld
systemctl enable firewalld
firewall-cmd --permanent --add-port=22/tcp
firewall-cmd --permanent --add-port=3000/tcp
firewall-cmd --permanent --add-port=80/tcp
firewall-cmd --permanent --add-port=443/tcp
firewall-cmd --reload

echo "安装完成！"
echo "Node.js版本: $(node -v)"
echo "npm版本: $(npm -v)"
echo "MySQL状态: $(systemctl is-active mysqld)"
echo "Redis状态: $(systemctl is-active redis)"
echo "Nginx状态: $(systemctl is-active nginx)"
```

---

## 🚀 快速命令参考

### 服务管理
```bash
# MySQL
systemctl start mysqld      # 启动
systemctl stop mysqld       # 停止
systemctl restart mysqld    # 重启
systemctl status mysqld     # 状态
systemctl enable mysqld     # 开机自启

# Redis
systemctl start redis       # 启动
systemctl stop redis        # 停止
systemctl restart redis     # 重启
systemctl status redis      # 状态
systemctl enable redis      # 开机自启

# Nginx
systemctl start nginx       # 启动
systemctl stop nginx        # 停止
systemctl restart nginx    # 重启
systemctl status nginx     # 状态
systemctl enable nginx     # 开机自启
```

### 数据库操作
```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
mysql -u root -p -e "CREATE DATABASE tongxun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 查看数据库
mysql -u root -p -e "SHOW DATABASES;"
```

### 防火墙操作
```bash
# 查看防火墙状态
systemctl status firewalld

# 查看开放的端口
firewall-cmd --list-ports

# 查看所有规则
firewall-cmd --list-all

# 临时开放端口（重启后失效）
firewall-cmd --add-port=3000/tcp

# 永久开放端口
firewall-cmd --permanent --add-port=3000/tcp
firewall-cmd --reload
```

---

## ⚠️ 常见问题

### 1. MySQL启动失败
```bash
# 查看错误日志
tail -f /var/log/mysqld.log

# 检查MySQL配置
cat /etc/my.cnf

# 重新初始化（谨慎使用，会删除数据）
rm -rf /var/lib/mysql/*
mysqld --initialize
```

### 2. Redis连接失败
```bash
# 检查Redis配置
cat /etc/redis.conf

# 测试连接
redis-cli ping

# 查看Redis日志
tail -f /var/log/redis/redis.log
```

### 3. 端口被占用
```bash
# 查看端口占用
netstat -tulpn | grep 3000
# 或
ss -tulpn | grep 3000

# 查看进程
ps aux | grep node
```

### 4. 权限问题
```bash
# 如果遇到权限问题，使用sudo
sudo systemctl start mysqld

# 或切换到root用户
su -
```

---

## 📋 下一步

安装完环境后，继续执行：
1. 上传项目代码到 `/var/www/tongxun/server`
2. 安装依赖：`cd /var/www/tongxun/server && npm install`
3. 配置环境变量：`cp .env.example .env && nano .env`
4. 创建数据库：`mysql -u root -p`
5. 启动服务：`pm2 start ecosystem.config.js`

