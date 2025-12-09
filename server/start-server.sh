#!/bin/bash

# 服务器启动脚本

echo "=========================================="
echo "开始启动通讯服务器"
echo "=========================================="

# 进入项目目录
cd /var/www/tongxun/server || exit 1

# 检查Node.js
if ! command -v node &> /dev/null; then
    echo "错误: Node.js 未安装"
    exit 1
fi

echo "Node.js 版本: $(node -v)"
echo "NPM 版本: $(npm -v)"

# 检查PM2
if ! command -v pm2 &> /dev/null; then
    echo "安装 PM2..."
    npm install -g pm2
fi

# 检查.env文件
if [ ! -f .env ]; then
    echo "警告: .env 文件不存在，正在创建..."
    cat > .env << EOF
NODE_ENV=production
PORT=3000

DB_HOST=localhost
DB_PORT=3306
DB_USER=wangjia
DB_PASSWORD=qazQAZ123
DB_NAME=tongxun

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_SECRET=your-very-secure-secret-key-change-this-in-production-min-32-chars
JWT_EXPIRES_IN=7d

WS_PATH=/ws
LOG_LEVEL=info
LOG_DIR=./logs
EOF
    chmod 600 .env
    echo ".env 文件已创建，请检查配置是否正确"
fi

# 安装依赖
if [ ! -d node_modules ]; then
    echo "安装依赖包..."
    npm install --production
fi

# 创建logs目录
mkdir -p logs

# 停止旧服务（如果存在）
pm2 stop tongxun-server 2>/dev/null
pm2 delete tongxun-server 2>/dev/null

# 启动服务
echo "启动服务..."
pm2 start ecosystem.config.js

# 保存PM2配置
pm2 save

# 设置开机自启
pm2 startup

echo "=========================================="
echo "服务启动完成！"
echo "=========================================="
echo ""
echo "查看服务状态: pm2 list"
echo "查看日志: pm2 logs tongxun-server"
echo "重启服务: pm2 restart tongxun-server"
echo "停止服务: pm2 stop tongxun-server"
echo ""

# 显示服务状态
pm2 list

