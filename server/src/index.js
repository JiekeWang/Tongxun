require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const authRoutes = require('./routes/auth');
const userRoutes = require('./routes/user');
const friendRoutes = require('./routes/friend');
const messageRoutes = require('./routes/message');
const uploadRoutes = require('./routes/upload');
const groupRoutes = require('./routes/group');
const conversationRoutes = require('./routes/conversation');
const { initDatabase } = require('./config/database');
const { initRedis } = require('./config/redis');
const { setupWebSocket } = require('./websocket/socketHandler');
const logger = require('./utils/logger');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*", // 生产环境应该限制具体域名
    methods: ["GET", "POST"]
  },
  path: process.env.WS_PATH || '/ws',
  // 心跳配置：增加超时时间，防止连接被意外断开
  pingTimeout: 60000, // 60秒：服务器等待客户端响应ping的超时时间
  pingInterval: 25000, // 25秒：服务器发送ping的间隔
  // 允许传输升级（从HTTP长轮询升级到WebSocket）
  transports: ['websocket', 'polling'],
  // 允许跨域
  allowEIO3: true
});

const PORT = process.env.PORT || 3000;

// 中间件
app.use(helmet());
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// 请求日志（详细记录）
app.use((req, res, next) => {
  const requestId = Date.now();
  req.requestId = requestId;
  
  // 记录请求详情
  logger.info(`[${requestId}] ${req.method} ${req.path}`, {
    query: req.query,
    body: req.method === 'POST' || req.method === 'PUT' ? req.body : undefined,
    headers: {
      'content-type': req.headers['content-type'],
      'authorization': req.headers.authorization ? 'Bearer ***' : 'none'
    }
  });
  
  // 记录响应
  const originalJson = res.json;
  res.json = function(data) {
    logger.info(`[${requestId}] 响应`, {
      statusCode: res.statusCode,
      data: typeof data === 'object' ? JSON.stringify(data).substring(0, 200) : data
    });
    return originalJson.call(this, data);
  };
  
  next();
});

// 路由
app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/friends', friendRoutes);
app.use('/api/messages', messageRoutes);
app.use('/api/upload', uploadRoutes);
app.use('/api/groups', groupRoutes);
app.use('/api/conversations', conversationRoutes);

// 静态文件服务（用于访问上传的文件）
app.use('/uploads', express.static('src/uploads'));

// 健康检查
app.get('/api/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// WebSocket设置
setupWebSocket(io);

// 错误处理
app.use((err, req, res, next) => {
  logger.error('Error:', err);
  res.status(err.status || 500).json({
    error: err.message || 'Internal Server Error',
    ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
  });
});

// 初始化数据库和Redis
async function startServer() {
  try {
    await initDatabase();
    // Redis 初始化失败不影响服务启动
    try {
      await initRedis();
    } catch (error) {
      logger.warn('Redis初始化失败，服务将继续使用内存存储:', error.message);
    }
    
    server.listen(PORT, '0.0.0.0', () => {
      logger.info(`服务器运行在端口 ${PORT}`);
      logger.info(`WebSocket路径: ${process.env.WS_PATH || '/ws'}`);
    });
  } catch (error) {
    logger.error('服务器启动失败:', error);
    process.exit(1);
  }
}

startServer();

module.exports = { app, server, io };

