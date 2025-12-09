# 通讯App服务器端

基于 Node.js + Express + Socket.io 的即时通讯服务器。

## 功能特性

- ✅ 用户注册/登录（JWT认证）
- ✅ WebSocket实时消息推送
- ✅ 好友管理（搜索、添加、接受/拒绝）
- ✅ 消息撤回（2分钟内）
- ✅ 消息已读/未读状态
- ✅ 离线消息存储
- ✅ Session管理（Redis）
- ✅ 在线状态管理

## 技术栈

- **运行时**: Node.js
- **框架**: Express.js
- **WebSocket**: Socket.io
- **数据库**: MySQL
- **缓存**: Redis
- **认证**: JWT
- **密码加密**: bcryptjs

## 快速开始

### 1. 安装依赖

```bash
npm install
```

### 2. 配置环境变量

复制 `.env.example` 为 `.env` 并修改配置：

```bash
cp .env.example .env
```

主要配置项：
- `PORT`: 服务器端口（默认3000）
- `JWT_SECRET`: JWT密钥（必须修改）
- `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`: 数据库配置
- `REDIS_HOST`, `REDIS_PORT`: Redis配置

### 3. 创建数据库

```sql
CREATE DATABASE tongxun_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

数据库表会在首次启动时自动创建。

### 4. 启动服务

开发模式（自动重启）：
```bash
npm run dev
```

生产模式：
```bash
npm start
```

## API文档

### 认证相关

#### 注册
```
POST /api/auth/register
Body: {
  phoneNumber: string,
  password: string,
  nickname: string
}
```

#### 登录
```
POST /api/auth/login
Body: {
  phoneNumber: string,
  password: string
}
```

#### 登出
```
POST /api/auth/logout
Headers: Authorization: Bearer <token>
```

### 用户相关

#### 获取用户信息
```
GET /api/users/:userId
Headers: Authorization: Bearer <token>
```

#### 搜索用户
```
GET /api/users/search/user?phone=xxx
或
GET /api/users/search/user?userId=xxx
Headers: Authorization: Bearer <token>
```

### 好友相关

#### 发送好友请求
```
POST /api/friends/request
Body: {
  toUserId: string,
  message?: string
}
Headers: Authorization: Bearer <token>
```

#### 接受好友请求
```
POST /api/friends/accept?requestId=xxx
Headers: Authorization: Bearer <token>
```

#### 拒绝好友请求
```
POST /api/friends/reject?requestId=xxx
Headers: Authorization: Bearer <token>
```

#### 获取好友列表
```
GET /api/friends
Headers: Authorization: Bearer <token>
```

### 消息相关

#### 撤回消息
```
POST /api/messages/:messageId/recall
Headers: Authorization: Bearer <token>
```

#### 标记消息已读
```
POST /api/messages/read
Body: {
  conversationId: string,
  messageIds?: string[]
}
Headers: Authorization: Bearer <token>
```

## WebSocket事件

### 客户端发送

#### 连接
```
连接URL: ws://host:port/ws?token=<jwt_token>
```

#### 心跳
```javascript
socket.emit('ping');
socket.on('pong', (data) => {
  console.log('收到心跳响应', data);
});
```

#### 发送消息
```javascript
socket.emit('message', {
  messageId: string,
  conversationId: string,
  receiverId: string,
  content: string,
  messageType: string,
  timestamp: number,
  extra?: string
});
```

#### 撤回消息
```javascript
socket.emit('recall_message', {
  messageId: string
});
```

### 服务端推送

#### 连接成功
```javascript
socket.on('connected', (data) => {
  console.log('连接成功', data);
});
```

#### 收到消息
```javascript
socket.on('message', (data) => {
  console.log('收到消息', data);
});
```

#### 消息发送确认
```javascript
socket.on('message_sent', (data) => {
  console.log('消息已发送', data);
});
```

#### 消息撤回通知
```javascript
socket.on('message_recalled', (data) => {
  console.log('消息已撤回', data);
});
```

## 数据库结构

主要表：
- `users`: 用户表
- `friends`: 好友关系表
- `friend_requests`: 好友请求表
- `messages`: 消息表
- `conversations`: 会话表
- `message_read_status`: 消息已读状态表

## 部署建议

1. 使用 PM2 管理进程
2. 配置 Nginx 反向代理
3. 使用 HTTPS/WSS
4. 配置 Redis 持久化
5. 定期备份数据库

## 注意事项

1. 生产环境必须修改 `JWT_SECRET`
2. 配置 CORS 允许的域名
3. 限制 WebSocket 连接数
4. 实现消息限流
5. 添加日志监控

