# 上传 friend.js 到服务器

## 方法1：从 Windows 本地执行（推荐）

在项目根目录（`E:\Tongxun`）执行：

```powershell
scp server\src\routes\friend.js root@47.116.197.230:/var/www/tongxun/server/src/routes/friend.js
```

**注意**：Windows 路径使用反斜杠 `\`，不是正斜杠 `/`

## 方法2：如果已经在服务器上

如果你已经 SSH 连接到服务器，可以直接在服务器上编辑文件：

```bash
# 在服务器上执行
cd /var/www/tongxun/server
nano src/routes/friend.js
```

找到这一行（大约在第256行）：
```javascript
createdAt: req.created_at
```

改为：
```javascript
createdAt: req.created_at ? new Date(req.created_at).getTime() : Date.now()
```

同样修改第265行（sent 请求的 createdAt）。

## 方法3：使用 WinSCP 或其他 FTP 工具

1. 连接到服务器：`47.116.197.230`
2. 用户名：`root`
3. 上传文件：
   - 本地文件：`E:\Tongxun\server\src\routes\friend.js`
   - 服务器路径：`/var/www/tongxun/server/src/routes/friend.js`

## 完整操作步骤（方法1）

```powershell
# 1. 确保在项目根目录
cd E:\Tongxun

# 2. 上传文件（注意使用反斜杠）
scp server\src\routes\friend.js root@47.116.197.230:/var/www/tongxun/server/src/routes/friend.js

# 3. SSH 连接到服务器
ssh root@47.116.197.230

# 4. 进入服务器目录
cd /var/www/tongxun/server

# 5. 验证文件已上传
cat src/routes/friend.js | grep -A 2 "createdAt"

# 6. 重启服务
pm2 restart tongxun

# 7. 查看日志
pm2 logs tongxun --lines 20
```

## 验证修改

上传后，在服务器上执行：

```bash
grep -A 1 "createdAt:" /var/www/tongxun/server/src/routes/friend.js
```

应该看到：
```javascript
createdAt: req.created_at ? new Date(req.created_at).getTime() : Date.now()
```

而不是：
```javascript
createdAt: req.created_at
```


