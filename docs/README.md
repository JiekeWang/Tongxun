# 通讯 - 类QQ即时通讯App

一个基于Android平台的即时通讯应用，采用MVVM架构和分层设计。

## 📚 文档

所有项目文档位于 [`docs/`](docs/) 文件夹中，包括：
- 📖 [文档索引](docs/README.md) - 完整的文档目录和分类
- 🚀 [部署指南](docs/项目部署详细步骤.md) - 项目部署详细步骤
- 📤 [上传指南](docs/上传服务器安全指南.md) - 安全上传代码指南
- 🗄️ [数据库文档](docs/) - 数据库相关文档
- 🔧 [问题排查](docs/) - 各种问题排查指南

查看完整文档列表：[docs/README.md](docs/README.md)

## 项目架构

### 整体架构 (MVVM + 分层架构)

```
┌─────────────────────────────────────────┐
│        Presentation Layer               │
│  Activity/Fragment + ViewModel + LiveData│
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│           Domain Layer                   │
│    Use Cases + Repository Interface      │
└─────────────────────────────────────────┘
                    │
┌─────────────────────────────────────────┐
│            Data Layer                    │
│ RepositoryImpl + Local/Remote Data       │
└─────────────────────────────────────────┘
```

## 技术栈

- **语言**: Kotlin + Java混合
- **架构**: MVVM + Clean Architecture
- **异步**: Kotlin Coroutines + Flow
- **依赖注入**: Hilt
- **网络**: Retrofit2 + OkHttp3 + WebSocket
- **数据库**: Room + SQLite
- **图片加载**: Glide
- **UI**: Material Design Components

## 核心模块

### 1. 数据层 (Data Layer)

- **本地数据库**: Room数据库，包含用户、好友、消息、会话、群组等表
- **网络通信**: Retrofit + WebSocket实现实时消息推送
- **数据仓库**: Repository模式实现数据源统一管理

### 2. 领域层 (Domain Layer)

- **Repository接口**: 定义数据访问接口
- **业务逻辑**: 封装核心业务规则

### 3. 表现层 (Presentation Layer)

- **ViewModel**: 管理UI相关数据，响应式更新
- **Activity/Fragment**: UI界面展示
- **Adapter**: RecyclerView适配器

## 功能模块

### 第一阶段 (MVP版本) - 已完成

✅ **基础登录注册**
- 手机号登录
- 用户注册
- 表单验证

✅ **一对一文本聊天**
- 消息发送/接收
- 消息列表展示
- WebSocket实时通信

✅ **好友添加**
- 好友列表展示
- 好友搜索（待完善）
- 好友详情（待完善）

✅ **基本UI框架**
- 主界面（底部导航）
- 消息列表页面
- 联系人页面
- 聊天界面

## 项目结构

```
app/src/main/java/com/tongxun/
├── data/                    # 数据层
│   ├── local/              # 本地数据
│   │   ├── entity/         # Room实体
│   │   ├── dao/            # DAO接口
│   │   └── TongxunDatabase.kt
│   ├── remote/             # 远程数据
│   │   ├── api/            # Retrofit接口
│   │   ├── dto/            # 数据传输对象
│   │   └── WebSocketManager.kt
│   ├── repository/         # Repository实现
│   └── di/                 # 依赖注入模块
├── domain/                 # 领域层
│   └── repository/         # Repository接口
├── ui/                     # 表现层
│   ├── auth/              # 登录注册
│   ├── main/              # 主界面
│   ├── home/              # 消息列表
│   ├── chat/              # 聊天界面
│   ├── contact/           # 联系人
│   ├── discover/          # 发现
│   └── me/                # 我的
└── service/                # 后台服务
```

## 配置说明

### 1. 服务器地址配置

在 `app/src/main/java/com/tongxun/data/remote/NetworkModule.kt` 中修改：

```kotlin
private const val BASE_URL = "http://your-server-url.com/api/"
```

### 2. WebSocket地址

WebSocket地址会自动基于BASE_URL生成，格式为：`ws://your-server-url.com/ws?token=xxx`

## 开发计划

### 第二阶段（待开发）

- [ ] 群聊功能
- [ ] 文件/图片消息
- [ ] 语音消息
- [ ] 推送通知
- [ ] 消息撤回
- [ ] 消息已读/未读状态

### 第三阶段（待开发）

- [ ] 音视频通话
- [ ] 朋友圈/动态
- [ ] 红包系统
- [ ] 位置共享
- [ ] 文件传输（分片上传/断点续传）

## 使用说明

### 1. 克隆项目

```bash
git clone <repository-url>
cd Tongxun
```

### 2. 配置服务器地址

修改 `NetworkModule.kt` 中的 `BASE_URL` 为你的服务器地址。

### 3. 构建运行

```bash
./gradlew assembleDebug
```

或使用Android Studio直接运行。

## 注意事项

1. **服务器端**: 本项目只包含Android客户端代码，需要配合后端服务器使用。
2. **WebSocket**: 确保服务器支持WebSocket协议。
3. **Token认证**: 登录后需要保存Token，后续请求需要携带Token。
4. **数据库迁移**: 如果修改数据库结构，需要更新版本号和迁移脚本。

## 许可证

本项目仅供学习交流使用。

