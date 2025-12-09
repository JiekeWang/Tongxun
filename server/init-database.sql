-- 创建数据库
CREATE DATABASE IF NOT EXISTS tongxun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户（如果不存在）
CREATE USER IF NOT EXISTS 'wangjia'@'localhost' IDENTIFIED BY 'qazQAZ123';

-- 授予权限
GRANT ALL PRIVILEGES ON tongxun.* TO 'wangjia'@'localhost';

-- 刷新权限
FLUSH PRIVILEGES;

-- 显示数据库
SHOW DATABASES LIKE 'tongxun';

-- 显示用户权限
SHOW GRANTS FOR 'wangjia'@'localhost';

