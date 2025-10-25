# Redis 重启恢复功能测试指南

本文档说明如何测试 Redis 缓存优化后的服务器重启恢复功能。

---

## 🎯 测试目标

验证以下场景：
1. ✅ 服务器重启后，游戏房间从 Redis 自动恢复
2. ✅ 玩家进度、分数、状态完整保留
3. ✅ 玩家可以无感继续游戏

---

## 📋 前置准备

### 1. 启动所有服务
```bash
# 启动 MySQL 和 Redis
docker-compose up -d

# 启动后端（开发模式）
cd backend
mvn spring-boot:run
```

### 2. 验证服务状态
```bash
# 检查 Redis 是否运行
docker ps | grep redis

# 测试 Redis 连接
docker exec -it game-redis redis-cli ping
# 应该返回：PONG
```

---

## 🧪 测试步骤

### 场景 1：基础重启恢复

#### Step 1: 创建房间并开始游戏
1. 打开浏览器访问 `http://localhost:5173`
2. 登录账号（或注册新账号）
3. 创建房间（房间码记为 `ABC123`）
4. 加入玩家（至少 2 个玩家）
5. 开始游戏
6. 答到第 3 题（记录当前分数）

**记录信息：**
```
房间码：ABC123
玩家1：player1（分数：150）
玩家2：player2（分数：120）
当前题目：第3题
```

#### Step 2: 检查 Redis 中的数据
```bash
# 连接 Redis
docker exec -it game-redis redis-cli

# 查看房间是否存在
KEYS game:room:*

# 查看房间详情（替换为实际房间码）
GET game:room:ABC123

# 退出 Redis CLI
exit
```

**预期结果：**
- 能看到 `game:room:ABC123` 键
- 内容包含完整的游戏状态（JSON格式）

#### Step 3: 重启 Spring Boot 服务
```bash
# 在运行 mvn spring-boot:run 的终端按 Ctrl+C
^C

# 等待 2-3 秒，确保服务完全停止

# 重新启动
mvn spring-boot:run
```

**观察日志：**
启动后应该看到 Spring Boot 正常启动的日志。

#### Step 4: 玩家重新连接
1. 在浏览器中刷新页面
2. 点击"加入房间"，输入房间码 `ABC123`
3. 观察是否能正常加入

**预期结果：**
- ✅ 玩家成功加入房间
- ✅ 房间显示"第3题"
- ✅ 分数显示为之前的分数（150, 120）
- ✅ 可以继续答题

**后端日志验证：**
```
🔄 从 Redis 恢复房间: ABC123
✅ 玩家 player1 重连房间 ABC123，离线时长: XX秒
```

---

### 场景 2：中途答题重启

#### Step 1: 游戏进行中
1. 在第 5 题时，部分玩家已提交答案
2. 记录哪些玩家已提交

**记录：**
```
第5题
已提交：player1（选择 A）
未提交：player2
```

#### Step 2: 重启服务
```bash
# Ctrl+C 停止
# 重新启动
mvn spring-boot:run
```

#### Step 3: 验证恢复
1. 玩家刷新页面并重新加入
2. 检查第5题的提交状态

**预期结果：**
- ✅ player1 的提交记录保留（显示已提交）
- ✅ player2 仍可提交答案
- ✅ 题目倒计时重新开始

---

### 场景 3：断线重连

#### Step 1: 模拟玩家断线
1. 游戏进行中
2. 玩家关闭浏览器标签（不是退出游戏）

#### Step 2: 等待一段时间
等待 10-30 秒

#### Step 3: 玩家重新打开
1. 重新访问 `http://localhost:5173`
2. 点击"加入房间"，输入房间码

**预期结果：**
- ✅ 自动识别为重连
- ✅ 恢复到当前题目
- ✅ 分数保留

**后端日志：**
```
✅ 玩家 player1 重连房间 ABC123，离线时长: 25秒
🔄 房间 ABC123 已同步到 Redis
```

---

### 场景 4：Redis 故障降级

#### Step 1: 停止 Redis
```bash
docker-compose stop redis
```

#### Step 2: 创建新房间
1. 尝试创建房间并开始游戏

**预期结果：**
- ✅ 游戏正常运行（降级为本地缓存）
- ✅ 后端日志显示：
```
❌ Redis 写入失败（roomCode=XXX），降级为本地缓存
```

#### Step 3: 重启服务
```bash
# 停止后端
# 重新启动
mvn spring-boot:run
```

**预期结果：**
- ❌ Redis 停止时的房间数据无法恢复（预期行为）
- ✅ 服务正常启动，不会因为 Redis 故障而崩溃

#### Step 4: 恢复 Redis
```bash
docker-compose start redis
```

后续创建的房间会重新启用 Redis 缓存。

---

## 🔍 验证要点

### 后端日志关键字
```bash
# 成功写入 Redis
✅ 房间 ABC123 已加入双层缓存（L1+Redis）

# 从 Redis 恢复
🔄 从 Redis 恢复房间: ABC123

# 同步到 Redis
🔥 同步到 Redis

# Redis 故障降级
❌ Redis 写入失败（roomCode=XXX），降级为本地缓存
```

### Redis 数据检查
```bash
# 查看所有房间键
redis-cli KEYS "game:room:*"

# 查看房间 TTL（剩余过期时间，单位：秒）
redis-cli TTL game:room:ABC123
# 应该返回接近 1800（30分钟）

# 查看房间内容
redis-cli GET game:room:ABC123
# 应该返回 JSON 格式的完整游戏状态
```

### 前端验证
- 玩家列表显示正确
- 当前题目索引正确
- 分数显示正确
- 已提交状态正确
- 可以继续答题

---

## ❌ 常见问题排查

### 问题 1：重启后无法恢复房间
**症状：** 玩家加入提示"房间不存在"

**排查步骤：**
1. 检查 Redis 是否运行
```bash
docker ps | grep redis
```

2. 检查 Redis 中是否有数据
```bash
redis-cli KEYS "game:room:*"
```

3. 检查后端日志是否有错误
```bash
# 搜索关键字
grep -i "redis" backend.log
```

**解决方案：**
- 如果 Redis 停止：`docker-compose start redis`
- 如果数据已过期：Redis TTL 为 30 分钟，超时自动删除
- 如果序列化错误：检查是否修改了 GameRoom 结构

---

### 问题 2：恢复后分数不对
**症状：** 分数与重启前不一致

**排查：**
1. 检查是否在重启前同步了 Redis
```bash
# 搜索日志
grep "同步到 Redis" backend.log
```

2. 检查 Redis 数据
```bash
redis-cli GET game:room:ABC123 | jq '.scores'
```

**解决方案：**
确保在关键操作后调用了 `roomCache.syncToRedis(roomCode)`

---

### 问题 3：重启后 WebSocket 连接失败
**症状：** 玩家无法重新连接 WebSocket

**排查：**
1. 检查 WebSocket 端点是否正常
```bash
curl http://localhost:8080/ws/info
```

2. 检查浏览器控制台错误

**解决方案：**
- 确保后端完全启动后再连接
- 检查 CORS 配置
- 清除浏览器缓存

---

## ✅ 测试通过标准

所有以下场景都能正常工作：
- [x] 创建房间后重启，房间数据完整恢复
- [x] 游戏进行中重启，玩家可以继续答题
- [x] 玩家断线重连，状态正确恢复
- [x] Redis 故障时降级为本地缓存
- [x] 提交答案后重启，答案记录保留
- [x] 多玩家同时重连，无冲突

---

## 📊 性能验证

### 缓存命中率测试
```bash
# 启动 10 个房间，每个房间 5 次操作
# 观察后端日志中 "从 Redis 恢复" 的次数

# 预期：
# - 本地缓存命中率 > 95%
# - Redis 读取次数 < 5%
```

### 重启恢复时间
```bash
# 记录时间
# 1. 停止服务时间
# 2. 启动完成时间
# 3. 玩家重连成功时间

# 预期：
# - 服务启动 < 30 秒
# - 玩家重连 < 5 秒
```

---

## 📝 测试报告模板

```markdown
## Redis 重启恢复测试报告

**测试日期：** YYYY-MM-DD
**测试人员：** XXX
**版本：** vX.X.X

### 测试结果

| 场景 | 结果 | 备注 |
|------|------|------|
| 基础重启恢复 | ✅ 通过 | 房间完整恢复 |
| 中途答题重启 | ✅ 通过 | 答案记录保留 |
| 断线重连 | ✅ 通过 | 25秒后成功重连 |
| Redis 故障降级 | ✅ 通过 | 自动降级到本地缓存 |

### 问题记录
- 无

### 建议
- 建议将 Redis TTL 延长至 60 分钟
```

---

## 🎉 测试完成

如果所有场景都通过，说明 Redis 缓存优化功能正常，可以上线！
