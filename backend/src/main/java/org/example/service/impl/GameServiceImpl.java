package org.example.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.controller.GameController;
import org.example.dto.*;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.*;
import org.example.repository.*;
import org.example.service.*;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.cache.RoomCache;
import org.example.service.flow.GameFlowService;
import org.example.service.history.GameHistoryService;
import org.example.service.leaderboard.LeaderboardService;
import org.example.service.persistence.GamePersistenceService;
import org.example.service.room.RoomLifecycleService;
import org.example.service.submission.SubmissionService;
import org.example.service.timer.QuestionTimerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private final LeaderboardService leaderboardService;
    private final GamePersistenceService persistenceService;
    private final GameHistoryService gameHistoryService;

    // 数据库依赖
    private final GameRepository gameRepository;
    private final PlayerGameRepository playerGameRepository;
    private final GameResultRepository gameResultRepository;
    private final ObjectMapper objectMapper;

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
    public RoomDTO updateRoomSettings(String roomCode, GameController.UpdateRoomSettingsRequest request) {
        roomLifecycleService.updateSettings(roomCode, request);
        RoomDTO roomDTO = roomLifecycleService.toRoomDTO(roomCode);
        broadcaster.sendRoomUpdate(roomCode, roomDTO);
        return roomDTO;
    }

    @Override
    @Transactional
    public RoomDTO joinRoom(String roomCode, String playerId, String playerName) {
        roomLifecycleService.handleJoin(roomCode, playerId, playerName);
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
    public RoomDTO reconnectRoom(String roomCode, String playerId) {
        roomLifecycleService.handleReconnect(roomCode, playerId);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Override
    public void handlePlayerDisconnect(String roomCode, String playerId) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null) return;

        synchronized (roomCode.intern()) {
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());
            log.info("⚠️ 玩家 {} 从房间 {} 断开连接", playerId, roomCode);

            // ✅ 新增：检查是否需要自动填充答案
            if (gameRoom.isStarted() && gameRoom.getCurrentQuestion() != null) {
                boolean allDisconnected = gameRoom.getPlayers().stream()
                        .allMatch(p -> gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()));

                if (allDisconnected) {
                    log.warn("❌ 房间 {} 所有玩家都断开连接，自动填充答案并推进", roomCode);
                    submissionService.fillDefaultAnswers(gameRoom);
                    gameFlowService.advanceQuestion(roomCode, "allDisconnected", true);
                }
            }
        }
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
                .map(gameRoom -> roomLifecycleService.toRoomDTO(
                        gameRoom.getRoomCode()
                ))
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

            // 检查是否所有人都已提交
            boolean allSubmitted = submissionService.allSubmitted(gameRoom);

            if (allSubmitted || force) {
                timerService.cancelTimeout(roomCode);
                gameFlowService.advanceQuestion(roomCode, force ? "force" : "allSubmitted", force);
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
    @Transactional
    public void saveGameResult(String roomCode) {
        persistenceService.saveGameResult(roomCode);
    }

    @Override
    public GameHistoryDTO getCurrentGameStatus(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);
        GameEntity game = gameRepository.findByRoom(gameRoom.getRoomEntity())
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));

        List<PlayerRankDTO> leaderboard = leaderboardService.buildLeaderboard(gameRoom);

        return GameHistoryDTO.builder()
                .gameId(game.getId())
                .roomCode(roomCode)
                .startTime(game.getStartTime())
                .endTime(game.getEndTime())
                .questionCount(gameRoom.getQuestions().size())
                .playerCount(gameRoom.getPlayers().size())
                .leaderboard(leaderboard)
                .questionDetails(new ArrayList<>())
                .build();
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
