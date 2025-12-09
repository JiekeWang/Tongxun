# MySQL命令行使用说明

## 🔍 区分两种使用方式

### 方式1：在bash shell中执行（未登录MySQL）

```bash
# 在Linux命令行中（不是mysql>提示符）
mysql -u root -p -e "SHOW DATABASES;"
```

### 方式2：在MySQL命令行中执行（已登录MySQL）

```sql
-- 在MySQL命令行中（看到mysql>提示符）
-- 直接输入SQL命令，不需要mysql -u root -p -e
SHOW DATABASES;
```

---

## ✅ 正确的使用方法

### 当前情况：你已经在MySQL命令行中

看到 `mysql>` 提示符，说明已经登录了MySQL，直接输入SQL命令：

```sql
-- 直接输入，不需要mysql -u root -p -e
SHOW DATABASES;

-- 使用数据库
USE tongxun;

-- 查看表
SHOW TABLES;

-- 查看数据
SELECT * FROM users LIMIT 10;

-- 退出
EXIT;
```

---

## 📝 常用MySQL命令

### 数据库操作

```sql
-- 查看所有数据库
SHOW DATABASES;

-- 创建数据库
CREATE DATABASE tongxun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE tongxun;

-- 查看当前使用的数据库
SELECT DATABASE();

-- 删除数据库（谨慎！）
-- DROP DATABASE tongxun;
```

### 表操作

```sql
-- 查看所有表
SHOW TABLES;

-- 查看表结构
DESCRIBE users;
-- 或
SHOW COLUMNS FROM users;

-- 查看创建表的SQL
SHOW CREATE TABLE users;

-- 查看表数据
SELECT * FROM users LIMIT 10;

-- 查看表记录数
SELECT COUNT(*) FROM users;
```

### 用户和权限

```sql
-- 查看所有用户
SELECT User, Host FROM mysql.user;

-- 创建用户
CREATE USER 'tongxun_user'@'localhost' IDENTIFIED BY 'password';

-- 授权
GRANT ALL PRIVILEGES ON tongxun.* TO 'tongxun_user'@'localhost';

-- 刷新权限
FLUSH PRIVILEGES;

-- 查看用户权限
SHOW GRANTS FOR 'tongxun_user'@'localhost';
```

### 退出MySQL

```sql
-- 退出MySQL（三种方式都可以）
EXIT;
QUIT;
\q
```

---

## 🚀 快速操作示例

### 创建项目数据库

```sql
-- 1. 查看现有数据库
SHOW DATABASES;

-- 2. 创建tongxun数据库
CREATE DATABASE tongxun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3. 使用数据库
USE tongxun;

-- 4. 查看数据库（确认创建成功）
SHOW DATABASES;

-- 5. 退出
EXIT;
```

### 检查数据库状态

```sql
-- 查看所有数据库
SHOW DATABASES;

-- 使用tongxun数据库
USE tongxun;

-- 查看表（项目启动后会自动创建）
SHOW TABLES;

-- 如果表已创建，查看表结构
DESCRIBE users;
DESCRIBE friends;
DESCRIBE messages;
```

---

## ⚠️ 常见错误

### 错误1：在MySQL命令行中使用bash命令

```sql
-- ❌ 错误：在mysql>提示符下
mysql -u root -p -e "SHOW DATABASES;"

-- ✅ 正确：在mysql>提示符下
SHOW DATABASES;
```

### 错误2：忘记分号

```sql
-- ❌ 错误：没有分号
SHOW DATABASES

-- ✅ 正确：有分号
SHOW DATABASES;
```

### 错误3：在bash中使用SQL命令

```bash
# ❌ 错误：在bash中直接输入SQL
SHOW DATABASES;

# ✅ 正确：先登录MySQL
mysql -u root -p
# 然后输入SQL命令
```

---

## 💡 提示

1. **看到 `mysql>` 提示符** = 已经在MySQL中，直接输入SQL命令
2. **看到 `[root@...]#` 或 `$` 提示符** = 在bash shell中，需要先登录MySQL
3. **SQL命令必须以分号 `;` 结尾**
4. **退出MySQL使用 `EXIT;` 或 `QUIT;`**

---

## 📋 当前操作建议

既然你已经在MySQL命令行中，直接执行：

```sql
-- 查看所有数据库
SHOW DATABASES;

-- 创建tongxun数据库（如果还没有）
CREATE DATABASE tongxun CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 查看数据库
SHOW DATABASES;

-- 退出
EXIT;
```

