const redis = require('redis');
const logger = require('../utils/logger');

let client = null;
let useMemoryStore = false;

// 内存存储（Redis后备方案）
const memoryStore = {
  sessions: new Map(),
  online: new Map(),
  sockets: new Map()
};

async function initRedis() {
  try {
    client = redis.createClient({
      socket: {
        host: process.env.REDIS_HOST || 'localhost',
        port: process.env.REDIS_PORT || 6379,
        reconnectStrategy: (retries) => {
          if (retries > 3) {
            logger.warn('Redis连接失败，使用内存存储');
            useMemoryStore = true;
            return false; // 停止重连
          }
          return Math.min(retries * 50, 1000);
        }
      },
      password: process.env.REDIS_PASSWORD || undefined
    });

    client.on('error', (err) => {
      logger.warn('Redis错误:', err.message);
      if (!useMemoryStore) {
        useMemoryStore = true;
        logger.warn('切换到内存存储模式');
      }
    });

    client.on('connect', () => {
      logger.info('Redis连接成功');
      useMemoryStore = false;
    });

    await client.connect();
  } catch (error) {
    logger.warn('Redis连接失败，使用内存存储:', error.message);
    useMemoryStore = true;
    client = null;
  }
}

function getClient() {
  if (!client && !useMemoryStore) {
    throw new Error('Redis未初始化');
  }
  return client;
}

// Session管理
async function setSession(userId, token, expiresIn = 7 * 24 * 60 * 60) {
  if (useMemoryStore || !client) {
    memoryStore.sessions.set(userId, {
      token,
      expiresAt: Date.now() + expiresIn * 1000
    });
    return;
  }
  const key = `session:${userId}`;
  await client.setEx(key, expiresIn, token);
}

async function getSession(userId) {
  if (useMemoryStore || !client) {
    const session = memoryStore.sessions.get(userId);
    if (session && session.expiresAt > Date.now()) {
      return session.token;
    }
    memoryStore.sessions.delete(userId);
    return null;
  }
  const key = `session:${userId}`;
  return await client.get(key);
}

async function deleteSession(userId) {
  if (useMemoryStore || !client) {
    memoryStore.sessions.delete(userId);
    return;
  }
  const key = `session:${userId}`;
  await client.del(key);
}

// 在线状态管理
async function setUserOnline(userId, socketId) {
  if (useMemoryStore || !client) {
    memoryStore.online.set(userId, {
      socketId,
      expiresAt: Date.now() + 300 * 1000 // 5分钟
    });
    return;
  }
  const key = `online:${userId}`;
  await client.setEx(key, 300, socketId); // 5分钟过期
}

async function getUserOnline(userId) {
  if (useMemoryStore || !client) {
    const online = memoryStore.online.get(userId);
    if (online && online.expiresAt > Date.now()) {
      return online.socketId;
    }
    memoryStore.online.delete(userId);
    return null;
  }
  const key = `online:${userId}`;
  return await client.get(key);
}

async function setUserOffline(userId) {
  if (useMemoryStore || !client) {
    memoryStore.online.delete(userId);
    return;
  }
  const key = `online:${userId}`;
  await client.del(key);
}

// WebSocket连接管理
async function addSocketConnection(userId, socketId) {
  if (useMemoryStore || !client) {
    if (!memoryStore.sockets.has(userId)) {
      memoryStore.sockets.set(userId, new Set());
    }
    memoryStore.sockets.get(userId).add(socketId);
    return;
  }
  const key = `socket:${userId}`;
  await client.sAdd(key, socketId);
}

async function removeSocketConnection(userId, socketId) {
  if (useMemoryStore || !client) {
    const sockets = memoryStore.sockets.get(userId);
    if (sockets) {
      sockets.delete(socketId);
      if (sockets.size === 0) {
        memoryStore.sockets.delete(userId);
      }
    }
    return;
  }
  const key = `socket:${userId}`;
  await client.sRem(key, socketId);
}

async function getUserSockets(userId) {
  if (useMemoryStore || !client) {
    const sockets = memoryStore.sockets.get(userId);
    return sockets ? Array.from(sockets) : [];
  }
  const key = `socket:${userId}`;
  return await client.sMembers(key);
}

module.exports = {
  initRedis,
  getClient,
  setSession,
  getSession,
  deleteSession,
  setUserOnline,
  getUserOnline,
  setUserOffline,
  addSocketConnection,
  removeSocketConnection,
  getUserSockets
};

