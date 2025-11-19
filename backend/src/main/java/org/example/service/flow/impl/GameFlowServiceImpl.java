package org.example.service.flow.impl;

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
import org.example.service.flow.GameFlowService;
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
    private final GamePersistenceService gamePersistenceService;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper objectMapper;

    /**
     * 推进锁（防止并发推进）
     */
    private final Map<String, AtomicBoolean> advancing = new java.util.concurrent.ConcurrentHashMap<>();

    private final long defaultQuestionTimeoutSeconds = 30L;

    @Override
    @Transactional(timeout = 10)  // 🔥 P0-4修复：添加10秒超时，防止长时间占用连接
    public void startGame(String roomCode) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
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
                    .isTest(gameRoom.isTestRoom())  // 标记测试游戏
                    .build();
            GameEntity savedGame = gameRepository.save(game);

            gameRoom.setRoomEntity(room);
            gameRoom.setGameId(savedGame.getId());

            // 🔥 创建玩家游戏记录（排除观战者和Bot）
            for (PlayerDTO playerDTO : gameRoom.getPlayers()) {
                // 🔥 跳过观战者
                if (Boolean.TRUE.equals(playerDTO.getSpectator())) {
                    continue;
                }

                // 🔥 跳过虚拟玩家（Bot）
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

            // 🔥 选题（返回 DTO）- 计算非观战者人数
            int nonSpectatorCount = (int) gameRoom.getPlayers().stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getSpectator()))
                    .count();

            // 🔥 解析标签筛选
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
                    nonSpectatorCount,
                    questionTagIds
            );

            // 🔥 修复问题3.2：检查题目是否为空
            if (questions == null || questions.isEmpty()) {
                log.error("❌ 题目加载失败：questionCount={}, 标签={}", room.getQuestionCount(), questionTagIds);
                // 回滚状态
                room.setStatus(RoomStatus.WAITING);
                roomRepository.save(room);
                gameRoom.setRoomEntity(room);
                throw new BusinessException("题目加载失败，请检查题库或标签设置");
            }


            // 初始化游戏房间状态
            gameRoom.setQuestions(questions);  // ✅ 直接设置 DTO
            gameRoom.setGameId(savedGame.getId());
            gameRoom.setStarted(true);
            gameRoom.setCurrentIndex(0);
            gameRoom.setQuestionStartTime(LocalDateTime.now());

            // 🔥 从 RoomEntity 中获取 timeLimit，如果没有则使用默认值 30
            Integer timeLimit = room.getTimeLimit() != null ? room.getTimeLimit() : 30;
            gameRoom.setTimeLimit(timeLimit);

            // 启动第一题的定时器（使用房间设置的 timeLimit）
            timerService.scheduleTimeout(roomCode, timeLimit,
                    () -> advanceQuestion(roomCode, "timeout", true));


            // 🔥 同步到 Redis
            roomCache.syncToRedis(roomCode);

            // 广播
            broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
        }
    }

    @Override
    public void advanceQuestion(String roomCode, String reason, boolean fillDefaults) {
        AtomicBoolean isAdvancing = advancing.computeIfAbsent(roomCode, k -> new AtomicBoolean(false));
        if (!isAdvancing.compareAndSet(false, true)) {
            log.warn("⚠️ 房间 {} 正在推进中，跳过（原因: {}）", roomCode, reason);
            // 🔥 性能优化：移除重复推送，正在推进的线程完成后会广播
            return;
        }

        try {

            GameRoom gameRoom = roomCache.getOrThrow(roomCode);

            // 🔥 P0修复：使用统一的RoomLock
            synchronized (RoomLock.getLock(roomCode)) {
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
                        Integer questionTimeout = gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() : 30;
                        timerService.scheduleTimeout(roomCode, questionTimeout,
                                () -> advanceQuestion(roomCode, "timeout", true));

                        // 🔥 同步到 Redis
                        roomCache.syncToRedis(roomCode);

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
                    }

                    if (gameRoom.nextQuestion()) {
                        gameRoom.setQuestionStartTime(LocalDateTime.now());
                        Integer questionTimeout = gameRoom.getTimeLimit() != null ? gameRoom.getTimeLimit() : 30;
                        timerService.scheduleTimeout(roomCode, questionTimeout,
                                () -> advanceQuestion(roomCode, "timeout", true));


                        // 🔥 同步到 Redis
                        roomCache.syncToRedis(roomCode);

                        broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));
                    } else {
                        // 没有更多题目，游戏结束
                        finishGame(roomCode);
                    }
                }
            }
        } finally {
            isAdvancing.set(false);
        }
    }

    @Override
    @Transactional(timeout = 10)  // 🔥 P0-4修复：添加10秒超时，防止长时间占用连接
    public void finishGame(String roomCode) {

        GameRoom gameRoom = roomCache.getOrThrow(roomCode);

        // 🔥 P0修复：使用统一的RoomLock
        synchronized (RoomLock.getLock(roomCode)) {
            // ✅ 使用 CAS 模式：先检查，通过后立即设置
            if (gameRoom.isFinished()) {
                log.warn("⚠️ 房间 {} 已经结束，跳过重复调用", roomCode);
                return;
            }

            // ✅ 唯一设置 finished 的地方
            gameRoom.setFinished(true);


            try {
                // 1. 更新房间状态
                RoomEntity room = roomRepository.findByRoomCode(roomCode)
                        .orElseThrow(() -> new BusinessException("房间不存在"));
                room.setStatus(RoomStatus.FINISHED);
                roomRepository.save(room);

                // 2. 更新游戏结束时间
                GameEntity game = gameRepository.findByRoom(room)
                        .orElseThrow(() -> new BusinessException("游戏记录不存在"));
                game.setEndTime(LocalDateTime.now());
                gameRepository.save(game);

                // 🔥 3. 保存玩家最终分数（排除观战者和Bot）
                for (Map.Entry<String, Integer> entry : gameRoom.getScores().entrySet()) {
                    String playerId = entry.getKey();

                    // 🔥 检查是否是观战者
                    boolean isSpectator = gameRoom.getPlayers().stream()
                            .filter(p -> p.getPlayerId().equals(playerId))
                            .findFirst()
                            .map(PlayerDTO::getSpectator)
                            .orElse(false);

                    if (isSpectator) {
                        continue;  // 🔥 跳过观战者
                    }

                    // 🔥 跳过 Bot 玩家（Bot 不保存到数据库）
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

                // 4. 清理轮次记录
                scoringService.clearRounds(roomCode);

                // 5. 取消定时器
                timerService.cancelTimeout(roomCode);

                // 6. 保存游戏结果
                gamePersistenceService.saveGameResult(roomCode);

            } catch (Exception e) {
                log.error("❌ 游戏结束流程失败: roomCode={}", roomCode, e);
                // 🔥 修复问题5.2：回滚finished状态，允许重试
                gameRoom.setFinished(false);
                roomCache.syncToRedis(roomCode);
                throw e;
            } finally {
                // 7. 清理玩家状态
                gameRoom.clearPlayerStates();

                // 🔥 8. 清理推进锁（P1-4修复）
                advancing.remove(roomCode);

                // 9. 同步最终状态到 Redis
                roomCache.syncToRedis(roomCode);

                // 10. 广播结束
                broadcaster.sendRoomUpdate(roomCode, roomLifecycleService.toRoomDTO(roomCode));

                // 🔥 11. 延迟删除房间（给前端时间接收结束广播并跳转到结果页）
                // 🔥 修复问题5.1：延长删除时间从2秒到10秒，避免与玩家操作冲突
                // 使用统一的deleteRoom方法，确保完整清理所有资源
                taskScheduler.schedule(() -> {
                    try {
                        roomLifecycleService.deleteRoom(roomCode);
                        broadcaster.sendRoomDeleted(roomCode);
                    } catch (Exception e) {
                        log.error("❌ 自动删除房间失败: roomCode={}", roomCode, e);
                    }
                }, Instant.now().plus(Duration.ofSeconds(10)));
            }
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

    }
}