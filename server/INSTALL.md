# 安装和部署指南

## 前置要求

- Node.js >= 16.0.0
- MySQL >= 5.7
- Redis >= 6.0

## 安装步骤

### 1. 安装Node.js依赖

```bash
cd server
npm install
```

### 2. 配置MySQL数据库

```sql
-- 创建数据库
CREATE DATABASE tongxun_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 数据库表会在首次启动时自动创建
```

### 3. 配置Redis

确保Redis服务正在运行：

```bash
# 启动Redis（Linux/Mac）
redis-server

# 或使用Docker
docker run -d -p 6379:6379 redis:latest
```

### 4. 配置环境变量

复制 `.env.example` 为 `.env`：

```bash
cp .env.example .env
```

编辑 `.env` 文件，配置以下内容：

```env
# 服务器配置
PORT=3000
NODE_ENV=development

# JWT配置（必须修改为随机字符串）
JWT_SECRET=your-super-secret-jwt-key-change-in-production

# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=your_password
DB_NAME=tongxun_db

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# WebSocket路径
WS_PATH=/ws
```

### 5. 启动服务

开发模式（自动重启）：
```bash
npm run dev
```

生产模式：
```bash
npm start
```

### 6. 验证服务

访问健康检查接口：
```bash
curl http://localhost:3000/health
```

应该返回：
```json
{"status":"ok","timestamp":"2024-01-01T00:00:00.000Z"}
```

## 测试API

### 注册用户

```bash
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "13800138000",
    "password": "123456",
    "nickname": "测试用户"
  }'
```

### 登录

```bash
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "13800138000",
    "password": "123456"
  }'
```

保存返回的token用于后续请求。

## 常见问题

### 1. 数据库连接失败

- 检查MySQL服务是否运行
- 验证数据库配置是否正确
- 确认数据库用户有足够权限

### 2. Redis连接失败

- 检查Redis服务是否运行
- 验证Redis配置是否正确
- 检查防火墙设置

### 3. 端口被占用

修改 `.env` 文件中的 `PORT` 配置。

### 4. JWT验证失败

确保 `.env` 文件中的 `JWT_SECRET` 已正确配置。

## 生产环境部署

### 使用PM2

```bash
# 安装PM2
npm install -g pm2

# 启动应用
pm2 start src/index.js --name tongxun-server

# 查看日志
pm2 logs tongxun-server

# 重启应用
pm2 restart tongxun-server
```

### 使用Nginx反向代理

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }

    location /ws {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }
}
```

## 安全建议

1. **修改JWT_SECRET**：使用强随机字符串
2. **使用HTTPS**：生产环境必须启用HTTPS
3. **限制CORS**：配置允许的域名
4. **设置防火墙**：只开放必要端口
5. **定期备份**：备份数据库和Redis数据

