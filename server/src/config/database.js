const mysql = require('mysql2/promise');
const logger = require('../utils/logger');

let pool = null;

async function initDatabase() {
  try {
    pool = mysql.createPool({
      host: process.env.DB_HOST || 'localhost',
      port: process.env.DB_PORT || 3306,
      user: process.env.DB_USER || 'wangjia',
      password: process.env.DB_PASSWORD,
      database: process.env.DB_NAME || 'tongxun_db',
      waitForConnections: true,
      connectionLimit: 10,
      queueLimit: 0
    });

    // 测试连接
    const connection = await pool.getConnection();
    logger.info('数据库连接成功');
    connection.release();

    // 创建表（如果不存在）
    await createTables();
  } catch (error) {
    logger.error('数据库连接失败:', error);
    throw error;
  }
}

async function createTables() {
  const connection = await pool.getConnection();
  try {
    // 用户表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS users (
        user_id VARCHAR(36) PRIMARY KEY,
        phone_number VARCHAR(20) UNIQUE NOT NULL,
        nickname VARCHAR(50) NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        avatar VARCHAR(255),
        signature VARCHAR(200),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_phone (phone_number)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 好友关系表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS friends (
        id INT AUTO_INCREMENT PRIMARY KEY,
        user_id VARCHAR(36) NOT NULL,
        friend_id VARCHAR(36) NOT NULL,
        remark VARCHAR(50),
        group_name VARCHAR(50),
        is_blocked BOOLEAN DEFAULT FALSE,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uk_user_friend (user_id, friend_id),
        INDEX idx_user (user_id),
        INDEX idx_friend (friend_id),
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
        FOREIGN KEY (friend_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 好友请求表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS friend_requests (
        request_id VARCHAR(36) PRIMARY KEY,
        from_user_id VARCHAR(36) NOT NULL,
        to_user_id VARCHAR(36) NOT NULL,
        message VARCHAR(200),
        status ENUM('PENDING', 'ACCEPTED', 'REJECTED') DEFAULT 'PENDING',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_from (from_user_id),
        INDEX idx_to (to_user_id),
        INDEX idx_status (status),
        FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
        FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 消息表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS messages (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        message_id VARCHAR(36) NOT NULL,
        conversation_id VARCHAR(100) NOT NULL,
        sender_id VARCHAR(36) NOT NULL,
        receiver_id VARCHAR(36) NOT NULL,
        content TEXT NOT NULL,
        message_type VARCHAR(20) NOT NULL,
        timestamp BIGINT NOT NULL,
        status VARCHAR(20) DEFAULT 'SENT',
        is_recalled BOOLEAN DEFAULT FALSE,
        recall_by VARCHAR(36),
        recall_time BIGINT,
        extra TEXT,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        UNIQUE KEY uk_message_receiver (message_id, receiver_id),
        INDEX idx_conversation (conversation_id),
        INDEX idx_message_id (message_id),
        INDEX idx_timestamp (timestamp),
        INDEX idx_sender (sender_id),
        INDEX idx_receiver (receiver_id),
        FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
        FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 会话表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS conversations (
        conversation_id VARCHAR(100) PRIMARY KEY,
        type ENUM('SINGLE', 'GROUP') NOT NULL,
        target_id VARCHAR(36) NOT NULL,
        target_name VARCHAR(100),
        target_avatar VARCHAR(255),
        last_message TEXT,
        last_message_time BIGINT,
        unread_count INT DEFAULT 0,
        is_top BOOLEAN DEFAULT FALSE,
        is_muted BOOLEAN DEFAULT FALSE,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
        INDEX idx_last_time (last_message_time)
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 已读状态表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS message_read_status (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        message_id VARCHAR(36) NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        read_at BIGINT NOT NULL,
        UNIQUE KEY uk_message_user (message_id, user_id),
        INDEX idx_user (user_id),
        FOREIGN KEY (message_id) REFERENCES messages(message_id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 文件表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS files (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        file_id VARCHAR(36) UNIQUE NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        original_name VARCHAR(255) NOT NULL,
        file_name VARCHAR(255) NOT NULL,
        file_path VARCHAR(500) NOT NULL,
        file_url VARCHAR(500) NOT NULL,
        thumbnail_url VARCHAR(500),
        file_size BIGINT NOT NULL,
        mime_type VARCHAR(100) NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        INDEX idx_file_id (file_id),
        INDEX idx_user (user_id),
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 群组表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS \`groups\` (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        group_id VARCHAR(36) UNIQUE NOT NULL,
        group_name VARCHAR(100) NOT NULL,
        description VARCHAR(500),
        avatar VARCHAR(255),
        owner_id VARCHAR(36) NOT NULL,
        member_count INT DEFAULT 0,
        max_member_count INT DEFAULT 500,
        created_at BIGINT NOT NULL,
        updated_at BIGINT NOT NULL,
        INDEX idx_group_id (group_id),
        INDEX idx_owner (owner_id),
        FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 群成员表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS group_members (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        group_id VARCHAR(36) NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        role ENUM('OWNER', 'ADMIN', 'MEMBER') DEFAULT 'MEMBER',
        joined_at BIGINT NOT NULL,
        UNIQUE KEY uk_group_user (group_id, user_id),
        INDEX idx_group (group_id),
        INDEX idx_user (user_id),
        FOREIGN KEY (group_id) REFERENCES \`groups\`(group_id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    // 群组申请表
    await connection.query(`
      CREATE TABLE IF NOT EXISTS group_join_requests (
        id BIGINT AUTO_INCREMENT PRIMARY KEY,
        request_id VARCHAR(36) UNIQUE NOT NULL,
        group_id VARCHAR(36) NOT NULL,
        user_id VARCHAR(36) NOT NULL,
        message VARCHAR(200),
        status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
        created_at BIGINT NOT NULL,
        processed_at BIGINT,
        INDEX idx_group (group_id),
        INDEX idx_user (user_id),
        INDEX idx_status (status),
        FOREIGN KEY (group_id) REFERENCES \`groups\`(group_id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
      ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
    `);

    logger.info('数据库表创建/检查完成');
  } catch (error) {
    logger.error('创建表失败:', error);
    throw error;
  } finally {
    connection.release();
  }
}

function getPool() {
  if (!pool) {
    throw new Error('数据库未初始化');
  }
  return pool;
}

module.exports = {
  initDatabase,
  getPool
};

