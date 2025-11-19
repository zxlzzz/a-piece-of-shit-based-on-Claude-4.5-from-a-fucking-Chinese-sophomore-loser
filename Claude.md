# Claude 开发文档

> **项目**: Boilune - 多人在线策略答题游戏
> **最后更新**: 2025年11月19日

---

## ⚠️ 强制规则 - 必须遵守

### 🚫 规则1: 禁用 WebSocket 心跳检测

**严格要求**: WebSocket **不允许启用心跳检测**（heartbeat）

**原因**:
- 玩家答题时需要专注思考，可能15-30秒不发送任何消息
- 心跳检测会将连接判定为"超时"，强制断开
- 本项目为本地开发/小规模部署，不需要心跳保活

**强制配置**:

后端 (`backend/src/main/java/org/example/config/WebSocketConfig.java`):
```java
// ✅ 正确：禁用心跳
registry.enableSimpleBroker("/topic", "/queue", "/user")
    .setTaskScheduler(taskScheduler());
// ❌ 错误：不要添加 .setHeartbeatValue()
```

前端 (`frontend/src/websocket/ws.js`):
```javascript
// ✅ 正确：禁用心跳
stompClient = new Client({
  heartbeatIncoming: 0,  // 必须为 0
  heartbeatOutgoing: 0,  // 必须为 0
})
```

**违规处理**: 如发现任何心跳配置，必须立即删除

---

### 🔓 规则2: 公共页面不允许登录检查

**严格要求**: 以下页面**不允许添加登录检查**（`meta: { requiresAuth: true }`）

**公共页面列表**:
- `/` - 首页/仪表板
- `/login` - 登录页（登录页加登录检查是逻辑错误）
- `/find` - 大厅/房间列表页
- `/table` - 题库页
- `/history` - 历史记录页（游客查看时显示空即可）

**需要登录检查的页面**（仅限以下3个）:
- `/wait/:roomId` - 等待房间
- `/game/:roomId` - 游戏页面
- `/result/:roomId` - 结果页面

**代码位置**: `frontend/src/router/index.js:8-64`

**原因**:
- 提升用户体验：允许游客浏览房间列表、题库、历史记录
- 避免登录墙：不强制用户注册才能了解游戏
- 登录页加登录检查是明显的逻辑错误

**违规处理**: 如发现公共页面添加了登录检查，必须立即删除

---

### 🗑️ 规则3: 数据库级联删除规范

**严格要求**: 理解并正确使用 JPA 级联配置

**核心原则**:
- **独立实体不允许级联删除**: PlayerEntity（玩家账号）、RoomEntity（房间）等
- **专属数据允许级联删除**: GameEntity的submissions、playerGames、result等

**关键配置**:

RoomEntity → Players（✅ 正确）:
```java
@OneToMany(mappedBy = "room",
        cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH},
        fetch = FetchType.LAZY)
private List<PlayerEntity> players = new ArrayList<>();
// ✅ 不允许 CascadeType.ALL 或 orphanRemoval = true
```

PlayerEntity → Submissions（✅ 正确）:
```java
@OneToMany(mappedBy = "player",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY)
private List<SubmissionEntity> submissions = new ArrayList<>();
// ✅ 硬删除玩家时级联删除历史记录（但建议使用软删除）
```

**原因**: 防止删除房间时误删玩家账号（P0级严重问题）

---

## 📋 最近修复记录

### 2025-11-19: 配置与架构优化（4个问题）

| 优先级 | 问题描述 | 影响 | 修复方式 |
|--------|---------|------|---------|
| P1-1 | 开发环境数据库每次重启删除数据 | 开发体验极差 | `ddl-auto: create-drop` → `update` |
| P1-2 | AuthController 返回 null 响应体 | 前端无法获取错误信息 | 移除try-catch，使用全局异常处理器 |
| P1-3 | GameController 错误处理不统一 | 同P1-2 | 移除try-catch，统一错误响应格式 |
| P1-5 | JWT开发环境配置太严格 | 新手无法启动项目 | 提供默认密钥+警告日志 |

**涉及文件**:
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/java/org/example/config/JwtProperties.java`
- `backend/src/main/java/org/example/controller/AuthController.java`
- `backend/src/main/java/org/example/controller/GameController.java`

---

### 2025-11-19: 数据库实体级联关系修复（3个问题）

| 优先级 | 问题描述 | 影响 | 修复方式 |
|--------|---------|------|---------|
| P0-7 | RoomEntity级联删除会误删玩家 | 删除房间会删除玩家账号 | 移除CascadeType.ALL和orphanRemoval |
| P1-7 | PlayerEntity级联配置不一致 | 删除玩家导致孤立submissions | 统一cascade配置 |
| P2-4 | 应使用软删除而非硬删除玩家 | 历史记录永久丢失 | 添加警告日志+注释建议 |

**涉及文件**:
- `backend/src/main/java/org/example/entity/RoomEntity.java`
- `backend/src/main/java/org/example/entity/PlayerEntity.java`
- `backend/src/main/java/org/example/service/player/impl/PlayerServiceImpl.java`
- `backend/src/main/java/org/example/controller/PlayerController.java`

---

### 2025-11-19: 路由登录检查规范化（1个问题）

| 优先级 | 问题描述 | 影响 | 修复方式 |
|--------|---------|------|---------|
| P1-8 | 历史记录页有登录检查 | 游客无法查看历史页 | 移除 `requiresAuth: true` |

**涉及文件**:
- `frontend/src/router/index.js`

**验证**: `/`, `/login`, `/find`, `/table`, `/history` 均无登录检查

---

## 📋 历史修复汇总

### WebSocket 稳定性优化（已完成）

**主要修复**:
- ✅ 修复13个WebSocket问题（P0运行时崩溃、P1内存泄漏、竞态条件等）
- ✅ 修复10个房间删除逻辑问题（数据库泄漏、资源泄漏、并发竞态）
- ✅ 添加聊天重连机制
- ✅ 优化后端线程池管理
- ✅ 统一等待连接工具函数

**详细文档**: 见项目历史commit记录

---

## 🔧 技术架构

### 后端
- **框架**: Spring Boot 3.3.5 + Java 17
- **数据库**: MySQL 8 + JPA/Hibernate
- **缓存**: Redis
- **WebSocket**: STOMP 协议
- **安全**: JWT + BCrypt

### 前端
- **框架**: Vue 3.4 + Vite 5
- **状态管理**: Pinia
- **路由**: Vue Router
- **WebSocket**: @stomp/stompjs

---

## 🚀 开发环境快速启动

### 后端
```bash
cd backend
mvn spring-boot:run
```

**注意**:
- MySQL需在 `localhost:3306` 运行
- Redis需在 `localhost:6379` 运行
- JWT密钥使用开发默认值（启动时会有警告）

### 前端
```bash
cd frontend
npm install
npm run dev
```

**访问**: http://localhost:5173

---

## 📝 开发注意事项

### 数据库
- ✅ 开发环境使用 `ddl-auto: update` 保留数据
- ✅ 生产环境禁用 `ddl-auto`，使用 Flyway 或 Liquibase 管理版本

### 错误处理
- ✅ Controller层不使用try-catch，直接抛出BusinessException
- ✅ 全局异常处理器统一返回格式：`{ "error": true, "message": "...", "timestamp": ... }`
- ✅ 前端API客户端自动处理错误提示

### 软删除
- ✅ PlayerEntity支持软删除（`deleted`, `deletedAt` 字段）
- ⚠️ 当前API实现的是硬删除，建议后续改为软删除
- ✅ 所有Repository查询已过滤 `deleted = false`

---

**文档版本**: 2.0
**维护原则**: 保持简洁，只记录关键规则和最近修复
