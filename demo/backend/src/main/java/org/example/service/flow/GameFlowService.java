package org.example.service.flow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.pojo.RoomStatus;
import org.example.repository.*;
import org.example.service.question.QuestionSelectorService;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.cache.RoomCache;
import org.example.service.persistence.GamePersistenceService;
import org.example.service.room.RoomLifecycleService;
import org.example.service.scoring.ScoringResult;
import org.example.service.scoring.ScoringService;
import org.example.service.submission.SubmissionService;
import org.example.service.timer.QuestionTimerService;
import org.example.utils.RoomLock;
import java.time.Duration;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 游戏流程控制服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameFlowService {

    private final RoomCache roomCache;
    private final RoomRepository roomRepository;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final PlayerGameRepository playerGameRepository;
    private final QuestionSelectorService questionSelector;
    private final SubmissionService submissionService;
    private final ScoringService scoringService;
    private final QuestionTimerService timerService;
    private final RoomStateBroadcaster broadcaster;
    private final RoomLifecycleService roomLifecycleService;
    private final GamePersistenceService gamePersistenceService;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper objectMapper;

    /**
     * 推进锁（防止并发推进）
     */
    private final Map<String, AtomicBoolean> advancing = new java.util.concurrent.ConcurrentHashMap<>();

    private final long defaultQuestionTimeoutSeconds = 30L;

    @Transactional(timeout = 10)
    public void startGame(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            if (gameRoom.isStarted()) {
                log.warn("房间 {} 已经开始游戏", roomCode);
                return;
            }

            RoomEntity room = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("房间不存在"));
            room.setStatus(RoomStatus.PLAYING);
            roomRepository.save(room);

            GameEntity game = GameEntity.builder()
                    .room(room)
                    .startTime(LocalDateTime.now())
                    .isTest(gameRoom.isTestRoom())
                    .build();
            GameEntity savedGame = gameRepository.save(game);

            gameRoom.setRoomEntity(room);
            gameRoom.setGameId(savedGame.getId());

            for (PlayerDTO playerDTO : gameRoom.getPlayers()) {
                if (playerDTO.getPlayerId().startsWith("BOT_")) {
                    continue;
                }

                PlayerEntity player = playerRepository.findByPlayerId(playerDTO.getPlayerId())
                        .orElseThrow(() -> new BusinessException("玩家不存在: " + playerDTO.getPlayerId()));

                PlayerGameEntity playerGame = PlayerGameEntity.builder()
                        .player(player)
                        .game(savedGame)
                        .score(0)
                        .build();
                playerGameRepository.save(playerGame);
            }

            int playerCount = gameRoom.getPlayers().size();

            List<Long> questionTagIds = null;
            if (room.getQuestionTagIdsJson() != null && !room.getQuestionTagIdsJson().isEmpty()) {
                try {
                    questionTagIds = objectMapper.readValue(
                            room.getQuestionTagIdsJson(),
                            new TypeReference<List<Long>>() {}
                    );
                } catch (Exception e) {
                    log.error("解析questionTagIds失败", e);
                }
            }

            List<QuestionDTO> questions = questionSelector.selectQuestions(
                    room.getQuestionCount(),
                    playerCount,
                    questionTagIds
            );

            if (questions == null || questions.isEmpty()) {
                log.error("题目加载失败：questionCount={}, 标签={}", room.getQuestionCount(), questionTagIds);
                room.setStatus(RoomStatus.WAITING);
                roomRepository.save(room);
                gameRoom.setRoomEntity(room);
                throw new BusinessException("题目加载失败，请检查题库或标签设置");
            }

            gameRoom.setQuestions(questions);
            gameRoom.setGameId(savedGame.getId());
            gameRoom.setStarted(true);
            gameRoom.setCurrentIndex(0);
            gameRoom.setQuestionStartTime(LocalDateTime.now());

            Integer timeLimit = room.getTimeLimit() != null ? room.getTimeLimit() : 30;
            gameRoom.setTimeLimit(timeLimit);

            timerService.scheduleTimeout(roomCode, timeLimit,
                    () -> advanceQuestion(roomCode, "timeout", true));

            roomCache.put(roomCode, gameRoom);

            broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
        }
    }

    public void advanceQuestion(String roomCode, String reason, boolean fillDefaults) {
        AtomicBoolean isAdvancing = advancing.computeIfAbsent(roomCode, k -> new AtomicBoolean(false));
        if (!isAdvancing.compareAndSet(false, true)) {
            log.warn("房间 {} 正在推进中，跳过（原因: {}）", roomCode, reason);
            return;
        }

        try {
            GameRoom gameRoom = roomCache.getOrThrow(roomCode);

            synchronized (RoomLock.getLock(roomCode)) {
                if (fillDefaults) {
                    submissionService.fillDefaultAnswers(gameRoom);
                }

                ScoringResult result = scoringService.calculateScores(gameRoom);

                applyScoresToGameRoom(gameRoom, result);

                gameRoom.getPlayers().forEach(p -> p.setReady(false));

                boolean shouldRepeat = scoringService.shouldContinueRepeating(gameRoom, result);

                if (shouldRepeat) {
                    if (gameRoom.nextQuestion()) {
                        gameRoom.setQuestionStartTime(LocalDateTime.now());
                        Integer questionTimeout = gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() : 30;
                        timerService.scheduleTimeout(roomCode, questionTimeout,
                                () -> advanceQuestion(roomCode, "timeout", true));

                        roomCache.put(roomCode, gameRoom);

                        broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
                    } else {
                        log.error("房间 {} 重复题轮次未完成但无法推进 currentIndex", roomCode);
                        finishGame(roomCode);
                    }

                } else {
                    if (result.isRepeatableQuestion()) {
                        scoringService.clearRounds(roomCode);
                    }

                    if (gameRoom.nextQuestion()) {
                        gameRoom.setQuestionStartTime(LocalDateTime.now());
                        Integer questionTimeout = gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() : 30;
                        timerService.scheduleTimeout(roomCode, questionTimeout,
                                () -> advanceQuestion(roomCode, "timeout", true));

                        roomCache.put(roomCode, gameRoom);

                        broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
                    } else {
                        finishGame(roomCode);
                    }
                }
            }
        } finally {
            isAdvancing.set(false);
        }
    }

    @Transactional(timeout = 10)
    public void finishGame(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (RoomLock.getLock(roomCode)) {
            if (gameRoom.isFinished()) {
                log.warn("房间 {} 已经结束，跳过重复调用", roomCode);
                return;
            }

            gameRoom.setFinished(true);

            try {
                RoomEntity room = roomRepository.findByRoomCode(roomCode)
                        .orElseThrow(() -> new BusinessException("房间不存在"));
                room.setStatus(RoomStatus.FINISHED);
                roomRepository.save(room);

                GameEntity game = gameRepository.findByRoom(room)
                        .orElseThrow(() -> new BusinessException("游戏记录不存在"));
                game.setEndTime(LocalDateTime.now());
                gameRepository.save(game);

                for (Map.Entry<String, Integer> entry : gameRoom.getScores().entrySet()) {
                    String playerId = entry.getKey();

                    if (playerId.startsWith("BOT_")) {
                        continue;
                    }

                    PlayerEntity player = playerRepository.findByPlayerId(playerId)
                            .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

                    PlayerGameEntity playerGame = playerGameRepository
                            .findByPlayerAndGame(player, game)
                            .orElseThrow(() -> new BusinessException("游戏记录不存在"));

                    playerGame.setScore(entry.getValue());
                    playerGameRepository.save(playerGame);
                }

                scoringService.clearRounds(roomCode);

                timerService.cancelTimeout(roomCode);

                gamePersistenceService.saveGameResult(gameRoom);

            } catch (Exception e) {
                log.error("游戏结束流程失败: roomCode={}", roomCode, e);
                gameRoom.setFinished(false);
                roomCache.put(roomCode, gameRoom);
                throw e;
            } finally {
                gameRoom.clearPlayerStates();

                advancing.remove(roomCode);

                roomCache.put(roomCode, gameRoom);

                broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));

                taskScheduler.schedule(() -> {
                    try {
                        roomLifecycleService.deleteRoom(roomCode);
                        broadcaster.sendRoomDeleted(roomCode);
                    } catch (Exception e) {
                        log.error("自动删除房间失败: roomCode={}", roomCode, e);
                    }
                }, Instant.now().plus(Duration.ofSeconds(10)));
            }
        }
    }

    private void applyScoresToGameRoom(GameRoom gameRoom, ScoringResult result) {
        int currentIndex = gameRoom.getCurrentIndex();

        for (Map.Entry<String, Integer> entry : result.getFinalScores().entrySet()) {
            String playerId = entry.getKey();
            Integer finalScore = entry.getValue();

            gameRoom.addScore(playerId, finalScore);

            gameRoom.updatePlayerStateTotalScore(playerId, gameRoom.getScores().get(playerId));

            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setScore(gameRoom.getScores().get(playerId)));
        }

        gameRoom.getQuestionScores().put(currentIndex, result.getScoreDetails());
    }
}