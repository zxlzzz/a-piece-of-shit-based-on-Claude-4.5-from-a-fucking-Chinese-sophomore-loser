package org.example.service.room.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.GameController;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.RoomDTO;
import org.example.entity.PlayerEntity;
import org.example.entity.RoomEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.pojo.RoomStatus;
import org.example.repository.PlayerRepository;
import org.example.repository.RoomRepository;
import org.example.service.cache.RoomCache;
import org.example.service.chat.ChatRoomManager;
import org.example.pojo.GameMode;
import org.example.service.room.RoomLifecycleService;
import org.example.service.timer.QuestionTimerService;
import org.example.utils.RoomLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间生命周期服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomLifecycleServiceImpl implements RoomLifecycleService {

    private final RoomRepository roomRepository;
    private final PlayerRepository playerRepository;
    private final RoomCache roomCache;
    private final ObjectMapper objectMapper;
    private final QuestionTimerService timerService;  // 🔥 P1-2: 用于取消定时器
    private final ChatRoomManager chatRoomManager;  // 🔥 用于清理聊天室

    @Override
    @Transactional
    public RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit, String password, GameRoom gameRoom) {
        return initializeRoom(maxPlayers, questionCount, gameRoom, timeLimit, password, null, null);
    }

    @Transactional
    @Override
    public RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, GameRoom gameRoom, Integer timeLimit, String password, java.util.List<Long> questionTagIds, GameMode gameMode) {
        String roomCode = generateRoomCode();

        // 🔥 序列化标签IDs
        String questionTagIdsJson = null;
        if (questionTagIds != null && !questionTagIds.isEmpty()) {
            try {
                questionTagIdsJson = objectMapper.writeValueAsString(questionTagIds);
            } catch (Exception e) {
                log.error("序列化questionTagIds失败", e);
            }
        }

        GameMode resolvedGameMode = gameMode != null ? gameMode : GameMode.SYNCHRONIZED;

        // 🔥 创建房间实体（只有基础字段）
        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                .timeLimit(timeLimit != null ? timeLimit : 30)
                .password(password != null && !password.trim().isEmpty() ? password : null)
                // 🔥 高级规则使用默认值
                .gameMode(resolvedGameMode)
                .rankingMode("standard")
                .targetScore(null)
                .winConditionsJson(null)
                .questionTagIdsJson(questionTagIdsJson)
                .build();

        RoomEntity savedRoom = roomRepository.save(roomEntity);

        // 初始化内存房间
        gameRoom.setRoomCode(roomCode);
        gameRoom.setGameMode(resolvedGameMode);
        gameRoom.setMaxPlayers(maxPlayers);
        gameRoom.setPlayers(new ArrayList<>());
        gameRoom.setQuestions(new ArrayList<>());
        gameRoom.setStarted(false);
        gameRoom.setFinished(false);
        gameRoom.setCurrentIndex(-1);
        gameRoom.setSubmissions(new ConcurrentHashMap<>());
        gameRoom.setScores(new ConcurrentHashMap<>());
        gameRoom.setDisconnectedPlayers(new ConcurrentHashMap<>());
        gameRoom.setPlayerGameStates(new ConcurrentHashMap<>());
        gameRoom.setRoomEntity(savedRoom); // 🔥 性能优化：缓存 RoomEntity，避免后续频繁查询数据库

        return savedRoom;
    }

    @Override
    @Transactional
    public void handleJoin(String roomCode, String playerId, String playerName, Boolean spectator, String password) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            // 检查房间密码（观战者不需要密码）
            // 🔥 修复：trim()避免空格绕过，检查空字符串
            if (!spectator && room.getPassword() != null && !room.getPassword().trim().isEmpty()) {
                if (!room.getPassword().equals(password)) {
                    throw new BusinessException("房间密码错误");
                }
            }

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
                    gameRoom.getDisconnectedPlayers().remove(playerId);
                    roomCache.syncToRedis(roomCode, gameRoom);
                }

                return; // 跳过后续加入逻辑
            }

            // 🔥 检查房间是否已满（观战者不计入人数）
            if (!spectator) {  // 非观战者才检查容量
                long nonSpectatorCount = gameRoom.getPlayers().stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getSpectator()))
                        .count();
                if (nonSpectatorCount >= room.getMaxPlayers()) {
                    throw new BusinessException("房间已满");
                }
            }

            // 检查玩家是否已在房间内
            boolean playerExists = gameRoom.getPlayers().stream()
                    .anyMatch(p -> p.getPlayerId().equals(playerId));

            if (!playerExists) {
                // 🔥 修改：必须从数据库查找已登录的玩家
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElseThrow(() -> new BusinessException("玩家不存在，请先登录"));

                // 🔥 改：直接设置房间和准备状态
                player.setRoom(room);
                player.setReady(false);
                player.setSpectator(spectator != null && spectator);  // 设置观战模式

                playerRepository.save(player);

                PlayerDTO playerDTO = PlayerDTO.builder()
                        .playerId(playerId)
                        .name(playerName)
                        .score(0)
                        .ready(false)
                        .spectator(spectator != null && spectator)  // 设置观战模式
                        .build();

                // 🔥 测试房间：真实玩家插入到第一位（成为房主）
                if (gameRoom.isTestRoom()) {
                    gameRoom.getPlayers().add(0, playerDTO);
                } else {
                    gameRoom.getPlayers().add(playerDTO);
                }

                gameRoom.getScores().put(playerId, 0);

                // 🔥 同步到 Redis
                roomCache.syncToRedis(roomCode, gameRoom);
            } else {
                // 🔥 修复问题3：玩家已存在，检查是否在断线列表中
                if (gameRoom.getDisconnectedPlayers().containsKey(playerId)) {
                    gameRoom.getDisconnectedPlayers().remove(playerId);
                    roomCache.syncToRedis(roomCode, gameRoom);
                }

            }
        }
    }

    @Override
    @Transactional
    public boolean handleLeave(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            PlayerDTO leavingPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            String playerName = leavingPlayer != null ? leavingPlayer.getName() : "未知玩家";

            if (!gameRoom.isStarted()) {
                // 游戏未开始：检查是否房主离开
                boolean isRoomOwner = !gameRoom.getPlayers().isEmpty() &&
                        gameRoom.getPlayers().get(0).getPlayerId().equals(playerId);

                if (isRoomOwner) {
                    // 🔥 房主离开，使用原子删除方法
                    deleteRoomAtomically(roomCode, gameRoom);
                    return false; // 房间已解散
                } else {
                    // 普通玩家离开
                    gameRoom.getPlayers().removeIf(p -> p.getPlayerId().equals(playerId));
                    gameRoom.getScores().remove(playerId);

                    PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
                    if (player != null) {
                        player.setRoom(null);
                        playerRepository.save(player);
                    }


                    // 🔥 同步到 Redis
                    roomCache.syncToRedis(roomCode, gameRoom);
                }

            } else {
                // 游戏进行中：标记断线

                long connectedCount = gameRoom.getPlayers().stream()
                        .filter(p -> !gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()))
                        .count();

                if (connectedCount == 0) {
                    // 🔥 改：游戏进行中时不立即删除，给重连时间
                    if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                        log.warn("⚠️ 房间 {} 所有玩家断线，但游戏进行中，保留房间等待重连", roomCode);
                        // 🔥 修复问题4.4：同步状态到Redis，以便返回最新状态并广播
                        roomCache.syncToRedis(roomCode, gameRoom);
                        // 不删除房间，保留5分钟
                        return true; // 房间仍存在
                    } else {
                        // 🔥 游戏未开始或已结束，使用原子删除方法
                        deleteRoomAtomically(roomCode, gameRoom);
                        return false; // 房间已解散
                    }
                }

                // 🔥 游戏进行中标记断线，同步到 Redis
                roomCache.syncToRedis(roomCode, gameRoom);
            }

            return true; // 房间仍存在
        }
    }

    @Override
    @Transactional
    public void handleReconnect(String roomCode, String playerId) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            LocalDateTime disconnectTime = gameRoom.getDisconnectedPlayers().remove(playerId);

            if (disconnectTime != null) {
                long offlineSeconds = java.time.Duration.between(disconnectTime, LocalDateTime.now()).getSeconds();

                // 🔥 P1-2: 游戏进行中重连
                // 注意：不在这里重启后端定时器，而是依赖前端 countdown
                // 当前端倒计时结束时会调用 handleAutoSubmit，自动提交答案并推进游戏
                if (gameRoom.isStarted() && !gameRoom.isFinished() && gameRoom.getCurrentQuestion() != null) {
                }

                // 🔥 添加：如果游戏已结束，重连时重置房间过期时间
                if (gameRoom.isFinished()) {
                    // 给房间续期（重新计时5分钟）
                    // 这里可以通过 RoomCache 添加续期机制
                }
            } else {
                log.warn("⚠️ 玩家 {} 重连房间 {}，但未找到断线记录", playerId, roomCode);
            }

            // 🔥 同步到 Redis
            roomCache.syncToRedis(roomCode, gameRoom);
        }
    }

    @Override
    @Transactional
    public void updateSettings(String roomCode, GameController.UpdateRoomSettingsRequest request) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            // 校验：游戏未开始
            if (gameRoom.isStarted()) {
                throw new BusinessException("游戏已开始，无法修改设置");
            }

            // 更新题目数量（可选）
            if (request.getQuestionCount() != null && request.getQuestionCount() > 0) {
                room.setQuestionCount(request.getQuestionCount());
            }

            // 更新每题时长（可选）
            if (request.getTimeLimit() != null && request.getTimeLimit() >= 20 && request.getTimeLimit() <= 120) {
                room.setTimeLimit(request.getTimeLimit());
            }

            // 更新聊天室开关
            if (request.getChatEnabled() != null) {
                room.setChatEnabled(request.getChatEnabled());
            }

            // 更新私聊开关
            if (request.getPrivateChatEnabled() != null) {
                room.setPrivateChatEnabled(request.getPrivateChatEnabled());
            }

            // 更新游戏模式
            if (request.getGameMode() != null) {
                room.setGameMode(request.getGameMode());
                gameRoom.setGameMode(request.getGameMode());
            }

            // 更新排名模式
            if (request.getRankingMode() != null) {
                room.setRankingMode(request.getRankingMode());
            }

            // 更新目标分数
            room.setTargetScore(request.getTargetScore());

            // 更新通关条件
            String winConditionsJson = null;
            if (request.getWinConditions() != null) {
                try {
                    winConditionsJson = objectMapper.writeValueAsString(request.getWinConditions());
                } catch (Exception e) {
                    log.error("序列化通关条件失败", e);
                    throw new BusinessException("通关条件格式错误");
                }
            }
            room.setWinConditionsJson(winConditionsJson);

            // 更新题目标签筛选
            String questionTagIdsJson = null;
            if (request.getQuestionTagIds() != null) {
                try {
                    questionTagIdsJson = objectMapper.writeValueAsString(request.getQuestionTagIds());
                } catch (Exception e) {
                    log.error("序列化题目标签失败", e);
                    throw new BusinessException("题目标签格式错误");
                }
            }
            room.setQuestionTagIdsJson(questionTagIdsJson);

            // 保存到数据库
            RoomEntity savedRoom = roomRepository.save(room);

            // 🔥 性能优化：更新缓存的 RoomEntity
            gameRoom.setRoomEntity(savedRoom);

        }
    }

    @Override
    @Transactional
    public void setPlayerReady(String roomCode, String playerId, boolean ready) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间不存在");
        }

        // 🔥 测试房间中的Bot玩家：只更新内存，不操作数据库
        if (playerId.startsWith("BOT_")) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));

            // 同步到 Redis
            roomCache.syncToRedis(roomCode, gameRoom);
            return;
        }

        // 🔥 真实玩家：更新数据库 + 内存
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        if (!player.getRoom().getRoomCode().equals(roomCode)) {
            throw new BusinessException("玩家不在该房间中");
        }

        player.setReady(ready);
        playerRepository.save(player);

        gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(ready));

        // 🔥 同步到 Redis
        roomCache.syncToRedis(roomCode, gameRoom);

        // 🔥 检查是否所有玩家都准备好了
        long totalPlayers = gameRoom.getPlayers().stream()
            .filter(p -> !Boolean.TRUE.equals(p.getSpectator()))
            .count();
        long readyPlayers = gameRoom.getPlayers().stream()
            .filter(p -> !Boolean.TRUE.equals(p.getSpectator()))
            .filter(PlayerDTO::getReady)
            .count();
    }

    @Override
    public RoomDTO toRoomDTO(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 性能优化：优先使用 GameRoom 中缓存的 RoomEntity，避免频繁数据库查询
        RoomEntity roomEntity = gameRoom.getRoomEntity();
        if (roomEntity == null) {
            // 缓存失效或首次访问，从数据库查询并缓存
            roomEntity = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("房间不存在"));
            gameRoom.setRoomEntity(roomEntity);
        }

        return toRoomDTO(roomEntity, gameRoom);
    }

    @Transactional
    @Override
    public void handlePlayerDisconnect(String roomCode, String playerId) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) {
            log.warn("⚠️ 房间 {} 不存在，跳过断线处理", roomCode);
            return;
        }

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            // 🔥 标记断线时间
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            String playerName = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .map(PlayerDTO::getName)
                    .findFirst()
                    .orElse("未知玩家");


            // 🔥 如果游戏进行中且所有非观战玩家都断线，自动推进
            if (gameRoom.isStarted() && gameRoom.getCurrentQuestion() != null) {
                boolean allDisconnected = gameRoom.getPlayers().stream()
                        .filter(p -> !Boolean.TRUE.equals(p.getSpectator())) // 排除观战者
                        .allMatch(p -> gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()));

                if (allDisconnected) {
                    log.warn("❌ 房间 {} 所有非观战玩家都断开连接", roomCode);
                    // 🔥 P1-2: 取消定时器，避免幽灵定时器在无人状态下触发
                    timerService.cancelTimeout(roomCode);
                }
            }
        }
    }

    @Override
    @Transactional
    public void removeDisconnectedPlayer(String roomCode, String playerId) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) {
            log.warn("⚠️ 房间 {} 不存在，跳过移除操作", roomCode);
            return;
        }

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            // 🔥 添加：如果游戏进行中，不移除玩家，只保持断线状态
            if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                // 不执行移除操作，保持在 disconnectedPlayers 列表中
                return;
            }

            // 🔥 只有在游戏未开始或已结束时才真正移除

            // 从断线列表移除
            gameRoom.getDisconnectedPlayers().remove(playerId);

            // 从玩家列表移除
            PlayerDTO removedPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            if (removedPlayer != null) {
                gameRoom.getPlayers().remove(removedPlayer);
            }

            // 清理分数
            gameRoom.getScores().remove(playerId);

            // 清理玩家状态
            gameRoom.getPlayerGameStates().remove(playerId);

            // 更新数据库
            PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
            if (player != null) {
                player.setRoom(null);
                player.setReady(false);
                playerRepository.save(player);
            }

            // 🔥 检查是否房间为空，使用原子删除方法（问题8）
            if (gameRoom.getPlayers().isEmpty()) {
                log.warn("🏠 房间 {} 所有玩家都已离开，准备解散", roomCode);
                deleteRoomAtomically(roomCode, gameRoom);
            }
        }
    }

    private RoomDTO toRoomDTO(RoomEntity roomEntity, GameRoom gameRoom) {
        RoomStatus status = RoomStatus.WAITING;
        if (gameRoom.isFinished()) {
            status = RoomStatus.FINISHED;
        } else if (gameRoom.isStarted()) {
            status = RoomStatus.PLAYING;
        }

        // 🔥 直接使用 DTO（无需转换）
        QuestionDTO currentQuestionDTO = gameRoom.getCurrentQuestion();

        Integer questionCount = null;
        if (gameRoom.getQuestions() != null && !gameRoom.getQuestions().isEmpty()) {
            questionCount = gameRoom.getQuestions().size();
        } else if (roomEntity != null && roomEntity.getQuestionCount() != null) {
            questionCount = roomEntity.getQuestionCount();
        } else {
            questionCount = 10;
        }

        // 解析 winConditionsJson
        RoomDTO.WinConditions winConditions = null;
        if (roomEntity != null && roomEntity.getWinConditionsJson() != null) {
            try {
                winConditions = objectMapper.readValue(
                        roomEntity.getWinConditionsJson(),
                        RoomDTO.WinConditions.class
                );
            } catch (Exception e) {
                log.error("解析通关条件失败", e);
            }
        }

        // 🔥 计算非观战者人数
        int currentNonSpectators = (int) gameRoom.getPlayers().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getSpectator()))
                .count();

        // 🔥 P1-1: 获取当前题目的已提交玩家ID列表（用于前端验证）
        java.util.List<String> submittedPlayerIds = new ArrayList<>();
        if (gameRoom.isStarted() && gameRoom.getCurrentIndex() >= 0) {
            Map<String, String> currentSubmissions = gameRoom.getSubmissions().get(gameRoom.getCurrentIndex());
            if (currentSubmissions != null) {
                submittedPlayerIds = new ArrayList<>(currentSubmissions.keySet());
            }
        }

        // ASYNC 模式：携带全量题目 + 各玩家进度
        boolean isAsync = gameRoom.getGameMode() == org.example.pojo.GameMode.ASYNC;
        java.util.List<org.example.dto.QuestionDTO> allQuestions =
                (isAsync && gameRoom.getQuestions() != null) ? new ArrayList<>(gameRoom.getQuestions()) : null;
        java.util.Map<String, Integer> playerProgressSnapshot =
                (isAsync && !gameRoom.getPlayerProgress().isEmpty())
                        ? new java.util.HashMap<>(gameRoom.getPlayerProgress()) : null;

        return RoomDTO.builder()
                .roomCode(gameRoom.getRoomCode())
                .maxPlayers(gameRoom.getMaxPlayers() != null ? gameRoom.getMaxPlayers() :
                        (roomEntity != null ? roomEntity.getMaxPlayers() : gameRoom.getPlayers().size()))
                .currentPlayers(currentNonSpectators)  // 🔥 只计算非观战者
                .status(status)
                .finished(gameRoom.isFinished())  // 🔥 添加 finished 字段
                .players(new ArrayList<>(gameRoom.getPlayers()))
                .questionStartTime(gameRoom.getQuestionStartTime())
                .timeLimit(gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() :
                        (roomEntity != null && roomEntity.getTimeLimit() != null ? roomEntity.getTimeLimit() : 30))
                .currentIndex(gameRoom.getCurrentIndex())
                .currentQuestion(currentQuestionDTO)  // ✅ 直接使用
                .questionCount(questionCount)
                .hasPassword(roomEntity != null && roomEntity.getPassword() != null && !roomEntity.getPassword().isEmpty())
                .submittedPlayerIds(submittedPlayerIds)  // 🔥 P1-1: 已提交玩家列表
                .gameMode(roomEntity != null ? roomEntity.getGameMode() : org.example.pojo.GameMode.SYNCHRONIZED)
                .questions(allQuestions)          // ASYNC 全量题目
                .playerProgress(playerProgressSnapshot) // ASYNC 各玩家进度
                .rankingMode(roomEntity != null ? roomEntity.getRankingMode() : "standard")
                .targetScore(roomEntity != null ? roomEntity.getTargetScore() : null)
                .winConditions(winConditions)
                .chatEnabled(roomEntity != null ? roomEntity.getChatEnabled() : true)
                .privateChatEnabled(roomEntity != null ? roomEntity.getPrivateChatEnabled() : true)  // 🔥 是否启用私聊
                .build();
    }

    @Override
    @Transactional
    public void deleteRoom(String roomCode) {
        GameRoom gameRoom = roomCache.get(roomCode);
        deleteRoomAtomically(roomCode, gameRoom);
    }

    // ==================== 私有方法 ====================

    /**
     * 🔥 原子删除房间（修复问题1/2/3/4/5/7 + P0-6）
     * - 问题1: 真正删除数据库记录，而不是只标记FINISHED
     * - 问题2: 清理所有关联的玩家记录
     * - 问题3: 主动清理聊天室，不等待5分钟定时任务
     * - 问题4: 统一删除方法，确保通知只发送一次
     * - 问题5: 使用带重试的缓存删除
     * - 问题7: 原子操作，防止并发竞态条件
     * - P0-6: 清理RoomLock，防止锁对象泄漏
     *
     * @param roomCode 房间代码
     * @param gameRoom 内存中的房间对象（可选，如果已经获取）
     * @return 被删除的房间实体（用于发送删除通知）
     */
    @Transactional
    protected RoomEntity deleteRoomAtomically(String roomCode, GameRoom gameRoom) {
        RoomEntity room;

        // 🔥 使用RoomLock确保原子性（问题7）
        synchronized (RoomLock.getLock(roomCode)) {
            // 1. 查询房间实体
            room = roomRepository.findByRoomCode(roomCode).orElse(null);
            if (room == null) {
                log.warn("⚠️ 房间 {} 已不存在，跳过删除", roomCode);
                RoomLock.removeLock(roomCode); // 清理锁
                return null;
            }

            // 2. 检查房间状态，防止重复删除（问题7）
            if (room.getStatus() == RoomStatus.FINISHED && room.getId() == null) {
                log.warn("⚠️ 房间 {} 已处于删除状态，跳过重复删除", roomCode);
                RoomLock.removeLock(roomCode); // 清理锁
                return null;
            }


            // 3. 清理所有关联的玩家记录（问题2）
            if (gameRoom != null) {
                for (PlayerDTO player : gameRoom.getPlayers()) {
                    String playerId = player.getPlayerId();
                    // Bot玩家不在数据库中，跳过
                    if (!playerId.startsWith("BOT_")) {
                        PlayerEntity playerEntity = playerRepository.findByPlayerId(playerId).orElse(null);
                        if (playerEntity != null) {
                            playerEntity.setRoom(null);
                            playerEntity.setReady(false);
                            playerRepository.save(playerEntity);
                        }
                    }
                }
            }

            // 4. 取消定时器
            timerService.cancelTimeout(roomCode);

            // 5. 删除缓存（带重试）（问题5）
            roomCache.removeWithRetry(roomCode);

            // 6. 主动清理聊天室（问题3）
            chatRoomManager.forceCleanup(roomCode);

            // 7. 真正删除数据库记录（问题1）
            roomRepository.delete(room);
        }

        // 🔥 P0-6: 在synchronized块外清理锁，防止内存泄漏
        RoomLock.removeLock(roomCode);

        return room;
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}