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

    @Override
    @Transactional
    public RoomDTO createRoom(Integer maxPlayers, Integer questionCount) {
        GameRoom gameRoom = new GameRoom();
        RoomEntity savedRoom = roomLifecycleService.initializeRoom(maxPlayers, questionCount, gameRoom);
        gameRoom.setRoomEntity(savedRoom);  // ✅ 新增
        roomCache.put(savedRoom.getRoomCode(), gameRoom);
        return roomLifecycleService.toRoomDTO(savedRoom.getRoomCode());  // ✅ 改这里
    }

    @Override
    @Transactional
    public RoomDTO createTestRoom(Integer maxPlayers, Integer questionCount) {
        log.info("🔧 创建测试房间: maxPlayers={}, questionCount={}", maxPlayers, questionCount);

        // 创建普通房间
        GameRoom gameRoom = new GameRoom();
        gameRoom.setTestRoom(true);  // 标记为测试房间

        RoomEntity savedRoom = roomLifecycleService.initializeRoom(maxPlayers, questionCount, gameRoom);
        gameRoom.setRoomEntity(savedRoom);

        log.info("🔧 RoomEntity 已保存: roomCode={}, id={}", savedRoom.getRoomCode(), savedRoom.getId());

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

            log.info("🔧 添加虚拟玩家: {}, ready={}", botName, true);
        }

        roomCache.put(savedRoom.getRoomCode(), gameRoom);

        log.info("🔧 测试房间创建完成: {}, Bot数量: {}, 玩家列表: {}",
            savedRoom.getRoomCode(),
            maxPlayers - 1,
            gameRoom.getPlayers().stream().map(PlayerDTO::getName).toList());

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
    public RoomDTO joinRoom(String roomCode, String playerId, String playerName, Boolean spectator) {
        roomLifecycleService.handleJoin(roomCode, playerId, playerName, spectator);
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
        timerService.cancelTimeout(roomCode);
        roomCache.remove(roomCode);
        log.info("🗑️ 移除房间: {}", roomCode);
    }

    @Override
    public List<RoomDTO> getAllActiveRoom() {
        return roomCache.getAll().stream()
                .filter(gameRoom -> !gameRoom.isFinished())
                .map(gameRoom -> {
                    try {
                        return roomLifecycleService.toRoomDTO(gameRoom.getRoomCode());
                    } catch (BusinessException e) {
                        // 🔥 房间在缓存中但数据库中不存在，跳过并清理
                        log.warn("⚠️ 房间 {} 在缓存中但数据库中不存在，已清理", gameRoom.getRoomCode());
                        roomCache.remove(gameRoom.getRoomCode());
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
        synchronized (roomCode.intern()) {
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

            // 提交答案
            submissionService.submitAnswer(roomCode, playerId, choice);

            // 如果是测试房间且提交者不是Bot，立即触发Bot提交
            if (gameRoom.isTestRoom() && !playerId.startsWith("BOT_")) {
                submissionService.autoSubmitBots(gameRoom);
            }

            // 检查是否所有人都已提交
            boolean allSubmitted = submissionService.allSubmitted(gameRoom);

            if (allSubmitted || force) {
                timerService.cancelTimeout(roomCode);
                // 🔥 总是填充默认答案，已提交的不会被覆盖
                String reason = force ? "force" : "allSubmitted";
                gameFlowService.advanceQuestion(roomCode, reason, true);
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
}
