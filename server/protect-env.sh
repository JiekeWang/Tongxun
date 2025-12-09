#!/bin/bash
# 保护服务器上的 .env 文件脚本
# 用于防止 .env 文件被覆盖

ENV_FILE="/var/www/tongxun/server/.env"
ENV_BACKUP="/var/www/tongxun/server/.env.backup"

echo "=========================================="
echo "保护 .env 文件"
echo "=========================================="

# 检查 .env 文件是否存在
if [ -f "$ENV_FILE" ]; then
    echo "✅ 找到 .env 文件"
    
    # 备份 .env 文件
    cp "$ENV_FILE" "$ENV_BACKUP"
    echo "✅ 已备份 .env 文件到 .env.backup"
    
    # 设置只读权限（可选，防止误删除）
    chmod 600 "$ENV_FILE"
    echo "✅ 已设置 .env 文件权限为 600"
else
    echo "❌ 未找到 .env 文件"
    echo "如果这是首次部署，请从 .env.example 创建："
    echo "  cp .env.example .env"
    echo "  nano .env  # 编辑配置"
    exit 1
fi

echo ""
echo "=========================================="
echo "完成！"
echo "=========================================="
echo ""
echo "建议："
echo "1. 定期备份 .env 文件"
echo "2. 上传代码后检查 .env 文件是否存在"
echo "3. 如果 .env 被覆盖，从备份恢复："
echo "   cp .env.backup .env"
echo ""

