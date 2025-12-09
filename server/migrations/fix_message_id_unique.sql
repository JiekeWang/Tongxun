-- 修复 message_id 唯一约束问题
-- 对于群组消息，同一个 message_id 需要对应多个接收者
-- 解决方案：移除 message_id 的 UNIQUE 约束，改为 (message_id, receiver_id) 联合唯一键

-- 1. 删除旧的唯一索引 message_id（如果存在，可能会失败但不影响）
SET @sqlstmt = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'messages' AND index_name = 'message_id') > 0,
    'ALTER TABLE messages DROP INDEX message_id',
    'SELECT "Index message_id does not exist, skipping" AS info');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 删除联合唯一键 uk_message_receiver（如果已存在）
SET @sqlstmt = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'messages' AND index_name = 'uk_message_receiver') > 0,
    'ALTER TABLE messages DROP INDEX uk_message_receiver',
    'SELECT "Index uk_message_receiver does not exist, skipping" AS info');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 添加联合唯一键
ALTER TABLE messages ADD UNIQUE KEY uk_message_receiver (message_id, receiver_id);

-- 4. 确保 message_id 仍有索引（用于查询性能）- 只在不存在时添加
SET @sqlstmt = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'messages' AND index_name = 'idx_message_id') = 0,
    'ALTER TABLE messages ADD INDEX idx_message_id (message_id)',
    'SELECT "Index idx_message_id already exists, skipping" AS info');
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

