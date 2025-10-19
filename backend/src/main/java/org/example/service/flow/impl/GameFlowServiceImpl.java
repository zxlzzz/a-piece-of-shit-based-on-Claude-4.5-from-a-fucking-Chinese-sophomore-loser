package org.example.service.flow.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.pojo.RoomStatus;
import org.example.repository.*;
import org.example.service.QuestionSelectorService;
import org.example.service.broadcast.RoomStateBroadcaster;
import org.example.service.cache.RoomCache;
import org.example.service.flow.GameFlowService;
import org.example.service.room.RoomLifecycleService;
import org.example.service.scoring.ScoringResult;
import org.example.service.scoring.ScoringService;
import org.example.service.submission.SubmissionService;
import org.example.service.timer.QuestionTimerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class GameFlowServiceImpl implements GameFlowService {

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

    /**
     * 推进锁（防止并发推进）
     */
    private final Map<String, AtomicBoolean> advancing = new java.util.concurrent.ConcurrentHashMap<>();

    private final long defaultQuestionTimeoutSeconds = 30L;

    @Override
    @Transactional
    public void startGame(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            if (gameRoom.isStarted()) {
                log.warn("⚠️ 房间 {} 已经开始游戏", roomCode);
                return;
            }

            RoomEntity room = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("房间不存在"));
            room.setStatus(RoomStatus.PLAYING);
            roomRepository.save(room);

            GameEntity game = GameEntity.builder()
                    .room(room)
                    .startTime(LocalDateTime.now())
                    .build();
            GameEntity savedGame = gameRepository.save(game);

            gameRoom.setRoomEntity(room);
            gameRoom.setGameId(savedGame.getId());

            // 创建玩家游戏记录
            for (PlayerDTO playerDTO : gameRoom.getPlayers()) {
                PlayerEntity player = playerRepository.findByPlayerId(playerDTO.getPlayerId())
                        .orElseThrow(() -> new BusinessException("玩家不存在: " + playerDTO.getPlayerId()));

                PlayerGameEntity playerGame = PlayerGameEntity.builder()
                        .player(player)
                        .game(savedGame)
                        .score(0)
                        .build();
                playerGameRepository.save(playerGame);
            }

            // 🔥 选题（返回 DTO）
            List<QuestionDTO> questions = questionSelector.selectQuestions(
                    room.getQuestionCount(),
                    gameRoom.getPlayers().size()
            );

            // 初始化游戏房间状态
            gameRoom.setQuestions(questions);  // ✅ 直接设置 DTO
            gameRoom.setGameId(savedGame.getId());
            gameRoom.setStarted(true);
            gameRoom.setCurrentIndex(0);
            gameRoom.setQuestionStartTime(LocalDateTime.now());
            gameRoom.setTimeLimit(30);

            // 启动第一题的定时器
            timerService.scheduleTimeout(roomCode, defaultQuestionTimeoutSeconds,
                    () -> advanceQuestion(roomCode, "timeout", true));

            log.info("🎮 房间 {} 开始游戏，题目数: {}, 玩家数: {}",
                    roomCode, questions.size(), gameRoom.getPlayers().size());

            // 广播
            broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
        }
    }

    @Override
    public void advanceQuestion(String roomCode, String reason, boolean fillDefaults) {
        AtomicBoolean isAdvancing = advancing.computeIfAbsent(roomCode, k -> new AtomicBoolean(false));
        if (!isAdvancing.compareAndSet(false, true)) {
            log.warn("⚠️ 房间 {} 正在推进中，跳过", roomCode);
            return;
        }

        try {
            log.info("📊 推进房间 {} (原因: {})", roomCode, reason);

            GameRoom gameRoom = roomCache.getOrThrow(roomCode);

            synchronized (getInternedRoomCode(roomCode)) {
                // 1. 填充默认答案
                if (fillDefaults) {
                    submissionService.fillDefaultAnswers(gameRoom);
                }

                // 2. 计算当前题目分数
                ScoringResult result = scoringService.calculateScores(gameRoom);

                // 3. 应用分数到房间
                applyScoresToGameRoom(gameRoom, result);

                // 4. 重置玩家准备状态
                gameRoom.getPlayers().forEach(p -> p.setReady(false));

                // 5. 判断是否继续重复题
                boolean shouldRepeat = scoringService.shouldContinueRepeating(gameRoom, result);

                if (shouldRepeat) {
                    // 🔥 重复题：继续下一轮（同一题）
                    if (gameRoom.nextQuestion()) {
                        gameRoom.setQuestionStartTime(LocalDateTime.now());
                        timerService.scheduleTimeout(roomCode, defaultQuestionTimeoutSeconds,
                                () -> advanceQuestion(roomCode, "timeout", true));

                        log.info("🔁 房间 {} 重复题下一轮，题目索引 {} (轮次 {}/{})",
                                roomCode, gameRoom.getCurrentIndex(),
                                result.getCurrentRound(), result.getTotalRounds());

                        broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
                    } else {
                        // 异常情况：重复题还没完成但无法推进
                        log.error("❌ 房间 {} 重复题轮次未完成但无法推进 currentIndex", roomCode);
                        finishGame(roomCode);
                    }

                } else {
                    // 🔥 普通题 或 重复题已完成所有轮次：推进到下一题
                    if (result.isRepeatableQuestion()) {
                        scoringService.clearRounds(roomCode);
                        log.info("✅ 房间 {} 重复题完成全部 {} 轮，准备下一题",
                                roomCode, result.getTotalRounds());
                    }

                    if (gameRoom.nextQuestion()) {
                        gameRoom.setQuestionStartTime(LocalDateTime.now());
                        timerService.scheduleTimeout(roomCode, defaultQuestionTimeoutSeconds,
                                () -> advanceQuestion(roomCode, "timeout", true));

                        log.info("➡️ 房间 {} 推进到题目索引 {}", roomCode, gameRoom.getCurrentIndex());
                        broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
                    } else {
                        // 没有更多题目，游戏结束
                        finishGame(roomCode);
                        log.info("🎉 房间 {} 所有题目完成，游戏结束", roomCode);
                    }
                }
            }
        } finally {
            isAdvancing.set(false);
        }
    }

    @Override
    @Transactional
    public void finishGame(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        synchronized (getInternedRoomCode(roomCode)) {
            if (gameRoom.isFinished()) {
                log.warn("⚠️ 房间 {} 已经结束", roomCode);
                return;
            }

            // 1. 标记游戏结束
            gameRoom.setFinished(true);
            gameRoom.clearPlayerStates();

            // 更新数据库
            RoomEntity room = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("房间不存在"));
            room.setStatus(RoomStatus.FINISHED);
            roomRepository.save(room);

            // ✅ 改成这样
            GameEntity game = gameRepository.findByRoom(room)  // 用 findByRoom
                    .orElseThrow(() -> new BusinessException("游戏记录不存在"));
            game.setEndTime(LocalDateTime.now());
            gameRepository.save(game);

            // 3. 保存玩家最终分数
            for (Map.Entry<String, Integer> entry : gameRoom.getScores().entrySet()) {
                String playerId = entry.getKey();
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

                PlayerGameEntity playerGame = playerGameRepository
                        .findByPlayerAndGame(player, game)
                        .orElseThrow(() -> new BusinessException("游戏记录不存在"));

                playerGame.setScore(entry.getValue());
                playerGameRepository.save(playerGame);
            }

            // 4. 清理轮次记录
            scoringService.clearRounds(roomCode);

            // 5. 取消定时器
            timerService.cancelTimeout(roomCode);

            // 6. 广播结束
            broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));

            log.info("🎉 房间 {} 游戏结束", roomCode);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 应用分数到游戏房间
     */
    private void applyScoresToGameRoom(GameRoom gameRoom, ScoringResult result) {
        int currentIndex = gameRoom.getCurrentIndex();

        for (Map.Entry<String, Integer> entry : result.getFinalScores().entrySet()) {
            String playerId = entry.getKey();
            Integer finalScore = entry.getValue();

            // 累加到总分
            gameRoom.addScore(playerId, finalScore);

            // 🔥 同步更新 playerGameState 的总分
            gameRoom.updatePlayerStateTotalScore(playerId, gameRoom.getScores().get(playerId));

            // 更新玩家DTO的分数
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setScore(gameRoom.getScores().get(playerId)));
        }

        // 记录本题得分详情
        gameRoom.getQuestionScores().put(currentIndex, result.getScoreDetails());

        log.info("✅ 房间 {} 题目索引 {} 分数计算完成", gameRoom.getRoomCode(), currentIndex);
    }

    private String getInternedRoomCode(String roomCode) {
        return roomCode.intern();
    }
}