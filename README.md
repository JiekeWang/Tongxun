# Tongxun - 通讯应用

一个基于 Android 和 Node.js 的即时通讯应用。

## 项目结构

```
Tongxun/
├── app/                    # Android 应用代码
│   ├── src/main/          # 主要源代码
│   └── build.gradle       # 应用构建配置
├── server/                 # Node.js 后端服务器
│   ├── src/               # 服务器源代码
│   └── package.json       # 服务器依赖配置
├── docs/                   # 项目文档
│   ├── README_PROJECT.md  # 项目详细说明
│   └── *.md               # 其他文档
├── scripts/                # 工具脚本
│   ├── *.ps1              # PowerShell 脚本
│   ├── *.bat              # Windows 批处理脚本
│   └── *.sh               # Shell 脚本
├── build.gradle            # 项目构建配置
├── settings.gradle         # Gradle 设置
└── README.md              # 本文件
```

## 快速开始

### Android 应用
1. 使用 Android Studio 打开项目
2. 同步 Gradle 依赖
3. 运行应用

### 服务器
1. 进入 `server` 目录
2. 运行 `npm install` 安装依赖
3. 配置环境变量（参考 `server/.env.example`）
4. 运行 `npm start` 启动服务器

## 更多信息

详细文档请查看 [docs/](docs/) 目录。

## 许可证

[添加您的许可证信息]

