const jwt = require('jsonwebtoken');
const { getSession } = require('../config/redis');
const logger = require('../utils/logger');

async function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1]; // Bearer TOKEN

  if (!token) {
    return res.status(401).json({ error: '未提供认证令牌' });
  }

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    
    // 验证Session是否存在（可选，用于支持登出功能）
    const sessionToken = await getSession(decoded.userId);
    if (!sessionToken || sessionToken !== token) {
      return res.status(401).json({ error: '令牌已失效' });
    }

    req.user = decoded;
    next();
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({ error: '令牌已过期' });
    }
    logger.error('Token验证失败:', error);
    return res.status(403).json({ error: '无效的令牌' });
  }
}

module.exports = { authenticateToken };

