package org.example.service.game.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.GameController;
import org.example.dto.*;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.*;
import org.example.repository.*;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.cache.RoomCache;
import org.example.service.flow.GameFlowService;
import org.example.service.game.GameService;
import org.example.service.history.GameHistoryService;
import org.example.service.room.RoomLifecycleService;
import org.example.service.submission.SubmissionService;
import org.example.service.timer.QuestionTimerService;
import org.example.utils.RoomLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 游戏服务实现（重构后 - 协调者模式）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameServiceImpl implements GameService {

    // 🔥 核心服务依赖
    private final RoomCache roomCache;
    private final RoomLifecycleService roomLifecycleService;
    private final GameFlowService gameFlowService;
    private final SubmissionService submissionService;
    private final QuestionTimerService timerService;
    private final RoomStateBroadcaster broadcaster;
    private final GameHistoryService gameHistoryService;

    // 数据库依赖
    private final GameRepository gameRepository;

    // ==================== 房间生命周期（委托给 RoomLifecycleService） ====================

    @Transactional
    @Override
    public RoomDTO createRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit, String password, List<Long> questionTagIds) {
        GameRoom gameRoom = new GameRoom();
        RoomEntity savedRoom = roomLifecycleService.initializeRoom(maxPlayers, questionCount, gameRoom, timeLimit, password, questionTagIds);
        gameRoom.setRoomEntity(savedRoom);
        roomCache.put(savedRoom.getRoomCode(), gameRoom);
        return roomLifecycleService.toRoomDTO(savedRoom.getRoomCode());
    }

    @Override
    @Transactional
    public RoomDTO createTestRoom(Integer maxPlayers, Integer questionCount) {

        // 创建普通房间
        GameRoom gameRoom = new GameRoom();
        gameRoom.setTestRoom(true);  // 标记为测试房间

        RoomEntity savedRoom = roomLifecycleService.initializeRoom(maxPlayers, questionCount, gameRoom, 30, null, null);
        gameRoom.setRoomEntity(savedRoom);


        // 添加虚拟玩家 (maxPlayers - 1 个)
        for (int i = 1; i < maxPlayers; i++) {
            String botId = "BOT_" + i;
            String botName = "Bot" + i;

            PlayerDTO botPlayer = PlayerDTO.builder()
                    .playerId(botId)
                    .name(botName)
                    .ready(true)  // Bot默认准备
                    .spectator(false)
                    .build();

            gameRoom.getPlayers().add(botPlayer);
            gameRoom.getScores().put(botId, 0);  // 初始化分数

        }

        roomCache.put(savedRoom.getRoomCode(), gameRoom);

        return roomLifecycleService.toRoomDTO(savedRoom.getRoomCode());
    }

    @Override
    @Transactional
    public RoomDTO updateRoomSettings(String roomCode, GameController.UpdateRoomSettingsRequest request) {
        roomLifecycleService.updateSettings(roomCode, request);
        RoomDTO roomDTO = roomLifecycleService.toRoomDTO(roomCode);
        broadcaster.sendRoomUpdate(roomCode, roomDTO);
        return roomDTO;
    }

    @Override
    @Transactional
    public RoomDTO joinRoom(String roomCode, String playerId, String playerName, Boolean spectator, String password) {
        roomLifecycleService.handleJoin(roomCode, playerId, playerName, spectator, password);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Override
    @Transactional
    public RoomDTO setPlayerReady(String roomCode, String playerId, boolean ready) {
        roomLifecycleService.setPlayerReady(roomCode, playerId, ready);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Override
    public RoomDTO getRoomStatus(String roomCode) {
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Override
    @Transactional
    public RoomDTO leaveRoom(String roomCode, String playerId) {
        boolean roomExists = roomLifecycleService.handleLeave(roomCode, playerId);
        if (!roomExists) {
            removeRoom(roomCode);
            return null;
        }
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Override
    public void handlePlayerDisconnect(String roomCode, String playerId) {
        roomLifecycleService.handlePlayerDisconnect(roomCode, playerId);
    }

    @Override
    public void removeDisconnectedPlayer(String roomCode, String playerId) {
        roomLifecycleService.removeDisconnectedPlayer(roomCode, playerId);
    }

    @Override
    public GameRoom getGameRoom(String roomCode) {
        return roomCache.get(roomCode);
    }

    @Override
    public void removeRoom(String roomCode) {
        roomLifecycleService.deleteRoom(roomCode);
    }

    @Override
    public List<RoomDTO> getAllActiveRoom() {
        return roomCache.getAll().stream()
                .filter(gameRoom -> !gameRoom.isFinished())
                .map(gameRoom -> {
                    try {
                        return roomLifecycleService.toRoomDTO(gameRoom.getRoomCode());
                    } catch (BusinessException e) {
                        // 🔥 P1-2修复：房间在缓存中但数据库中不存在，清理时需取消定时器
                        String orphanedRoomCode = gameRoom.getRoomCode();
                        log.warn("⚠️ 房间 {} 在缓存中但数据库中不存在，已清理", orphanedRoomCode);
                        timerService.cancelTimeout(orphanedRoomCode);
                        roomCache.remove(orphanedRoomCode);
                        return null;
                    }
                })
                .filter(roomDTO -> roomDTO != null)  // 过滤掉null值
                .toList();
    }

    // ==================== 游戏流程（委托给 GameFlowService） ====================

    @Override
    @Transactional
    public RoomDTO startGame(String roomCode) {
        gameFlowService.startGame(roomCode);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Override
    public RoomDTO submitAnswer(String roomCode, String playerId, String choice, boolean force) {
        // 🔥 P0修复：使用统一的RoomLock代替intern()，确保并发安全
        synchronized (RoomLock.getLock(roomCode)) {
            GameRoom gameRoom = roomCache.getOrThrow(roomCode);
            if (!gameRoom.isStarted()) {
                throw new BusinessException("游戏未开始");
            }

            if (gameRoom.getCurrentQuestion() == null) {
                throw new BusinessException("当前没有有效题目");
            }

            // 检查是否已提交
            Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                    .get(gameRoom.getCurrentIndex());
            if (currentRoundSubmissions != null && currentRoundSubmissions.containsKey(playerId)) {
                throw new BusinessException("本轮已经提交过答案");
            }

            // 提交答案（内部会自动同步到Redis）
            submissionService.submitAnswer(roomCode, playerId, choice);

            // 如果是测试房间且提交者不是Bot，立即触发Bot提交
            if (gameRoom.isTestRoom() && !playerId.startsWith("BOT_")) {
                submissionService.autoSubmitBots(gameRoom);
            }

            // 🔥 修复：重新从Redis获取最新的gameRoom（因为submitAnswer已经同步了）
            gameRoom = roomCache.getOrThrow(roomCode);

            // 检查是否所有人都已提交
            boolean allSubmitted = submissionService.allSubmitted(gameRoom);

            if (allSubmitted || force) {
                timerService.cancelTimeout(roomCode);
                // 🔥 总是填充默认答案，已提交的不会被覆盖
                String reason = force ? "force" : "allSubmitted";
                gameFlowService.advanceQuestion(roomCode, reason, true);
                // 🔥 advanceQuestion内部会广播，这里不需要再广播
            } else {
                // 🔥 只在未满员时才广播，减少广播次数
                broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
            }

            return roomLifecycleService.toRoomDTO(roomCode);
        }
    }

    // ==================== 游戏结果 ====================

    @Override
    public GameHistoryDTO getGameHistoryByRoomCode(String roomCode) {
        return gameHistoryService.getGameHistoryByRoomCode(roomCode);
    }

    @Override
    public List<GameHistorySummaryDTO> getHistoryList(Integer days, String playerId) {
        return gameHistoryService.getHistoryList(days, playerId);
    }

    @Override
    public GameHistoryDTO getHistoryDetail(Long gameId) {
        return gameHistoryService.getHistoryDetail(gameId);
    }

    @Override
    @Transactional
    public RoomDTO kickPlayer(String roomCode, String ownerId, String targetPlayerId) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock代替intern()
        synchronized (RoomLock.getLock(roomCode)) {
            // 检查房间状态
            if (gameRoom.isStarted()) {
                throw new BusinessException("游戏已开始，无法踢出玩家");
            }

            // 检查操作者是否是房主（第一个玩家）
            if (gameRoom.getPlayers().isEmpty() ||
                !gameRoom.getPlayers().get(0).getPlayerId().equals(ownerId)) {
                throw new BusinessException("只有房主可以踢出玩家");
            }

            // 不能踢出自己
            if (targetPlayerId.equals(ownerId)) {
                throw new BusinessException("不能踢出自己");
            }

            // 踢出玩家（使用 handleLeave 的逻辑）
            roomLifecycleService.handleLeave(roomCode, targetPlayerId);

            return roomLifecycleService.toRoomDTO(roomCode);
        }
    }
}
