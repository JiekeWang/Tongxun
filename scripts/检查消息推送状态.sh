#!/bin/bash
# 检查消息推送状态脚本

RECEIVER_ID="406a3cd2-6137-4c1a-bd0b-37008507093d"
MESSAGE_ID="20e7150a-2b39-491a-9e10-e845f71e0128"

echo "========== 1. 检查消息是否在数据库中 =========="
mysql -h localhost -u wangjia -pqazQAZ123 tongxun -e "
SELECT 
    message_id,
    sender_id,
    receiver_id,
    content,
    FROM_UNIXTIME(timestamp/1000) as send_time,
    status
FROM messages
WHERE message_id = '${MESSAGE_ID}';
"

echo ""
echo "========== 2. 检查接收者是否在线（Redis）=========="
redis-cli GET "user:online:${RECEIVER_ID}"

echo ""
echo "========== 3. 检查接收者的Socket连接 =========="
redis-cli SMEMBERS "user:sockets:${RECEIVER_ID}"

echo ""
echo "========== 4. 查看最近的推送日志 =========="
pm2 logs tongxun-server --lines 50 --nostream | grep -E "发送消息给用户.*${RECEIVER_ID}|消息已发送.*${RECEIVER_ID}|用户.*${RECEIVER_ID}.*离线|用户.*${RECEIVER_ID}.*连接WebSocket" | tail -20

echo ""
echo "========== 5. 检查接收者的会话信息 =========="
mysql -h localhost -u wangjia -pqazQAZ123 tongxun -e "
SELECT 
    conversation_id,
    type,
    target_id,
    target_name,
    last_message,
    FROM_UNIXTIME(last_message_time/1000) as last_time,
    unread_count
FROM conversations
WHERE conversation_id LIKE '%${RECEIVER_ID}%'
ORDER BY last_message_time DESC;
"

