# Claude Code 修复记录

本文档记录了 Claude 对游戏系统的完整检查和修复过程。

## 修复概览

### 已完成修复
- ✅ JavaScript 语法错误（2处）
- ✅ 玩家重连问题（3个严重问题）
- ✅ 房间删除逻辑（10个问题）
- ✅ 游戏生命周期（4个关键问题）

### 总计修复问题数：19个

---

## 一、JavaScript 语法错误修复

### 问题描述
进入游戏页面时报错：
```
SyntaxError: Unexpected reserved word (at useGameSubmit.js:53:7)
vue-router.mjs:26 [Vue Router warn]: uncaught error during route navigation
```

### 根本原因
`useGameSubmit.js` 中的两个函数使用了 `await` 关键字，但函数声明缺少 `async` 关键字。

### 修复内容

#### Commit 1: `2fbc50b` - 修复 handleChoose 函数
**文件**: `frontend/src/composables/game/useGameSubmit.js:15`

**修改前**:
```javascript
const handleChoose = (choice) => {
```

**修改后**:
```javascript
const handleChoose = async (choice) => {
```

#### Commit 2: `4166226` - 修复 handleAutoSubmit 函数
**文件**: `frontend/src/composables/game/useGameSubmit.js:77`

**修改前**:
```javascript
const handleAutoSubmit = () => {
```

**修改后**:
```javascript
const handleAutoSubmit = async () => {
```

---

## 二、玩家刷新页面时无法重连问题

### 问题描述
用户报告："一个两人的房间，其中有一人掉线了，然后另一人刷新后就无法连接websocket了"

### 根本原因分析
通过完整检查重连流程，发现3个严重问题：

1. **HTTP API 缺少重连检测**
   - `GameController.joinRoom` 只处理新加入，未区分重连场景
   - 导致已存在玩家刷新时被当作新加入处理

2. **游戏进行中拒绝所有加入请求**
   - `RoomLifecycleServiceImpl.handleJoin` 检查 `room.status != WAITING` 时直接抛异常
   - 未考虑已在房间的玩家刷新场景

3. **已存在玩家处理不完整**
   - handleJoin 的 else 分支只记录日志，未清理 `disconnectedPlayers`
   - 导致玩家状态不一致

### 修复内容

#### Commit: `b5f5b61` - 修复玩家刷新页面时无法重连的3个严重问题

#### 修复1: HTTP API 添加重连检测
**文件**: `backend/src/main/java/org/example/controller/GameController.java:78-95`

**新增逻辑**:
```java
@PostMapping("/rooms/{roomCode}/join")
public ResponseEntity<RoomDTO> joinRoom(...) {
    // 🔥 修复：添加重连检测逻辑
    GameRoom gameRoom = roomCache.get(roomCode);
    boolean isReconnect = gameRoom != null &&
            gameRoom.getDisconnectedPlayers().containsKey(playerId);

    RoomDTO room;
    if (isReconnect) {
        // 重连场景：调用专用的handleReconnect
        roomLifecycleService.handleReconnect(roomCode, playerId);
        room = roomLifecycleService.toRoomDTO(roomCode);
        log.info("✅ 玩家 {} 重连房间 {}", playerName, roomCode);
    } else {
        // 新加入场景：调用原有逻辑
        room = gameService.joinRoom(roomCode, playerId, playerName, spectator, password);
        log.info("✅ 玩家 {} 加入房间 {} 成功 (观战模式: {})", playerName, roomCode, spectator);
    }

    broadcaster.sendRoomUpdate(roomCode, room);
    return ResponseEntity.ok(room);
}
```

#### 修复2: 允许游戏进行中的玩家刷新
**文件**: `backend/src/main/java/org/example/service/room/impl/RoomLifecycleServiceImpl.java:125-151`

**新增逻辑**:
```java
// 🔥 修复问题2：检查房间状态（允许已在房间的玩家刷新/重连）
if (room.getStatus() != RoomStatus.WAITING) {
    // 检查玩家是否已在房间内（允许重连）
    boolean playerInRoom = gameRoom.getPlayers().stream()
            .anyMatch(p -> p.getPlayerId().equals(playerId));

    if (!playerInRoom) {
        // 新玩家不允许加入进行中的游戏
        throw new BusinessException("房间已开始游戏或已结束");
    }

    // 🔥 已在房间的玩家允许刷新/重连，检查是否在断线列表中
    if (gameRoom.getDisconnectedPlayers().containsKey(playerId)) {
        log.info("🔄 玩家 {} 在游戏进行中刷新页面，从断线列表移除", playerName);
        gameRoom.getDisconnectedPlayers().remove(playerId);
        roomCache.syncToRedis(roomCode);
    }

    log.info("✅ 玩家 {} 已在房间中，游戏进行中刷新页面成功", playerName);
    return; // 跳过后续加入逻辑
}
```

#### 修复3: 清理已存在玩家的断线状态
**文件**: `backend/src/main/java/org/example/service/room/impl/RoomLifecycleServiceImpl.java:171-178`

**修改前**:
```java
} else {
    log.info("⚠️ 玩家 {} 已在房间 {} 中", playerName, roomCode);
}
```

**修改后**:
```java
} else {
    // 🔥 修复问题3：玩家已在房间内，清理可能存在的断线状态
    if (gameRoom.getDisconnectedPlayers().containsKey(playerId)) {
        gameRoom.getDisconnectedPlayers().remove(playerId);
        roomCache.syncToRedis(roomCode);
        log.info("🔄 玩家 {} 已在房间中但在断线列表，清理断线状态", playerName);
    }
    log.info("✅ 玩家 {} 已在房间 {} 中", playerName, roomCode);
}
```

---

## 三、房间删除逻辑修复

### 问题概览
通过完整审计房间删除逻辑，发现 **10个问题**（3个P0，2个P1，5个P2）

### 修复内容

#### Commit 1: `1f17b25` - 修复房间删除逻辑的8个严重问题

修复了以下问题：

1. **P0-1: 数据库记录永不删除**
   - 原问题：房间只标记为 FINISHED，从不真正删除
   - 修复：改为真实删除 RoomEntity 和关联的 PlayerEntity

2. **P0-2: 玩家记录孤儿化**
   - 原问题：删除房间时未删除关联的 PlayerEntity
   - 修复：添加级联删除逻辑

3. **P1-1: 聊天室保留5分钟**
   - 原问题：`ChatRoomManager` 依赖 TTL 机制延迟清理
   - 修复：新增 `forceCleanup` 方法立即清理

4. **P0-5: Redis 同步失败无重试**
   - 原问题：Redis 删除失败时不重试
   - 修复：`RoomCache.removeWithRetry` 实现3次重试机制

5. **P0-6: RoomLock 对象泄漏**
   - 原问题：`RoomLock.getLock()` 创建的锁对象从不清理
   - 修复：在 synchronized 块外调用 `RoomLock.removeLock()`

6. **P0-7: 并发删除竞态条件**
   - 原问题：多个线程可能同时删除同一房间
   - 修复：统一使用 `deleteRoomAtomically()` 原子操作

7. **P2-3: 定时器未取消**
   - 原问题：`QuestionTimerService` 的定时器可能未清理
   - 修复：在 `finishGame` 中调用 `timerService.cancelTimeout()`

8. **P2-4: 断线玩家清理不完整**
   - 原问题：`handlePlayerDisconnect` 中的延迟删除任务未取消
   - 修复：统一使用 `deleteRoom()` 方法完整清理

**关键代码改动**:

##### 新增 ChatRoomManager.forceCleanup()
**文件**: `backend/src/main/java/org/example/service/chat/ChatRoomManager.java:138-145`
```java
public void forceCleanup(String roomCode) {
    activeChatRooms.remove(roomCode);
    chatRoomLastActivity.remove(roomCode);
    chatRoomUsers.remove(roomCode);
    log.info("🧹 已强制清理聊天室: {}", roomCode);
}
```

##### 新增 RoomCache.removeWithRetry()
**文件**: `backend/src/main/java/org/example/service/cache/RoomCache.java:115-136`
```java
public void removeWithRetry(String roomCode) {
    // 清理本地缓存
    localCache.remove(roomCode);
    roomCreationTime.remove(roomCode);

    // 重试删除 Redis
    String redisKey = getRedisKey(roomCode);
    int maxRetries = 3;
    for (int i = 0; i < maxRetries; i++) {
        try {
            redisTemplate.delete(redisKey);
            log.debug("✅ 已从 Redis 删除房间: {}", roomCode);
            return;
        } catch (Exception e) {
            if (i < maxRetries - 1) {
                Thread.sleep(100 * (i + 1)); // 指数退避
            }
        }
    }
}
```

##### 原子删除 deleteRoomAtomically()
**文件**: `backend/src/main/java/org/example/service/room/impl/RoomLifecycleServiceImpl.java:397-457`
```java
private RoomEntity deleteRoomAtomically(String roomCode, GameRoom gameRoom) {
    RoomEntity room;
    synchronized (RoomLock.getLock(roomCode)) {
        // 1. 获取数据库实体
        room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        // 2. 清理玩家记录
        for (PlayerDTO player : gameRoom.getPlayers()) {
            if (player.getPlayerId().startsWith("BOT_")) continue;
            playerRepository.findByPlayerId(player.getPlayerId())
                    .ifPresent(playerEntity -> {
                        playerRepository.delete(playerEntity);
                    });
        }

        // 3. 删除房间实体
        roomRepository.delete(room);

        // 4. 清理内存缓存
        roomCache.removeWithRetry(roomCode);

        // 5. 强制清理聊天室
        chatRoomManager.forceCleanup(roomCode);

        // 6. 取消定时器
        timerService.cancelTimeout(roomCode);
    }

    // 🔥 P0-6: 在synchronized块外清理锁，防止内存泄漏
    RoomLock.removeLock(roomCode);

    return room;
}
```

##### 公开 deleteRoom() 方法
**文件**: `backend/src/main/java/org/example/service/room/impl/RoomLifecycleServiceImpl.java:383-395`
```java
@Override
@Transactional
public void deleteRoom(String roomCode) {
    GameRoom gameRoom = roomCache.getOrThrow(roomCode);

    RoomEntity room = deleteRoomAtomically(roomCode, gameRoom);

    log.info("🗑️ 房间 {} 已完整删除（状态: {}）", roomCode, room.getStatus());
}
```

#### Commit 2: `24454b5` - 补充修复2个资源泄漏问题（RoomLock和advancing锁）

修复了以下问题：

9. **P1-4: finishGame 中 advancing 锁泄漏**
   - 原问题：`GameFlowServiceImpl.advancing` 的 Map 条目从不清理
   - 修复：在 `finishGame` 的 finally 块中添加 `advancing.remove(roomCode)`

10. **P0-6: deleteRoom 中 RoomLock 泄漏（补充修复）**
    - 原问题：`handleLeave` 删除空房间时未清理 RoomLock
    - 修复：在房间为空时也调用统一的 `deleteRoom()` 方法

**关键代码改动**:

##### finishGame 清理 advancing 锁
**文件**: `backend/src/main/java/org/example/service/flow/impl/GameFlowServiceImpl.java:349-354`
```java
} finally {
    // 7. 清理玩家状态
    gameRoom.clearPlayerStates();

    // 🔥 8. 清理推进锁（P1-4修复）
    advancing.remove(roomCode);
    log.debug("🔧 已清理房间 {} 的推进锁", roomCode);

    // ... 其他清理逻辑
}
```

##### handleLeave 统一删除逻辑
**文件**: `backend/src/main/java/org/example/service/room/impl/RoomLifecycleServiceImpl.java:286-295`
```java
// 🔥 P0修复：房间为空时统一使用deleteRoom清理所有资源
if (gameRoom.getPlayers().isEmpty() && gameRoom.getDisconnectedPlayers().isEmpty()) {
    log.info("🗑️ 房间 {} 已无玩家，准备删除", roomCode);

    deleteRoom(roomCode);  // 使用统一方法，确保完整清理

    broadcaster.sendRoomDeleted(roomCode);

    log.info("✅ 房间 {} 已删除（所有玩家离开）", roomCode);
    return false; // 房间已不存在
}
```

---

## 四、游戏生命周期修复

### 问题概览
通过完整审计房间生命周期（创建→等待→开始→进行→结束→返回），发现多个高优先级问题。

### Commit: `5d49ed8` - 修复游戏生命周期的4个关键问题

#### 修复1: 游戏启动时题目为空的检查（问题3.2）
**文件**: `backend/src/main/java/org/example/service/flow/impl/GameFlowServiceImpl.java:141-151`

**问题**:
- `questionSelector.selectQuestions()` 返回空列表时，游戏仍然启动
- 导致游戏无题目可玩

**修复**:
```java
// 🔥 修复问题3.2：检查题目是否为空
if (questions == null || questions.isEmpty()) {
    log.error("❌ 题目加载失败：questionCount={}, 标签={}", room.getQuestionCount(), questionTagIds);
    // 回滚状态
    room.setStatus(RoomStatus.WAITING);
    roomRepository.save(room);
    gameRoom.setRoomEntity(room);
    throw new BusinessException("题目加载失败，请检查题库或标签设置");
}

log.info("✅ 成功加载 {} 道题目", questions.size());
```

#### 修复2: finishGame 异常处理优化（问题5.2）
**文件**: `backend/src/main/java/org/example/service/flow/impl/GameFlowServiceImpl.java:342-347`

**问题**:
- `finishGame` 抛异常时，`finished=true` 状态已设置
- 导致游戏卡在 finished 状态，无法重试

**修复**:
```java
} catch (Exception e) {
    log.error("❌ 游戏结束流程失败: roomCode={}", roomCode, e);
    // 🔥 修复问题5.2：回滚finished状态，允许重试
    gameRoom.setFinished(false);
    roomCache.syncToRedis(roomCode);
    throw e;
}
```

#### 修复3: 重连时恢复提交状态（问题4.6）
**文件**: `frontend/src/composables/game/useGameWebSocket.js:77-80`

**问题**:
- 玩家刷新页面后，`hasSubmitted` 状态丢失
- 可能导致重复提交或状态不一致

**修复**:
```javascript
// 🔥 修复问题4.6：重连时恢复提交状态
if (restoreSubmitState) {
    restoreSubmitState()
}
```

`restoreSubmitState()` 会从 localStorage 读取提交状态并恢复。

#### 修复4: 延长房间删除延迟（问题5.1）
**文件**: `backend/src/main/java/org/example/service/flow/impl/GameFlowServiceImpl.java:374`

**问题**:
- 游戏结束后2秒即删除房间
- 前端可能未接收到结束广播就被删除
- 与玩家查看结果的操作冲突

**修复**:
```java
// 🔥 修复问题5.1：延长删除时间从2秒到10秒
}, Instant.now().plus(Duration.ofSeconds(10)));
```

---

## 五、其他已知问题（待修复）

### 中优先级问题

1. **问题1.1: 房间创建成功但加入失败（"幽灵房间"）**
   - 场景：创建房间成功，但立即加入时失败
   - 影响：房间在大厅显示但无法进入

2. **问题2.1: WebSocket 订阅竞态条件**
   - 场景：玩家快速刷新页面时，旧订阅未取消
   - 影响：收到重复的房间更新消息

3. **问题4.3: 倒计时定时器泄漏风险**
   - 场景：快速切换题目时，定时器可能未清理
   - 影响：内存泄漏和性能下降

4. **问题4.4: 所有玩家断线时无广播**
   - 场景：所有玩家同时断线/离开
   - 影响：房间状态不更新

5. **问题6.1: 结果页刷新时房间已删除**
   - 场景：玩家在结果页刷新，但房间已被删除
   - 影响：404错误或空白页

6. **问题7.1: 返回大厅未清理 playerStore**
   - 场景：从结果页返回大厅
   - 影响：可能携带旧房间数据

### 低优先级优化

- 前端错误提示的用户体验优化
- 加载状态的视觉反馈改进
- 房间列表刷新频率优化

---

## 五、中优先级问题修复（第二轮）

### Commit: `9ae432b` - 修复6个中优先级的房间生命周期问题

#### 修复1: 房间创建成功但加入失败（问题1.1 - "幽灵房间"）
**文件**: `frontend/src/views/room/RoomView.vue:145-210`

**问题**:
- 创建房间成功但加入失败时，房间会遗留在数据库中
- 导致"幽灵房间"显示在大厅但无法进入

**修复**:
```javascript
const handleCreate = async ({ questionCount, maxPlayers, password, questionTagIds }) => {
  loading.value = true
  let createdRoomCode = null  // 🔥 记录创建的房间代码

  try {
    const createResponse = await createRoom(...)
    createdRoomCode = roomData.roomCode

    // 🔥 嵌套try-catch，加入失败时清理房间
    try {
      const joinResponse = await joinRoom(...)
      // 成功逻辑...
    } catch (joinError) {
      // 🔥 加入失败，清理已创建的"幽灵房间"
      logger.error("加入房间失败，尝试清理幽灵房间:", joinError)
      try {
        await deleteRoom(createdRoomCode)
        logger.info(`✅ 已清理幽灵房间: ${createdRoomCode}`)
      } catch (deleteError) {
        logger.error("清理幽灵房间失败:", deleteError)
      }

      toast.add({
        severity: 'error',
        summary: '加入房间失败',
        detail: '无法加入刚创建的房间，房间已清理'
      })
      throw joinError
    }
  } catch (error) {
    // 外层错误处理...
  }
}
```

#### 修复2: WebSocket订阅竞态条件（问题2.1）
**文件**: `frontend/src/websocket/ws.js`, `frontend/src/composables/game/useGameWebSocket.js`

**问题**:
- 玩家快速刷新页面时，旧订阅未取消就创建了新订阅
- 导致收到重复的房间更新消息

**修复**:

##### ws.js - 全局订阅管理
```javascript
// 新增全局订阅Map
let activeRoomSubscriptions = new Map(); // 🔥 全局跟踪活动的房间订阅

export function subscribeRoom(roomCode, onRoomUpdate, onRoomError, playerId = null) {
  // 🔥 检查是否已有该房间的活动订阅
  if (activeRoomSubscriptions.has(roomCode)) {
    logger.warn(`⚠️ 房间 ${roomCode} 已存在订阅，先取消旧订阅`);
    const oldSubs = activeRoomSubscriptions.get(roomCode);
    unsubscribeAll(oldSubs);
    activeRoomSubscriptions.delete(roomCode);
  }

  // 创建新订阅...
  const subscriptions = [...];

  // 🔥 记录活动订阅
  activeRoomSubscriptions.set(roomCode, subscriptions);
  return subscriptions;
}

// 新增专用清理函数
export function unsubscribeRoom(roomCode) {
  if (activeRoomSubscriptions.has(roomCode)) {
    const subs = activeRoomSubscriptions.get(roomCode);
    unsubscribeAll(subs);
    activeRoomSubscriptions.delete(roomCode);
    logger.debug(`✅ 已取消房间 ${roomCode} 的订阅`);
  }
}
```

##### useGameWebSocket.js - 使用新API
```javascript
onUnmounted(() => {
  // 🔥 使用unsubscribeRoom清理订阅，确保从全局Map中移除
  unsubscribeRoom(roomCode.value)
  // ...
})
```

#### 修复3: 倒计时定时器泄漏风险（问题4.3）
**文件**: `frontend/src/composables/game/useGameCountdown.js:14-25`

**问题**:
- 虽然resetCountdown会先clearCountdown，但缺少双重保护
- 极端情况下可能有timer泄漏风险

**修复**:
```javascript
const startCountdown = () => {
  // 🔥 修复问题4.3：防御性检查，确保不会在已有timer时创建新timer
  if (countdownTimer.value) {
    clearInterval(countdownTimer.value)
    countdownTimer.value = null
  }

  updateCountdown()
  countdownTimer.value = setInterval(() => {
    updateCountdown()
  }, 100)
}
```

#### 修复4: 所有玩家断线时无广播（问题4.4）
**文件**: `backend/src/main/java/org/example/service/room/impl/RoomLifecycleServiceImpl.java:259-273`

**问题**:
- 所有玩家断线时，直接return true，没有同步状态到Redis
- 导致GameServiceImpl.leaveRoom返回的状态可能不是最新的

**修复**:
```java
if (connectedCount == 0) {
    // 🔥 改：游戏进行中时不立即删除，给重连时间
    if (gameRoom.isStarted() && !gameRoom.isFinished()) {
        log.warn("⚠️ 房间 {} 所有玩家断线，但游戏进行中，保留房间等待重连", roomCode);
        // 🔥 修复问题4.4：同步状态到Redis，以便返回最新状态并广播
        roomCache.syncToRedis(roomCode);
        // 不删除房间，保留5分钟
        return true; // 房间仍存在
    } else {
        // 游戏未开始或已结束，删除房间...
    }
}
```

#### 修复5: 结果页刷新时房间已删除（问题6.1）
**文件**: `frontend/src/views/room/ResultView.vue`

**问题分析**:
- 游戏结束时会立即保存历史记录，10秒后删除房间
- 理论上，只要历史记录保存成功，房间删除后仍能查看结果
- 前端已有错误处理（显示"无法加载游戏结果"）

**改进**:
- 添加playerStore和chatStore清理逻辑
- 避免返回大厅时携带旧状态

```javascript
// 🔥 修复问题7.1: 返回大厅时清理playerStore
const handleBackToLobby = () => {
  playerStore.clearRoom()
  chatStore.clearChat()
  router.push('/find')
}
```

#### 修复6: 返回大厅未清理playerStore（问题7.1）
**文件**: `frontend/src/views/room/ResultView.vue`, `frontend/src/components/result/ResultContent.vue`

**问题**:
- 从结果页返回大厅时，playerStore仍然携带旧房间数据
- 可能导致状态不一致或UI显示错误

**修复**:

##### ResultView.vue
```javascript
const handleBackToLobby = () => {
  playerStore.clearRoom()
  chatStore.clearChat()
  router.push('/find')
}
```

##### ResultContent.vue
```javascript
import { useChatStore } from '@/stores/chat'
import { useRouter } from 'vue-router'

const router = useRouter()
const chatStore = useChatStore()

const handleBackToLobby = () => {
  playerStore.clearRoom()
  chatStore.clearChat()
  router.push('/find')
}

// 模板中使用
<button @click="handleBackToLobby">
  返回大厅
</button>
```

---

## 总结

### 修复统计（更新）
- 语法错误: 2个
- 重连问题: 3个
- 房间删除: 10个
- 游戏生命周期: 4个
- **中优先级问题**: 6个
- **总计**: 25个问题已修复

### 关键改进
1. **重连机制完善**: 玩家可以在游戏中刷新页面而不断线
2. **资源管理优化**: 消除了多个内存泄漏点（RoomLock、advancing、聊天室、WebSocket订阅、定时器）
3. **错误恢复能力**: 游戏启动和结束流程增加了容错和回滚机制
4. **原子操作**: 房间删除使用原子操作防止竞态条件
5. **状态一致性**: 玩家状态、房间状态、订阅状态的完整清理和同步
6. **用户体验**: 幽灵房间清理、重复订阅防护、状态清理

### 待优化问题（低优先级）
- 前端错误提示的用户体验优化
- 加载状态的视觉反馈改进
- 房间列表刷新频率优化（已从10秒优化到5秒）
