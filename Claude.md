# Claude 开发文档

> **分支**: `claude/analyze-project-features-011CV5NFyW1hJJNRfoFeDxjv`
> **创建时间**: 2025年1月
> **开发者**: Claude (Anthropic AI Assistant)
> **项目**: Boilune - 多人在线策略答题游戏

---

## ⚠️ 重要约定 - 必须遵守

### 🚫 **禁用 WebSocket 心跳检测机制**

**严格要求**：WebSocket **不允许启用心跳检测**（heartbeat）

**原因**：
1. 玩家答题时需要专注思考，可能15-30秒不发送任何消息
2. 心跳检测会在无操作时将连接判定为"超时"，强制断开连接
3. 本项目为本地开发/小规模部署，不需要心跳保活
4. 已在开发和测试中验证，禁用心跳更稳定

**配置要求**：

后端 (`backend/src/main/java/org/example/config/WebSocketConfig.java`):
```java
// ✅ 正确配置：禁用心跳
registry.enableSimpleBroker("/topic", "/queue", "/user")
    .setTaskScheduler(taskScheduler());
// ❌ 错误：不要添加 .setHeartbeatValue()
```

前端 (`frontend/src/websocket/ws.js`):
```javascript
// ✅ 正确配置：禁用心跳
stompClient = new Client({
  heartbeatIncoming: 0,  // 必须为 0
  heartbeatOutgoing: 0,  // 必须为 0
})
```

**违规处理**：
- 如果发现任何心跳配置，必须立即删除
- 不允许以"优化稳定性"为由添加心跳
- 如需修改，必须先与用户讨论

---

### 🔧 **WebSocket 连接优化规范**

**已实施的关键优化**（2025-01）：

#### 1️⃣ 聊天重连机制
**问题**：WebSocket 断线重连后，聊天功能失效（收不到消息）

**解决方案**：
- 在 `chat.js` 中添加重连回调注册机制
- 使用 `registerSubscriptionCallback()` 和 `unregisterSubscriptionCallback()`
- 重连后自动恢复聊天订阅

**代码位置**：
- `frontend/src/stores/chat.js:22-27` - 重连回调函数
- `frontend/src/stores/chat.js:151-152` - 注册回调
- `frontend/src/stores/chat.js:175-177` - 注销回调

#### 2️⃣ 后端线程池管理
**问题**：每个 WebSocket 连接都创建新线程，可能导致线程泄漏和资源耗尽

**解决方案**：
- 创建专用线程池 `wsConnectionExecutor` (10-50线程，队列500)
- 将 `new Thread().start()` 替换为 `executor.execute()`
- 线程池配置：核心10线程，最大50线程，使用 CallerRunsPolicy 拒绝策略

**代码位置**：
- `backend/src/main/java/org/example/config/WebSocketConfig.java:59-71` - 线程池Bean
- `backend/src/main/java/org/example/config/WebSocketConfig.java:161` - 使用线程池

#### 3️⃣ 连接等待机制优化
**问题**：轮询检查连接状态（每200ms检查一次，持续10秒），CPU占用高

**解决方案**：
- 使用事件驱动 Promise 替代轮询
- 监听 `websocket-connected` 事件
- 超时后自动清理事件监听器

**代码位置**：
- `frontend/src/stores/chat.js:66-90` - `waitForConnection()` 函数

**⚠️ 开发注意事项**：
1. **禁止回退**：不要将线程池改回 `new Thread()`
2. **聊天订阅**：必须注册重连回调，否则断线后聊天失效
3. **事件清理**：所有事件监听器必须在超时/成功后清理，避免内存泄漏

---

### 🔧 **WebSocket 连接最终审查 - 修复所有P0和P1问题**

**最终优化**（2025-01）：

本次审查修复了WebSocket连接中所有关键的内存泄漏、资源泄漏和错误处理问题：

#### 前端修复 (ws.js, useWaitRoomWebSocket.js)
- **内存泄漏**: 个人消息订阅未清理 → 添加 `cleanupPersonalSubscriptions()`
- **资源泄漏**: 连接超时handler未清理 → 添加 `connectTimeoutId` 跟踪和清理
- **参数错误**: 重连回调传递无效参数 → 修复 `subscriptionRestoreCallback`

#### 后端修复 (ChatWebSocketController, WebSocketEventListener, SessionManager)
- **错误处理**: @MessageMapping 方法缺少异常处理 → 添加 try-catch，失败时通知用户
- **错误传播**: 断连事件异常会中断清理流程 → 捕获异常，确保清理继续
- **会话泄漏**: 断连失败时会话永久残留内存 → 添加定时清理（每10分钟，清理2小时以上会话）
- **监控能力**: 添加 `SessionManager.getStats()` 获取会话统计

**影响**: 生产环境稳定性显著提升，消除内存泄漏风险，增强异常恢复能力。

---

### 🔧 **WebSocket 连接优化规范 - 第二次优化**

**补充优化**（2025-01）：

#### 4️⃣ 统一等待连接工具函数
**问题**：多处代码重复实现等待连接逻辑（chat.js、useGameWebSocket.js）

**解决方案**：
- 在 `ws.js` 中添加通用的 `waitForConnection()` 函数
- 所有需要等待连接的地方统一使用此函数
- 避免代码重复，统一维护

**代码位置**：
- `frontend/src/websocket/ws.js:312-340` - `waitForConnection()` 工具函数
- `frontend/src/stores/chat.js:84-88` - 聊天订阅中使用
- `frontend/src/composables/game/useGameWebSocket.js:240-254` - 游戏视图中使用
- `frontend/src/composables/room/useWaitRoomWebSocket.js:170-184` - 等待房间中使用

**函数签名**：
```javascript
export function waitForConnection(maxWait = 10000): Promise<void>
```

**使用示例**：
```javascript
// 等待连接（默认10秒超时）
await waitForConnection()

// 自定义超时时间（3秒）
await waitForConnection(3000)
```

**优势**：
- ✅ 事件驱动，无轮询开销
- ✅ 统一超时控制
- ✅ 自动清理事件监听器
- ✅ 减少代码重复，便于维护

---

## 📋 目录

1. [分支概览](#分支概览)
2. [核心功能实现](#核心功能实现)
3. [技术架构优化](#技术架构优化)
4. [WebSocket架构重构](#websocket架构重构)
5. [UI/UX优化](#uiux优化)
6. [问题修复记录](#问题修复记录)
7. [关键文件说明](#关键文件说明)
8. [部署建议](#部署建议)
9. [后续优化方向](#后续优化方向)

---

## 分支概览

### 🎯 分支目标

本分支主要聚焦于以下几个方面的优化：

1. **全局聊天系统重构** - 实现跨页面持久化的聊天体验
2. **WebSocket稳定性优化** - 解决频繁断连问题
3. **用户体验提升** - 添加平滑动画和交互优化
4. **代码架构改进** - 降低耦合度，提升可维护性

### 📊 改动统计

- **17次提交**
- **涉及文件**: 约30+个文件
- **代码变更**: ~1000行新增，~500行删除
- **主要语言**: JavaScript (Vue 3), Java (Spring Boot 3)

---

## 核心功能实现

### 1. 题目反馈功能 (b170f8b)

**实现内容**:
- 用户可以对每道题目提交反馈
- 支持多种反馈类型（题目错误、计分问题、建议等）
- 反馈数据持久化到后端

**关键文件**:
- `frontend/src/components/feedback/` - 反馈组件
- `backend/src/main/java/org/example/controller/FeedbackController.java`

### 2. 联系作者页面 (f75c860)

**实现内容**:
- 独立的联系页面
- 提供作者联系方式和项目信息
- 响应式设计，支持移动端

**文件位置**: `frontend/src/views/Contact.vue`

### 3. 全局ChatRoom系统 (caf1503, d7b5f16)

这是本分支**最核心**的功能改进。

#### 架构设计

**之前的问题**:
- WaitRoom、GameView、ResultView各自管理ChatRoom
- 页面切换时聊天历史丢失
- 需要重新连接WebSocket
- 每次打开/关闭聊天都会触发订阅/取消订阅

**新架构**:
```
App.vue (全局)
  ├── ChatRoom (单例，fixed定位)
  ├── MobileChatDrawer (移动端)
  └── Router View
        ├── WaitRoom
        ├── GameView
        └── ResultView
```

**核心特性**:
1. **单例模式** - 整个应用只有一个ChatRoom实例
2. **状态管理** - 通过Pinia store (`chat.js`) 集中管理
3. **路由守卫** - 自动管理订阅生命周期
4. **消息持久化** - 在WaitRoom→GameView→ResultView间保持聊天历史

#### 技术实现

**chatStore架构** (`frontend/src/stores/chat.js`):
```javascript
export const useChatStore = defineStore('chat', () => {
  const roomCode = ref(null)
  const visible = ref(false)
  const messages = ref([])  // 消息持久化
  let chatSubscription = null

  const subscribeToChat = async (code) => {
    // 防重复订阅
    // WebSocket连接等待（最多10秒）
    // 订阅聊天频道
    // 发送加入消息
  }

  const unsubscribeFromChat = () => {
    // 取消订阅
  }

  const sendChatMessage = (content) => {
    // 发送消息到WebSocket
  }
})
```

**路由守卫管理** (`frontend/src/router/index.js:78-114`):
```javascript
router.beforeEach(async (to, from, next) => {
  const roomPages = ['wait', 'game', 'result']
  const fromRoom = roomPages.includes(from.name)
  const toRoom = roomPages.includes(to.name)

  // 离开房间页面 → 断开订阅和WebSocket
  if (fromRoom && !toRoom) {
    chatStore.unsubscribeFromChat()
    chatStore.clearChat()
    disconnect()
  }

  // 进入房间页面 → 自动订阅
  if (toRoom && to.params.roomId) {
    chatStore.subscribeToChat(to.params.roomId)
  }

  next()
})
```

**App.vue全局组件** (`frontend/src/App.vue:167-191`):
```vue
<!-- 桌面端：fixed定位在右侧 -->
<teleport to="body">
  <transition name="slide-left">
    <div v-show="chatStore.visible && isDesktop"
         class="fixed top-0 right-0 h-screen w-[400px] z-[60]">
      <ChatRoom v-if="chatStore.roomCode" ... />
    </div>
  </transition>
</teleport>

<!-- 移动端：抽屉式 -->
<MobileChatDrawer v-if="chatStore.roomCode" ... />
```

#### 后端支持

**ChatRoomManager** (`backend/src/main/java/org/example/service/chat/ChatRoomManager.java`):
- 管理聊天室生命周期，独立于游戏房间
- 支持游戏结束后聊天室继续存在
- 自动清理机制：5分钟无活动且无人在线

**核心逻辑**:
```java
@Scheduled(fixedRate = 60000)
public void cleanupInactiveChatRooms() {
    for (String roomCode : activeChatRooms) {
        int onlineCount = getOnlineCount(roomCode);
        if (onlineCount > 0) continue;

        LocalDateTime lastActivity = roomLastActivity.get(roomCode);
        if (lastActivity.plusMinutes(5).isBefore(now)) {
            // 清理聊天室
        }
    }
}
```

---

## WebSocket架构重构

### 问题诊断过程

#### 问题1: 答题时15秒断连 (41b6509)

**现象**: 玩家专心答题（无任何操作）15秒后WebSocket自动断开

**根本原因**:
- 前后端配置了心跳检测（25秒间隔）
- 玩家答题时不发送任何消息
- 超过心跳间隔被判定为"无响应"

**解决方案**: **完全禁用心跳检测**

前端 (`frontend/src/websocket/ws.js:85-88`):
```javascript
stompClient = new Client({
  heartbeatIncoming: 0,  // 禁用
  heartbeatOutgoing: 0,  // 禁用
})
```

后端 (`backend/config/WebSocketConfig.java:52-55`):
```java
registry.enableSimpleBroker("/topic", "/queue", "/user")
    .setHeartbeatValue(new long[]{0, 0});
```

**为什么可以禁用?**
- 本地开发环境，网络环境简单
- 不存在复杂的负载均衡、反向代理
- SockJS本身有连接保活机制

#### 问题2: 重复订阅导致消息重复 (d7b5f16)

**现象**: 每次打开/关闭聊天窗口，订阅就重新建立

**原因**:
- App.vue中ChatRoom使用`v-if`
- 每次显示/隐藏都会触发mount/unmount
- ChatRoom在mount时自动订阅

**解决方案**:
1. ChatRoom外层div使用`v-show`（避免销毁）
2. 订阅逻辑移到chatStore
3. 路由守卫统一管理订阅生命周期

#### 问题3: WebSocket连接超时 (54d3ec1)

**问题**: chatStore订阅时只等待3秒，连接慢时失败

**优化**:
```javascript
// 之前: 等待3秒
while (!isConnected() && waited < 3000) { ... }

// 现在: 等待10秒，记录等待时长
const maxWait = 10000
while (!isConnected() && waited < maxWait) { ... }
logger.info(`WebSocket连接成功（等待${waited}ms）`)
```

### 重连机制简化

**优化前**: 最多重连5次，指数退避
**优化后**: 最多重连2次

**原因**: 本地开发环境，真断网重连也没用，减少无意义重试

文件: `frontend/src/config/constants.js:26`
```javascript
export const WS_MAX_RECONNECT_ATTEMPTS = 2
```

---

## UI/UX优化

### 1. 平滑过渡动画 (74e4874)

**核心优化**: ChatRoom打开/关闭时，页面内容平滑移动

**之前**: padding瞬间变化，内容"跳"到左边
**现在**: 300ms平滑过渡

**实现** (WaitRoom.vue, GameView.vue, ResultView.vue):
```vue
<div class="transition-[padding] duration-300 ease-in-out"
     :class="chatStore.visible && isDesktop ? 'pr-[420px]' : ''">
```

**效果**:
- 点击聊天按钮 → 页面内容向左移动 + chatroom从右滑入
- 关闭聊天 → chatroom滑出 + 内容回到中间
- 动画同步，视觉流畅

### 2. 聊天消息弹入动画

**实现** (`frontend/src/components/chat/ChatRoom.vue:184-210`):
```vue
<TransitionGroup name="message">
  <div v-for="msg in messages" :key="msg.id">
    <!-- 消息内容 -->
  </div>
</TransitionGroup>

<style>
@keyframes message-slide-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
```

**效果**: 新消息从下往上滑动+淡入，动画时长300ms

### 3. 按钮交互优化

**改进点**:
- **Hover**: 放大5% (`hover:scale-105`)
- **Active**: 缩小5% (`active:scale-95`)
- **过渡**: 200ms平滑过渡
- **图标**: 打开时显示蓝色×，关闭时显示评论图标

**文件**:
- `WaitRoom.vue:559-568`
- `ResultView.vue:51-60`
- `GameHeader.vue:37-51`

### 4. 布局优化

**统一策略**: 所有页面采用相同的布局方式

**WaitRoom改进**:
- 移除`lg:grid-cols-3`的grid布局
- 改为单列`max-w-4xl`布局
- 打开聊天时动态添加`pr-[420px]`

**效果**: 三个页面（WaitRoom、GameView、ResultView）布局一致

---

## 问题修复记录

### Canvas相关

#### 1. 首次加载坐标偏移 (fdc0cce)
**问题**: canvas首次加载时，绘制坐标偏移
**原因**: 未等待canvas完全渲染就开始绘制
**解决**: 添加`nextTick`等待DOM渲染完成

#### 2. 尺寸变化内容放大 (fe54613)
**问题**: 窗口resize时，canvas内容被放大/缩小
**原因**: canvas尺寸改变但未重绘内容
**解决**: 监听resize事件，重新绘制

### WebSocket相关

#### 1. 连接和重连机制 (443d086)
**问题**:
- 重连时机不对
- 连接状态判断有误
- 订阅时序问题

**解决**:
- 使用Promise统一管理连接流程
- 添加连接超时保护
- 修复状态标志更新时机

#### 2. 结果页面断连 (4f92a7e)
**问题**: ResultView进入后很快断连
**原因**: 未正确初始化WebSocket连接
**解决**: 在路由守卫中统一管理连接

### 其他优化

#### 1. 题目反馈默认收起 (ec3b1af)
**优化**: 反馈表单改为展开式，避免占用过多空间

#### 2. 日志精简 (be06173, 7b60f9f)
**优化**:
- 开发环境只显示INFO及以上级别
- 反馈相关日志改为DEBUG级别
- 减少控制台噪音

---

## 关键文件说明

### 前端核心文件

#### 状态管理
- **`frontend/src/stores/chat.js`** - 聊天状态管理
  - 订阅管理
  - 消息持久化
  - WebSocket通信

- **`frontend/src/stores/player.js`** - 玩家状态管理

#### WebSocket
- **`frontend/src/websocket/ws.js`** - WebSocket客户端
  - STOMP连接管理
  - 重连机制
  - 订阅工具函数

#### 路由
- **`frontend/src/router/index.js`** - 路由配置和守卫
  - 聊天订阅管理（78-114行）
  - 权限检查
  - 页面跳转逻辑

#### 组件
- **`frontend/src/App.vue`** - 根组件
  - 全局ChatRoom容器（167-191行）
  - Teleport实现fixed定位

- **`frontend/src/components/chat/ChatRoom.vue`** - 聊天组件
  - 消息展示
  - 消息发送
  - 动画效果

#### 页面
- **`frontend/src/views/room/WaitRoom.vue`** - 等待房间
- **`frontend/src/views/room/GameView.vue`** - 游戏页面
- **`frontend/src/views/room/ResultView.vue`** - 结果页面

### 后端核心文件

#### WebSocket配置
- **`backend/config/WebSocketConfig.java`** - WebSocket配置
  - 心跳设置（52-55行：已禁用）
  - 线程池配置
  - 拦截器

#### 聊天服务
- **`backend/service/chat/ChatRoomManager.java`** - 聊天室管理
  - 生命周期管理
  - 自动清理（5分钟无活动）
  - 在线统计

- **`backend/controller/ChatWebSocketController.java`** - 聊天WebSocket控制器
  - 消息处理
  - 活动记录

#### 配置文件
- **`backend/resources/application.yml`** - 主配置
- **`backend/resources/application-dev.yml`** - 开发环境配置

---

## 部署建议

### 开发环境

#### 前端
```bash
cd frontend
npm install
npm run dev
```

**配置**: `.env.development`
```
VITE_API_URL=http://localhost:8080
VITE_WS_URL=http://localhost:8080/ws
```

#### 后端
```bash
cd backend
mvn clean install
mvn spring-boot:run
```

**配置**: `application-dev.yml`
- 数据库: MySQL (localhost:3306)
- Redis: localhost:6379
- WebSocket: 心跳已禁用

### 生产环境注意事项

#### 1. WebSocket心跳
如果部署到真实服务器（有负载均衡、反向代理），建议：
```java
// 设置较长的心跳间隔（如120秒）
.setHeartbeatValue(new long[]{120000, 120000});
```

前端对应修改：
```javascript
heartbeatIncoming: 120000,
heartbeatOutgoing: 120000,
```

#### 2. Nginx配置
如果使用Nginx反向代理，需要配置WebSocket支持：
```nginx
location /ws {
    proxy_pass http://backend:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 86400;  # 24小时
}
```

#### 3. 聊天室清理
生产环境可调整清理时间：
```java
// ChatRoomManager.java
if (lastActivity.plusMinutes(30).isBefore(now)) {
    // 30分钟无活动才清理
}
```

---

## 后续优化方向

### 功能增强
1. **聊天历史持久化**
   - 将聊天记录保存到数据库
   - 用户可查看历史聊天

2. **表情和富文本**
   - 支持emoji表情
   - 支持图片发送
   - Markdown格式支持

3. **私聊功能**
   - 一对一私聊
   - 群组聊天

### 性能优化
1. **虚拟滚动**
   - 聊天消息超过100条时使用虚拟滚动
   - 减少DOM节点数量

2. **消息分页**
   - 按需加载历史消息
   - 避免一次性加载所有消息

3. **WebSocket消息压缩**
   - 启用压缩减少带宽

### 用户体验
1. **未读消息提示**
   - 未读消息数量Badge
   - 浏览器通知

2. **@mention功能**
   - 支持@特定玩家
   - 高亮显示

3. **消息搜索**
   - 搜索历史聊天记录

### 代码质量
1. **单元测试**
   - chatStore测试
   - WebSocket连接测试

2. **E2E测试**
   - 聊天功能端到端测试
   - 页面切换测试

3. **TypeScript迁移**
   - 前端代码TypeScript化
   - 类型安全

---

## 总结

本分支通过系统性的架构重构，实现了以下目标：

✅ **稳定性提升**
- WebSocket断连问题基本解决
- 心跳机制优化，适应答题场景
- 重连机制简化，减少不必要重试

✅ **用户体验改善**
- 聊天历史跨页面持久化
- 平滑动画过渡
- 统一的交互反馈

✅ **代码质量提升**
- 降低耦合度（订阅管理集中到路由守卫）
- 单一职责（chatStore专注状态管理）
- 可维护性增强

✅ **功能完整性**
- 题目反馈功能
- 联系作者页面
- Canvas草稿功能完善

---

**文档维护**: 本文档随代码更新而更新
**最后更新**: 2025年1月17日
**文档版本**: 1.0
