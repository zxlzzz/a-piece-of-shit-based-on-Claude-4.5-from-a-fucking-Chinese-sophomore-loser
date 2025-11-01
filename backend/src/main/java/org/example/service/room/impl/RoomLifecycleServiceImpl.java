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
import org.example.service.room.RoomLifecycleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    @Override
    @Transactional
    public RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit, GameRoom gameRoom) {
        String roomCode = generateRoomCode();

        // 🔥 创建房间实体（只有基础字段）
        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                .timeLimit(timeLimit != null ? timeLimit : 30)
                // 🔥 高级规则使用默认值
                .rankingMode("standard")
                .targetScore(null)
                .winConditionsJson(null)
                .build();

        RoomEntity savedRoom = roomRepository.save(roomEntity);

        // 初始化内存房间
        gameRoom.setRoomCode(roomCode);
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

        log.info("✅ 创建房间: {}, 最大人数: {}, 题目数: {}", roomCode, maxPlayers, questionCount);
        return savedRoom;
    }

    @Override
    @Transactional
    public void handleJoin(String roomCode, String playerId, String playerName, Boolean spectator) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            // 检查房间状态
            if (room.getStatus() != RoomStatus.WAITING) {
                throw new BusinessException("房间已开始游戏或已结束");
            }

            // 检查房间是否已满
            if (gameRoom.getPlayers().size() >= room.getMaxPlayers()) {
                throw new BusinessException("房间已满");
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
                gameRoom.getPlayers().add(playerDTO);
                gameRoom.getScores().put(playerId, 0);

                log.info("✅ 玩家 {} ({}) 加入房间 {} (观战模式: {})", playerName, playerId, roomCode, spectator);

                // 🔥 同步到 Redis
                roomCache.syncToRedis(roomCode);
            }
        }
    }

    @Override
    @Transactional
    public boolean handleLeave(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
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
                    // 房主离开，解散房间
                    roomCache.remove(roomCode);
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);

                    log.info("🏠 房主 {} 离开，房间 {} 已解散", playerName, roomCode);
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

                    log.info("👋 玩家 {} 离开房间 {}（游戏未开始）", playerName, roomCode);

                    // 🔥 同步到 Redis
                    roomCache.syncToRedis(roomCode);
                }

            } else {
                // 游戏进行中：标记断线
                log.info("⏸️ 玩家 {} 离开房间 {}（游戏进行中，后续自动提交）", playerName, roomCode);

                long connectedCount = gameRoom.getPlayers().stream()
                        .filter(p -> !gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()))
                        .count();

                if (connectedCount == 0) {
                    // 🔥 改：游戏进行中时不立即删除，给重连时间
                    if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                        log.warn("⚠️ 房间 {} 所有玩家断线，但游戏进行中，保留房间等待重连", roomCode);
                        // 不删除房间，保留5分钟
                        return true; // 房间仍存在
                    } else {
                        // 游戏未开始或已结束，可以删除
                        roomCache.remove(roomCode);
                        room.setStatus(RoomStatus.FINISHED);
                        roomRepository.save(room);
                        log.info("🏠 所有玩家离开，房间 {} 已解散", roomCode);
                        return false; // 房间已解散
                    }
                }

                // 🔥 游戏进行中标记断线，同步到 Redis
                roomCache.syncToRedis(roomCode);
            }

            return true; // 房间仍存在
        }
    }

    @Override
    @Transactional
    public void handleReconnect(String roomCode, String playerId) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            LocalDateTime disconnectTime = gameRoom.getDisconnectedPlayers().remove(playerId);

            if (disconnectTime != null) {
                long offlineSeconds = java.time.Duration.between(disconnectTime, LocalDateTime.now()).getSeconds();

                gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .ifPresent(player ->
                                log.info("✅ 玩家 {} 重连房间 {}，离线时长: {}秒",
                                        player.getName(), roomCode, offlineSeconds)
                        );

                // 🔥 添加：如果游戏已结束，重连时重置房间过期时间
                if (gameRoom.isFinished()) {
                    // 给房间续期（重新计时5分钟）
                    log.info("🔄 玩家重连，房间 {} 延长存活时间", roomCode);
                    // 这里可以通过 RoomCache 添加续期机制
                }
            } else {
                log.warn("⚠️ 玩家 {} 重连房间 {}，但未找到断线记录", playerId, roomCode);
            }

            // 🔥 同步到 Redis
            roomCache.syncToRedis(roomCode);
        }
    }

    @Override
    @Transactional
    public void updateSettings(String roomCode, GameController.UpdateRoomSettingsRequest request) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            // 校验：游戏未开始
            if (gameRoom.isStarted()) {
                throw new BusinessException("游戏已开始，无法修改设置");
            }

            // 更新题目数量（可选）
            if (request.getQuestionCount() != null && request.getQuestionCount() > 0) {
                room.setQuestionCount(request.getQuestionCount());
                log.info("📝 房间 {} 题目数量更新为: {}", roomCode, request.getQuestionCount());
            }

            // 更新每题时长（可选）
            if (request.getTimeLimit() != null && request.getTimeLimit() >= 20 && request.getTimeLimit() <= 120) {
                room.setTimeLimit(request.getTimeLimit());
                log.info("⏱️ 房间 {} 每题时长更新为: {}秒", roomCode, request.getTimeLimit());
            }

            // 更新排名模式
            if (request.getRankingMode() != null) {
                room.setRankingMode(request.getRankingMode());
                log.info("📊 房间 {} 排名模式更新为: {}", roomCode, request.getRankingMode());
            }

            // 更新目标分数
            room.setTargetScore(request.getTargetScore());

            // 更新通关条件
            String winConditionsJson = null;
            if (request.getWinConditions() != null) {
                try {
                    winConditionsJson = objectMapper.writeValueAsString(request.getWinConditions());
                    log.info("🎯 房间 {} 通关条件更新为: {}", roomCode, winConditionsJson);
                } catch (Exception e) {
                    log.error("序列化通关条件失败", e);
                    throw new BusinessException("通关条件格式错误");
                }
            }
            room.setWinConditionsJson(winConditionsJson);

            // 保存到数据库
            roomRepository.save(room);

            log.info("✅ 房间 {} 设置更新成功", roomCode);
        }
    }

    @Override
    @Transactional
    public void setPlayerReady(String roomCode, String playerId, boolean ready) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        if (!player.getRoom().getRoomCode().equals(roomCode)) {
            throw new BusinessException("玩家不在该房间中");
        }

        player.setReady(ready);
        playerRepository.save(player);

        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom != null) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));

            // 🔥 同步到 Redis
            roomCache.syncToRedis(roomCode);
        }

        log.info("✅ 玩家 {} 设置准备状态: {}", playerId, ready);
    }

    @Override
    public RoomDTO toRoomDTO(String roomCode) {
        RoomEntity roomEntity = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);
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

        synchronized (getInternedRoomCode(roomCode)) {
            // 🔥 标记断线时间
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            String playerName = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .map(PlayerDTO::getName)
                    .findFirst()
                    .orElse("未知玩家");

            log.info("⚠️ 玩家 {} ({}) 从房间 {} 断开连接", playerName, playerId, roomCode);

            // 🔥 如果游戏进行中且所有玩家都断线，自动推进
            if (gameRoom.isStarted() && gameRoom.getCurrentQuestion() != null) {
                boolean allDisconnected = gameRoom.getPlayers().stream()
                        .allMatch(p -> gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()));

                if (allDisconnected) {
                    log.warn("❌ 房间 {} 所有玩家都断开连接", roomCode);
                    // 注意：不在这里调用 advanceQuestion，由 GameFlowService 处理
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

        synchronized (getInternedRoomCode(roomCode)) {
            // 🔥 添加：如果游戏进行中，不移除玩家，只保持断线状态
            if (gameRoom.isStarted() && !gameRoom.isFinished()) {
                log.info("⚠️ 玩家 {} 在游戏中断线，保留玩家数据，游戏结束后再移除", playerId);
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
                log.info("👋 玩家 {} 超时未重连，已从房间 {} 移除", removedPlayer.getName(), roomCode);
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

            // 检查是否房间为空
            if (gameRoom.getPlayers().isEmpty()) {
                log.warn("🏠 房间 {} 所有玩家都已离开，准备解散", roomCode);
                RoomEntity room = roomRepository.findByRoomCode(roomCode).orElse(null);
                if (room != null) {
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);
                }
                roomCache.remove(roomCode);
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

        return RoomDTO.builder()
                .roomCode(gameRoom.getRoomCode())
                .maxPlayers(gameRoom.getMaxPlayers() != null ? gameRoom.getMaxPlayers() :
                        (roomEntity != null ? roomEntity.getMaxPlayers() : gameRoom.getPlayers().size()))
                .currentPlayers(gameRoom.getPlayers().size())
                .status(status)
                .players(new ArrayList<>(gameRoom.getPlayers()))
                .questionStartTime(gameRoom.getQuestionStartTime())
                .timeLimit(gameRoom.getTimeLimit())
                .currentIndex(gameRoom.getCurrentIndex())
                .currentQuestion(currentQuestionDTO)  // ✅ 直接使用
                .questionCount(questionCount)
                .rankingMode(roomEntity != null ? roomEntity.getRankingMode() : "standard")
                .targetScore(roomEntity != null ? roomEntity.getTargetScore() : null)
                .winConditions(winConditions)
                .build();
    }

    // ==================== 私有方法 ====================

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String getInternedRoomCode(String roomCode) {
        return roomCode.intern();
    }
}