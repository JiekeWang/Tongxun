#!/bin/bash
# 恢复 .env 文件脚本（如果被覆盖）

ENV_FILE="/var/www/tongxun/server/.env"
ENV_BACKUP="/var/www/tongxun/server/.env.backup"
ENV_EXAMPLE="/var/www/tongxun/server/.env.example"

echo "=========================================="
echo "恢复 .env 文件"
echo "=========================================="

# 检查备份文件
if [ -f "$ENV_BACKUP" ]; then
    echo "找到备份文件，正在恢复..."
    cp "$ENV_BACKUP" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    echo "✅ .env 文件已从备份恢复"
    exit 0
fi

# 如果没有备份，检查示例文件
if [ -f "$ENV_EXAMPLE" ]; then
    echo "未找到备份文件，从 .env.example 创建新文件..."
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    echo ""
    echo "⚠️  请编辑 .env 文件，填入正确的配置："
    echo "   nano $ENV_FILE"
    echo ""
    echo "主要配置项："
    echo "  - DB_USER: 数据库用户名（当前应该是: root）"
    echo "  - DB_PASSWORD: 数据库密码"
    echo "  - DB_NAME: 数据库名称（应该是: tongxun）"
    echo ""
else
    echo "❌ 未找到备份文件和示例文件"
    echo "请手动创建 .env 文件"
    exit 1
fi

