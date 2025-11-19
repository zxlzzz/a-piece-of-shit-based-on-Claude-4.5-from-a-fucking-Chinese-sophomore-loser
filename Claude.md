# Claude 开发文档

## ⚠️ 强制规则

### 1. 禁用 WebSocket 心跳检测
**严格禁止**启用心跳（heartbeat），玩家答题时会长时间无操作

前端 `ws.js`: `heartbeatIncoming: 0, heartbeatOutgoing: 0`
后端 `WebSocketConfig.java`: 不要添加 `.setHeartbeatValue()`

### 2. 公共页面不允许登录检查
**严禁**添加 `requiresAuth: true` 或页面级登录检查

公共页面：`/`, `/login`, `/find`, `/table`, `/history`
需要登录：`/wait/:id`, `/game/:id`, `/result/:id`

### 3. 数据库级联删除规范
**独立实体禁止级联删除**：PlayerEntity、RoomEntity
**专属数据允许级联删除**：GameEntity的子数据

RoomEntity → Players: 只用 `PERSIST, MERGE, REFRESH`，**禁止** `CascadeType.ALL` 和 `orphanRemoval`


### 4. 不修改application内容，不新增多余的打印日志内容（除非有必要，且测试完后需删除所有打印内容）
---

## 最近修复

### 2025-11-19

| 问题 | 修复 |
|------|------|
| P1-1: dev环境数据库重启删除数据 | `ddl-auto: create-drop` → `update` |
| P1-2/P1-3: Controller返回null响应 | 移除try-catch，用全局异常处理器 |
| P1-5: JWT开发环境配置严格 | 提供默认密钥+警告 |
| P0-7: RoomEntity级联删除误删玩家 | 移除CascadeType.ALL |
| P1-7: PlayerEntity级联配置不一致 | 统一cascade配置 |
| P1-8: 大厅页面强制登录检查 | 移除页面级检查，仅操作时检查 |

**涉及文件**：
- `application-dev.yml`, `JwtProperties.java`
- `AuthController.java`, `GameController.java`
- `RoomEntity.java`, `PlayerEntity.java`
- `RoomView.vue`, `router/index.js`

---

## 技术栈
后端: Spring Boot 3.3.5 + MySQL + Redis + JWT
前端: Vue 3.4 + Pinia + Vite 5

## 快速启动
```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm run dev
```

**注意**：MySQL(3306) + Redis(6379) 需运行
