const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { v4: uuidv4 } = require('uuid');
const { authenticateToken } = require('../middleware/auth');
const { getPool } = require('../config/database');
const logger = require('../utils/logger');

const router = express.Router();

// 确保上传目录存在
const uploadDir = path.join(__dirname, '../uploads');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

// 配置multer存储
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    let type = 'files';
    if (file.mimetype.startsWith('image/')) {
      type = 'images';
    } else if (file.mimetype.startsWith('audio/')) {
      type = 'audio';
    }
    const dir = path.join(uploadDir, type);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    cb(null, dir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    const filename = `${uuidv4()}${ext}`;
    cb(null, filename);
  }
});

// 文件过滤器
const fileFilter = (req, file, cb) => {
  // 允许图片、音频和文件
  const allowedExtensions = /\.(jpeg|jpg|png|gif|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|zip|rar|mp3|m4a|aac|wav|ogg|amr)$/i;
  const allowedMimeTypes = /^(image|audio|application|text|video)\//;
  
  const ext = path.extname(file.originalname).toLowerCase();
  const extname = allowedExtensions.test(ext);
  const mimetype = allowedMimeTypes.test(file.mimetype);
  
  // 记录详细信息用于调试
  logger.info(`[文件过滤器] 检查文件: ${file.originalname}, MIME: ${file.mimetype}, 扩展名: ${ext}`);
  logger.info(`[文件过滤器] extname匹配: ${extname}, mimetype匹配: ${mimetype}`);
  
  // 如果扩展名匹配，或者 MIME 类型匹配，都允许（更宽松的策略）
  if (extname || mimetype) {
    logger.info(`[文件过滤器] 文件通过: ${file.originalname}`);
    return cb(null, true);
  } else {
    const errorMsg = `不支持的文件类型: MIME=${file.mimetype}, 扩展名=${ext}`;
    logger.error(`[文件过滤器] ${errorMsg}`);
    cb(new Error(errorMsg));
  }
};

// 配置multer
const upload = multer({
  storage: storage,
  limits: {
    fileSize: 50 * 1024 * 1024 // 50MB
  },
  fileFilter: fileFilter
});

// 分片上传专用的multer配置（更宽松，因为分片文件只是二进制数据块）
const chunkUpload = multer({
  storage: storage,
  limits: {
    fileSize: 50 * 1024 * 1024 // 50MB per chunk
  },
  fileFilter: (req, file, cb) => {
    // 分片文件不需要验证类型，因为原始文件的类型已经在 req.body.mimeType 中
    logger.info(`[分片上传] 接收分片: ${file.originalname}, MIME: ${file.mimetype}`);
    cb(null, true);
  }
});

// 所有路由需要认证
router.use(authenticateToken);

// 上传文件
router.post('/file', upload.single('file'), async (req, res) => {
  try {
    const pool = getPool();
    if (!req.file) {
      return res.status(400).json({ error: '未选择文件' });
    }

    const userId = req.user.userId;
    const fileId = uuidv4();
    const filePath = req.file.path;
    
    // 根据文件类型确定目录
    let uploadType = 'files';
    if (req.file.mimetype.startsWith('image/')) {
      uploadType = 'images';
    } else if (req.file.mimetype.startsWith('audio/')) {
      uploadType = 'audio';
    }
    const fileUrl = `/uploads/${uploadType}/${req.file.filename}`;
    const fileSize = req.file.size;
    const mimeType = req.file.mimetype;
    const originalName = req.file.originalname;

    // 如果是图片，生成缩略图路径
    let thumbnailUrl = null;
    if (mimeType.startsWith('image/')) {
      thumbnailUrl = fileUrl; // 简化处理，实际应该生成缩略图
    }

    // 保存文件信息到数据库
    // 注意：created_at 使用数据库默认值 CURRENT_TIMESTAMP，不需要手动设置
    logger.info(`[上传文件] 准备保存到数据库`, {
      fileId: fileId,
      userId: userId,
      fileUrl: fileUrl,
      fileSize: fileSize,
      mimeType: mimeType
    });
    
    await pool.query(
      `INSERT INTO files 
       (file_id, user_id, original_name, file_name, file_path, file_url, thumbnail_url, file_size, mime_type)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [fileId, userId, originalName, req.file.filename, filePath, fileUrl, thumbnailUrl, fileSize, mimeType]
    );
    
    logger.info(`[上传文件] 文件保存成功`, {
      fileId: fileId,
      fileUrl: fileUrl,
      fileSize: fileSize
    });

    res.json({
      fileId,
      fileUrl,
      thumbnailUrl,
      fileName: originalName,
      fileSize,
      mimeType
    });
  } catch (error) {
    logger.error('文件上传失败:', error);
    if (req.file) {
      // 删除已上传的文件
      fs.unlinkSync(req.file.path);
    }
    res.status(500).json({ error: '文件上传失败' });
  }
});

// 分片上传（大文件）
router.post('/chunk', chunkUpload.single('chunk'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: '未选择文件块' });
    }

    const { fileId, chunkIndex, totalChunks, fileName, fileSize, mimeType } = req.body;
    
    if (!fileId || chunkIndex === undefined || totalChunks === undefined) {
      return res.status(400).json({ error: '缺少必要参数' });
    }

    const chunkDir = path.join(uploadDir, 'chunks', fileId);
    if (!fs.existsSync(chunkDir)) {
      fs.mkdirSync(chunkDir, { recursive: true });
    }

    const chunkPath = path.join(chunkDir, `chunk_${chunkIndex}`);
    fs.renameSync(req.file.path, chunkPath);

    res.json({
      fileId,
      chunkIndex: parseInt(chunkIndex),
      uploaded: true
    });
  } catch (error) {
    logger.error('分片上传失败:', error);
    res.status(500).json({ error: '分片上传失败' });
  }
});

// 合并分片
router.post('/merge', authenticateToken, async (req, res) => {
  try {
    const pool = getPool();
    const { fileId, fileName, fileSize, mimeType } = req.body;
    const userId = req.user.userId;

    if (!fileId || !fileName) {
      return res.status(400).json({ error: '缺少必要参数' });
    }

    const chunkDir = path.join(uploadDir, 'chunks', fileId);
    if (!fs.existsSync(chunkDir)) {
      return res.status(404).json({ error: '文件块不存在' });
    }

    const chunks = fs.readdirSync(chunkDir)
      .filter(f => f.startsWith('chunk_'))
      .sort((a, b) => {
        const aIndex = parseInt(a.split('_')[1]);
        const bIndex = parseInt(b.split('_')[1]);
        return aIndex - bIndex;
      });

    let type = 'files';
    if (mimeType.startsWith('image/')) {
      type = 'images';
    } else if (mimeType.startsWith('audio/')) {
      type = 'audio';
    }
    const finalDir = path.join(uploadDir, type);
    if (!fs.existsSync(finalDir)) {
      fs.mkdirSync(finalDir, { recursive: true });
    }

    const ext = path.extname(fileName);
    const finalFileName = `${uuidv4()}${ext}`;
    const finalPath = path.join(finalDir, finalFileName);
    const fileUrl = `/uploads/${type}/${finalFileName}`;

    // 合并文件块
    const writeStream = fs.createWriteStream(finalPath);
    for (const chunk of chunks) {
      const chunkPath = path.join(chunkDir, chunk);
      const chunkData = fs.readFileSync(chunkPath);
      writeStream.write(chunkData);
      fs.unlinkSync(chunkPath); // 删除已合并的块
    }
    writeStream.end();

    // 删除临时目录
    fs.rmdirSync(chunkDir);

    // 保存文件信息
    // 注意：created_at 使用数据库默认值 CURRENT_TIMESTAMP，不需要手动设置
    await pool.query(
      `INSERT INTO files 
       (file_id, user_id, original_name, file_name, file_path, file_url, file_size, mime_type)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      [fileId, userId, fileName, finalFileName, finalPath, fileUrl, parseInt(fileSize), mimeType]
    );

    res.json({
      fileId,
      fileUrl,
      fileName,
      fileSize: parseInt(fileSize),
      mimeType
    });
  } catch (error) {
    logger.error('合并文件失败:', error);
    res.status(500).json({ error: '合并文件失败' });
  }
});

// 下载文件
router.get('/download/:fileId', async (req, res) => {
  try {
    const pool = getPool();
    const { fileId } = req.params;

    const [files] = await pool.query(
      'SELECT file_path, original_name, mime_type FROM files WHERE file_id = ?',
      [fileId]
    );

    if (files.length === 0) {
      return res.status(404).json({ error: '文件不存在' });
    }

    const file = files[0];
    if (!fs.existsSync(file.file_path)) {
      return res.status(404).json({ error: '文件不存在' });
    }

    res.setHeader('Content-Disposition', `attachment; filename="${encodeURIComponent(file.original_name)}"`);
    res.setHeader('Content-Type', file.mime_type);
    res.sendFile(path.resolve(file.file_path));
  } catch (error) {
    logger.error('文件下载失败:', error);
    res.status(500).json({ error: '文件下载失败' });
  }
});

module.exports = router;

