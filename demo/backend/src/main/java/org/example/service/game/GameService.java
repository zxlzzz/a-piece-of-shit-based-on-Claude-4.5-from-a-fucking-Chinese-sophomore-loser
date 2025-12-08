package org.example.service.game;

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
import org.example.service.history.GameHistoryService;
import org.example.service.room.RoomLifecycleService;
import org.example.service.submission.SubmissionService;
import org.example.service.timer.QuestionTimerService;
import org.example.utils.RoomLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 游戏服务实现（协调者模式）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final RoomCache roomCache;
    private final RoomLifecycleService roomLifecycleService;
    private final GameFlowService gameFlowService;
    private final SubmissionService submissionService;
    private final QuestionTimerService timerService;
    private final RoomStateBroadcaster broadcaster;
    private final GameHistoryService gameHistoryService;
    private final GameRepository gameRepository;

    @Transactional
    public RoomDTO createRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit) {
        GameRoom gameRoom = new GameRoom();
        RoomEntity savedRoom = roomLifecycleService.initializeRoom(maxPlayers, questionCount, timeLimit, gameRoom);
        gameRoom.setRoomEntity(savedRoom);
        roomCache.put(savedRoom.getRoomCode(), gameRoom);
        return roomLifecycleService.toRoomDTO(savedRoom.getRoomCode());
    }

    @Transactional
    public RoomDTO joinRoom(String roomCode, String playerId, String playerName) {
        roomLifecycleService.handleJoin(roomCode, playerId, playerName);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Transactional
    public RoomDTO setPlayerReady(String roomCode, String playerId, boolean ready) {
        roomLifecycleService.setPlayerReady(roomCode, playerId, ready);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    public RoomDTO getRoomStatus(String roomCode) {
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    @Transactional
    public RoomDTO leaveRoom(String roomCode, String playerId) {
        boolean roomExists = roomLifecycleService.handleLeave(roomCode, playerId);
        if (!roomExists) {
            removeRoom(roomCode);
            return null;
        }
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    public GameRoom getGameRoom(String roomCode) {
        return roomCache.get(roomCode);
    }

    public void removeRoom(String roomCode) {
        timerService.cancelTimeout(roomCode);
        roomCache.remove(roomCode);
    }

    public List<RoomDTO> getAllActiveRoom() {
        Collection<GameRoom> rooms = roomCache.getAll();
        List<RoomDTO> roomDTOs = new ArrayList<>();
        for (GameRoom room : rooms) {
            if (room != null && room.getRoomEntity() != null) {
                try {
                    RoomDTO dto = roomLifecycleService.toRoomDTO(room.getRoomEntity().getRoomCode());
                    if (dto != null) {
                        roomDTOs.add(dto);
                    }
                } catch (Exception e) {
                    log.error("转换房间DTO失败: {}", room.getRoomEntity().getRoomCode(), e);
                }
            }
        }
        return roomDTOs;
    }

    @Transactional
    public RoomDTO startGame(String roomCode) {
        gameFlowService.startGame(roomCode);
        return roomLifecycleService.toRoomDTO(roomCode);
    }

    public RoomDTO submitAnswer(String roomCode, String playerId, String choice, boolean force) {
        synchronized (RoomLock.getLock(roomCode)) {
            GameRoom gameRoom = roomCache.getOrThrow(roomCode);
            if (!gameRoom.isStarted()) {
                throw new BusinessException("游戏未开始");
            }

            if (gameRoom.getCurrentQuestion() == null) {
                throw new BusinessException("当前没有有效题目");
            }

            Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                    .get(gameRoom.getCurrentIndex());
            if (currentRoundSubmissions != null && currentRoundSubmissions.containsKey(playerId)) {
                throw new BusinessException("本轮已经提交过答案");
            }

            submissionService.submitAnswer(roomCode, playerId, choice);

            gameRoom = roomCache.getOrThrow(roomCode);

            boolean allSubmitted = submissionService.allSubmitted(gameRoom);

            if (allSubmitted || force) {
                timerService.cancelTimeout(roomCode);
                String reason = force ? "force" : "allSubmitted";
                gameFlowService.advanceQuestion(roomCode, reason, true);
            } else {
                broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
            }

            return roomLifecycleService.toRoomDTO(roomCode);
        }
    }

    public GameHistoryDTO getGameHistoryByRoomCode(String roomCode) {
        return gameHistoryService.getGameHistoryByRoomCode(roomCode);
    }

    public List<GameHistorySummaryDTO> getHistoryList(Integer days, String playerId) {
        return gameHistoryService.getHistoryList(days, playerId);
    }

    public GameHistoryDTO getHistoryDetail(Long gameId) {
        return gameHistoryService.getHistoryDetail(gameId);
    }
}
