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
import org.example.service.QuestionScoringStrategyImpl.QR.RepeatableQuestionStrategy;
import org.example.utils.DTOConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameServiceImpl implements GameService {

    private final QuestionFactory questionFactory;
    private final RoomRepository roomRepository;
    private final GameRepository gameRepository;
    private final PlayerRepository playerRepository;
    private final PlayerGameRepository playerGameRepository;
    private final SubmissionRepository submissionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameResultRepository gameResultRepository;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;
    private final QuestionSelectorService questionSelector;
    private final Map<String, Map<String, Integer>> roomStrategyRounds = new ConcurrentHashMap<>();

    // 活跃房间（内存存储）
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();

    // 定时任务调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

    // 题目超时定时器
    private final Map<String, ScheduledFuture<?>> questionTimeouts = new ConcurrentHashMap<>();

    // 推进锁（防止并发推进）
    private final Map<String, AtomicBoolean> advancing = new ConcurrentHashMap<>();

    // 默认答题时间
    private final long defaultQuestionTimeoutSeconds = 30L;


    // 在 GameServiceImpl 类中修改和新增以下方法：

    @Override
    @Transactional
    public RoomDTO createRoom(Integer maxPlayers, Integer questionCount) {
        String roomCode = generateRoomCode();

        // 🔥 创建房间实体（只有基础字段）
        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                // 🔥 高级规则使用默认值
                .rankingMode("standard")
                .targetScore(null)
                .winConditionsJson(null)
                .build();

        RoomEntity savedRoom = roomRepository.save(roomEntity);

        // 创建内存房间（原有逻辑）
        GameRoom gameRoom = new GameRoom();
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

        activeRooms.put(roomCode, gameRoom);
        advancing.put(roomCode, new AtomicBoolean(false));

        log.info("创建房间: {}, 最大人数: {}, 题目数: {}", roomCode, maxPlayers, questionCount);
        return toRoomDTO(savedRoom, gameRoom);
    }

    // 🔥 新增：更新房间设置
    @Override
    @Transactional
    public RoomDTO updateRoomSettings(String roomCode, GameController.UpdateRoomSettingsRequest request) {
        // 1. 查找房间
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间状态异常");
        }

        synchronized (this.getInternedRoomCode(roomCode)) {
            // 2. 校验：游戏未开始
            if (gameRoom.isStarted()) {
                throw new BusinessException("游戏已开始，无法修改设置");
            }

            // 3. 更新题目数量（可选）
            if (request.getQuestionCount() != null && request.getQuestionCount() > 0) {
                room.setQuestionCount(request.getQuestionCount());
                log.info("房间 {} 题目数量更新为: {}", roomCode, request.getQuestionCount());
            }

            // 4. 更新排名模式
            if (request.getRankingMode() != null) {
                room.setRankingMode(request.getRankingMode());
                log.info("房间 {} 排名模式更新为: {}", roomCode, request.getRankingMode());
            }

            // 5. 更新目标分数
            room.setTargetScore(request.getTargetScore());

            // 6. 更新通关条件
            String winConditionsJson = null;
            if (request.getWinConditions() != null) {
                try {
                    winConditionsJson = objectMapper.writeValueAsString(request.getWinConditions());
                    log.info("房间 {} 通关条件更新为: {}", roomCode, winConditionsJson);
                } catch (Exception e) {
                    log.error("序列化通关条件失败", e);
                    throw new BusinessException("通关条件格式错误");
                }
            }
            room.setWinConditionsJson(winConditionsJson);

            // 7. 保存到数据库
            roomRepository.save(room);

            log.info("✅ 房间 {} 设置更新成功", roomCode);

            // 8. 转换为 DTO
            RoomDTO roomDTO = toRoomDTO(room, gameRoom);

            // 9. 🔥 广播给所有人
            broadcastRoomState(roomCode);

            return roomDTO;
        }
    }

    @Override
    @Transactional
    public RoomDTO joinRoom(String roomCode, String playerId, String playerName) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间状态异常");
        }

        synchronized (this.getInternedRoomCode(roomCode)) {
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
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElse(null);

                if (player == null) {
                    player = PlayerEntity.builder()
                            .playerId(playerId)
                            .name(playerName)
                            .ready(false)
                            .room(room)
                            .build();
                } else {
                    player.setRoom(room);
                    player.setReady(false);
                }
                playerRepository.save(player);

                PlayerDTO playerDTO = PlayerDTO.builder()
                        .playerId(playerId)
                        .name(playerName)
                        .score(0)
                        .ready(false)
                        .build();
                gameRoom.getPlayers().add(playerDTO);
                gameRoom.getScores().put(playerId, 0);

                log.info("玩家 {} ({}) 加入房间 {}", playerName, playerId, roomCode);
            }

            return toRoomDTO(room, gameRoom);
        }
    }

    @Override
    public GameHistoryDTO getCurrentGameStatus(String roomCode) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameEntity game = gameRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("游戏数据不存在");
        }

        List<PlayerRankDTO> leaderboard = buildLeaderboard(gameRoom);
        List<QuestionDetailDTO> questionDetails = buildQuestionDetails(gameRoom);

        return GameHistoryDTO.builder()
                .gameId(game.getId())
                .roomCode(roomCode)
                .startTime(game.getStartTime())
                .endTime(game.getEndTime())
                .questionCount(gameRoom.getQuestions().size())
                .playerCount(gameRoom.getPlayers().size())
                .leaderboard(leaderboard)
                .questionDetails(questionDetails)
                .build();
    }

    @Override
    @Transactional
    public RoomDTO startGame(String roomCode) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间状态异常");
        }

        synchronized (this.getInternedRoomCode(roomCode)) {
            if (gameRoom.isStarted()) {
                return toRoomDTO(room, gameRoom);
            }

            room.setStatus(RoomStatus.PLAYING);
            roomRepository.save(room);

            GameEntity game = GameEntity.builder()
                    .roomCode(roomCode)
                    .startTime(LocalDateTime.now())
                    .build();
            GameEntity savedGame = gameRepository.save(game);

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

            List<QuestionEntity> questions = questionSelector.selectQuestions(
                    room.getQuestionCount(),
                    gameRoom.getPlayers().size()
            );
            gameRoom.setQuestions(questions);
            gameRoom.setGameId(savedGame.getId());
            gameRoom.setStarted(true);
            gameRoom.setCurrentIndex(0);
            gameRoom.setQuestionStartTime(LocalDateTime.now());
            gameRoom.setTimeLimit(30);

            scheduleQuestionTimeout(gameRoom, defaultQuestionTimeoutSeconds);

            log.info("房间 {} 开始游戏，题目数: {}, 玩家数: {}",
                    roomCode, questions.size(), gameRoom.getPlayers().size());
            return toRoomDTO(room, gameRoom);
        }
    }

    @Override
    public RoomDTO submitAnswer(String roomCode, String playerId, String choice, boolean force) {
        synchronized (this.getInternedRoomCode(roomCode)) {
            GameRoom gameRoom = activeRooms.get(roomCode);
            if (gameRoom == null || !gameRoom.isStarted()) {
                throw new BusinessException("游戏未开始");
            }

            QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
            if (currentQuestion == null) {
                throw new BusinessException("当前没有有效题目");
            }

            Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                    .get(gameRoom.getCurrentIndex());

            if (currentRoundSubmissions != null && currentRoundSubmissions.containsKey(playerId)) {
                throw new BusinessException("本轮已经提交过答案");
            }

            this.saveSubmissionInNewTransaction(playerId, currentQuestion, gameRoom, choice);

            gameRoom.getSubmissions()
                    .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                    .put(playerId, choice);

            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(true));

            log.info("💾 玩家 {} 提交答案: {}", playerId, choice);

            boolean allSubmitted = gameRoom.getPlayers().stream()
                    .allMatch(p -> gameRoom.getSubmissions()
                            .get(gameRoom.getCurrentIndex())
                            .containsKey(p.getPlayerId()));

            if (allSubmitted || force) {
                cancelQuestionTimeout(roomCode);
                advanceQuestionIfNeeded(gameRoom, force ? "force" : "allSubmitted", force);
            }

            RoomEntity room = roomRepository.findByRoomCode(roomCode)
                    .orElseThrow(() -> new BusinessException("房间不存在"));
            return toRoomDTO(room, gameRoom);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSubmissionInNewTransaction(String playerId, QuestionEntity question,
                                               GameRoom gameRoom, String choice) {
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        GameEntity game = gameRepository.findById(gameRoom.getGameId())
                .orElseThrow(() -> new BusinessException("游戏不存在"));

        SubmissionEntity submission = SubmissionEntity.builder()
                .player(player)
                .question(question)
                .game(game)
                .choice(choice)
                .build();

        submissionRepository.save(submission);
    }

    private String getInternedRoomCode(String roomCode) {
        return roomCode.intern();
    }

    @Override
    @Transactional
    public RoomDTO setPlayerReady(String roomCode, String playerId, boolean ready) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        if (!player.getRoom().getRoomCode().equals(roomCode)) {
            throw new BusinessException("玩家不在该房间中");
        }

        player.setReady(ready);
        playerRepository.save(player);

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom != null) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));
        }

        log.info("玩家 {} 设置准备状态: {}", playerId, ready);
        if (gameRoom != null) {
            return toRoomDTO(room, gameRoom);
        }
        return null;
    }

    @Override
    public RoomDTO getRoomStatus(String roomCode) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间状态异常");
        }

        return toRoomDTO(room, gameRoom);
    }

    @Override
    public List<PlayerGameEntity> getGameResults(String roomCode) {
        GameEntity game = gameRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));

        return playerGameRepository.findByGameOrderByScoreDesc(game);
    }

    @Override
    public void removeRoom(String roomCode) {
        cancelQuestionTimeout(roomCode);
        activeRooms.remove(roomCode);
        advancing.remove(roomCode);
        log.info("移除房间: {}", roomCode);
    }

    @Override
    public List<RoomDTO> getAllActiveRoom() {
        return activeRooms.values().stream()
                .filter(gameRoom -> !gameRoom.isFinished())
                .map(gameRoom -> {
                    RoomEntity room = roomRepository.findByRoomCode(gameRoom.getRoomCode())
                            .orElse(null);
                    return toRoomDTO(room, gameRoom);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RoomDTO leaveRoom(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间状态异常");
        }

        synchronized (this.getInternedRoomCode(roomCode)) {
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            PlayerDTO leavingPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            String playerName = leavingPlayer != null ? leavingPlayer.getName() : "未知玩家";

            if (!gameRoom.isStarted()) {
                boolean isRoomOwner = !gameRoom.getPlayers().isEmpty() &&
                        gameRoom.getPlayers().get(0).getPlayerId().equals(playerId);

                if (isRoomOwner) {
                    removeRoom(roomCode);
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);

                    log.info("房主 {} 离开，房间 {} 已解散", playerName, roomCode);
                    return null;
                } else {
                    gameRoom.getPlayers().removeIf(p -> p.getPlayerId().equals(playerId));
                    gameRoom.getScores().remove(playerId);

                    PlayerEntity player = playerRepository.findByPlayerId(playerId).orElse(null);
                    if (player != null) {
                        player.setRoom(null);
                        playerRepository.save(player);
                    }

                    log.info("玩家 {} 离开房间 {}（游戏未开始）", playerName, roomCode);
                }

            } else {
                log.info("玩家 {} 离开房间 {}（游戏进行中，后续自动提交）", playerName, roomCode);

                long connectedCount = gameRoom.getPlayers().stream()
                        .filter(p -> !gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()))
                        .count();

                if (connectedCount == 0) {
                    finishGame(roomCode);
                    removeRoom(roomCode);
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);
                    log.info("所有玩家离开，房间 {} 已解散", roomCode);
                    return null;
                }
            }

            return toRoomDTO(room, gameRoom);
        }
    }

    @Override
    public RoomDTO reconnectRoom(String roomCode, String playerId) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            throw new BusinessException("房间状态异常");
        }

        synchronized (this.getInternedRoomCode(roomCode)) {
            if (gameRoom.getDisconnectedPlayers().containsKey(playerId)) {
                gameRoom.getDisconnectedPlayers().remove(playerId);

                gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst().ifPresent(player -> log.info("玩家 {} 重新连接到房间 {}", player.getName(), roomCode));

            }

            return toRoomDTO(room, gameRoom);
        }
    }

    @Override
    public void handlePlayerDisconnect(String roomCode, String playerId) {
        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null) {
            return;
        }

        synchronized (this.getInternedRoomCode(roomCode)) {
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst().ifPresent(player -> log.info("玩家 {} 从房间 {} 断开连接", player.getName(), roomCode));

        }
    }

    @Override
    @Transactional
    public void saveGameResult(String roomCode) {
        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null || !gameRoom.isFinished()) {
            return;
        }

        try {
            GameEntity game = gameRepository.findById(gameRoom.getGameId())
                    .orElseThrow(() -> new BusinessException("游戏不存在"));

            List<PlayerRankDTO> leaderboard = buildLeaderboard(gameRoom);
            List<QuestionDetailDTO> questionDetails = buildQuestionDetails(gameRoom);

            String leaderboardJson = objectMapper.writeValueAsString(leaderboard);
            String questionDetailsJson = objectMapper.writeValueAsString(questionDetails);

            GameResultEntity entity = GameResultEntity.builder()
                    .game(game)
                    .roomCode(roomCode)
                    .questionCount(gameRoom.getQuestions().size())
                    .playerCount(gameRoom.getPlayers().size())
                    .leaderboardJson(leaderboardJson)
                    .questionDetailsJson(questionDetailsJson)
                    .build();

            gameResultRepository.save(entity);
            log.info("游戏结果已保存: roomCode={}, gameId={}", roomCode, gameRoom.getGameId());

        } catch (Exception e) {
            log.error("保存游戏结果失败: roomCode={}", roomCode, e);
            throw new RuntimeException("保存游戏结果失败", e);
        }
    }

    // ==================== 私有方法 ====================

    private void advanceQuestionIfNeeded(GameRoom gameRoom, String reason, boolean fillDefaults) {
        String roomCode = gameRoom.getRoomCode();

        AtomicBoolean isAdvancing = advancing.computeIfAbsent(roomCode, k -> new AtomicBoolean(false));
        if (!isAdvancing.compareAndSet(false, true)) {
            log.warn("⚠️ 房间 {} 正在推进中，跳过", roomCode);
            return;
        }

        try {
            log.info("📊 推进房间 {} (原因: {})", roomCode, reason);

            // 1. 填充默认答案
            if (fillDefaults) {
                fillDefaultAnswersInNewTransaction(gameRoom);
            }

            // 2. 计算当前题目分数
            calculateCurrentQuestionScores(gameRoom);

            // 3. 重置玩家准备状态
            gameRoom.getPlayers().forEach(p -> p.setReady(false));

            QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
            if (currentQuestion == null) {
                log.warn("⚠️ 房间 {} 当前题目为空，结束游戏", roomCode);
                finishGame(roomCode);
                return;
            }

            QuestionScoringStrategy strategy = questionFactory.getStrategy(currentQuestion.getStrategyId());
            if (strategy == null) {
                log.error("❌ 房间 {} 无法获取题目 {} 的评分策略 {}",
                        roomCode, currentQuestion.getId(), currentQuestion.getStrategyId());
                finishGame(roomCode);
                return;
            }

            // 4. 检查是否是重复题，且是否还有剩余轮次
            boolean shouldAdvanceToNextQuestion = true;

            if (strategy instanceof RepeatableQuestionStrategy repeatStrategy) {
                int currentRound = getCurrentRound(roomCode, currentQuestion.getStrategyId());
                int totalRounds = repeatStrategy.getTotalRounds();

                log.info("🔄 房间 {} 题目 {} 当前轮次: {}/{}",
                        roomCode, currentQuestion.getStrategyId(), currentRound, totalRounds);

                // 🔥 判断是否完成：currentRound > totalRounds 才算完成
                if (currentRound <= totalRounds) {
                    shouldAdvanceToNextQuestion = false;
                    log.info("⏸️ 房间 {} 题目 {} 继续重复（当前轮次 {}/{}）",
                            roomCode, currentQuestion.getStrategyId(), currentRound, totalRounds);
                } else {
                    // 🔥 所有轮次已完成（currentRound > totalRounds）
                    clearRoomRounds(roomCode);
                    log.info("✅ 房间 {} 题目 {} 完成全部 {} 轮，准备下一题",
                            roomCode, currentQuestion.getStrategyId(), totalRounds);
                }
            }

            // 5. 推进题目或准备下一轮
            if (shouldAdvanceToNextQuestion) {
                // 推进到真正的下一题
                if (gameRoom.nextQuestion()) {
                    gameRoom.setQuestionStartTime(LocalDateTime.now());
                    scheduleQuestionTimeout(gameRoom, defaultQuestionTimeoutSeconds);
                    log.info("➡️ 房间 {} 推进到题目索引 {}", roomCode, gameRoom.getCurrentIndex());
                    broadcastRoomState(roomCode);
                } else {
                    // 没有更多题目，游戏结束
                    finishGame(roomCode);
                    log.info("🎉 房间 {} 所有题目完成，游戏结束", roomCode);
                }
            } else {
                // 重复题的下一轮（同一题，新的currentIndex）
                if (gameRoom.nextQuestion()) {
                    gameRoom.setQuestionStartTime(LocalDateTime.now());
                    scheduleQuestionTimeout(gameRoom, defaultQuestionTimeoutSeconds);
                    log.info("🔁 房间 {} 题目索引推进到 {}（重复题下一轮）", roomCode, gameRoom.getCurrentIndex());
                    broadcastRoomState(roomCode);
                } else {
                    // 异常情况：重复题还没完成但没有下一个index
                    log.error("❌ 房间 {} 重复题轮次未完成但无法推进 currentIndex", roomCode);
                    finishGame(roomCode);
                }
            }
        } finally {
            isAdvancing.set(false);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fillDefaultAnswersInNewTransaction(GameRoom gameRoom) {
        QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
        GameEntity game = gameRepository.findById(gameRoom.getGameId()).orElse(null);
        if (game == null) return;

        Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                .get(gameRoom.getCurrentIndex());

        for (PlayerDTO player : gameRoom.getPlayers()) {
            if (currentRoundSubmissions == null || !currentRoundSubmissions.containsKey(player.getPlayerId())) {
                String defaultChoice = currentQuestion.getDefaultChoice() != null
                        ? currentQuestion.getDefaultChoice()
                        : "4";

                PlayerEntity playerEntity = playerRepository.findByPlayerId(player.getPlayerId()).orElse(null);
                if (playerEntity == null) continue;

                SubmissionEntity submission = SubmissionEntity.builder()
                        .player(playerEntity)
                        .question(currentQuestion)
                        .game(game)
                        .choice(defaultChoice)
                        .build();

                submissionRepository.save(submission);

                gameRoom.getSubmissions()
                        .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                        .put(player.getPlayerId(), defaultChoice);

                log.info("⏰ 玩家 {} 超时，填充默认答案: {}", player.getName(), defaultChoice);
            }
        }
    }

    private void broadcastRoomState(String roomCode) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode).orElse(null);
        if (room != null) {
            GameRoom gameRoom = activeRooms.get(roomCode);
            if (gameRoom != null) {
                messagingTemplate.convertAndSend("/topic/room/" + roomCode, toRoomDTO(room, gameRoom));
            }
        }
    }

    private void calculateCurrentQuestionScores(GameRoom gameRoom) {
        QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
        int currentIndex = gameRoom.getCurrentIndex();
        Map<String, String> submissions = gameRoom.getSubmissions().get(currentIndex);

        if (submissions == null || submissions.isEmpty()) {
            log.warn("⚠️ 房间 {} 题目索引 {} 没有提交记录", gameRoom.getRoomCode(), currentIndex);
            return;
        }

        // 🔥 使用 GameRoom 中的持久化状态，而不是每次重新创建
        Map<String, PlayerGameState> playerStates = new HashMap<>();
        for (PlayerDTO player : gameRoom.getPlayers()) {
            int currentScore = gameRoom.getScores().getOrDefault(player.getPlayerId(), 0);

            // 🔥 从 GameRoom 获取或创建状态（会保留 customData）
            PlayerGameState state = gameRoom.getOrCreatePlayerState(
                    player.getPlayerId(),
                    player.getName(),
                    currentScore
            );

            // 🔥 更新总分（可能在上一轮增加了）
            state.setTotalScore(currentScore);

            playerStates.put(player.getPlayerId(), state);
        }

        // 构建游戏上下文
        GameContext context = GameContext.builder()
                .roomCode(gameRoom.getRoomCode())
                .currentQuestion(currentQuestion)
                .currentSubmissions(submissions)
                .playerStates(playerStates)
                .currentQuestionIndex(currentIndex)
                .build();

        // 获取策略并计算分数
        QuestionResult result;
        QuestionScoringStrategy strategy = questionFactory.getStrategy(currentQuestion.getStrategyId());

        if (strategy instanceof RepeatableQuestionStrategy repeatStrategy) {
            // 重复题：获取当前轮次（从1开始）
            int currentRound = getCurrentRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());

            log.info("💯 房间 {} 计算重复题分数: {} 第 {} 轮",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId(), currentRound);

            result = repeatStrategy.calculateRoundResult(context, currentRound);

            // 🔥 加这行日志
            log.info("📈 房间 {} 题目 {} 轮次递增: {} -> {}",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId(),
                    currentRound, currentRound + 1);

            incrementRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());

            // 🔥 再加这行，看 increment 后的值
            int afterRound = getCurrentRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());
            log.info("📊 房间 {} 题目 {} increment后轮次: {}",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId(), afterRound);
        } else {
            // 普通题
            log.info("💯 房间 {} 计算普通题分数: {}",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId());

            result = strategy.calculateResult(context);
        }

        // 应用分数到房间
        Map<String, GameRoom.QuestionScoreDetail> currentQuestionScores = new HashMap<>();

        for (Map.Entry<String, Integer> entry : result.getFinalScores().entrySet()) {
            String playerId = entry.getKey();
            Integer finalScore = entry.getValue();
            Integer baseScore = result.getBaseScores().getOrDefault(playerId, finalScore);

            // 累加到总分
            gameRoom.addScore(playerId, finalScore);

            // 🔥 同步更新 playerGameState 的总分
            gameRoom.updatePlayerStateTotalScore(playerId, gameRoom.getScores().get(playerId));

            // 更新玩家DTO的分数
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setScore(gameRoom.getScores().get(playerId)));

            // 记录本题得分详情
            currentQuestionScores.put(playerId, GameRoom.QuestionScoreDetail.builder()
                    .baseScore(baseScore)
                    .finalScore(finalScore)
                    .build());
        }

        gameRoom.getQuestionScores().put(currentIndex, currentQuestionScores);

        log.info("✅ 房间 {} 题目索引 {} 分数计算完成", gameRoom.getRoomCode(), currentIndex);
    }

    /**
     * 获取当前轮次（从1开始）
     * 第1次调用返回1，第2次返回2，以此类推
     */
    private int getCurrentRound(String roomCode, String strategyId) {
        Map<String, Integer> strategyRounds = roomStrategyRounds
                .computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>());

        // 🔥 使用 getOrDefault，而不是再次 computeIfAbsent
        int round = strategyRounds.getOrDefault(strategyId, 1);

        // 🔥 如果是第一次访问（默认值），写入到Map中
        if (!strategyRounds.containsKey(strategyId)) {
            strategyRounds.put(strategyId, 1);
        }

        return round;
    }

    /**
     * 增加轮次计数
     * 每次调用后，getCurrentRound会返回+1的值
     */
    private void incrementRound(String roomCode, String strategyId) {
        Map<String, Integer> strategyRounds = roomStrategyRounds
                .computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>());

        // 🔥 先获取当前值，如果不存在则初始化为1，然后+1
        int current = strategyRounds.getOrDefault(strategyId, 1);
        strategyRounds.put(strategyId, current + 1);
    }

    /**
     * 清理房间的所有轮次记录
     * 在重复题完成所有轮次后调用
     */
    private void clearRoomRounds(String roomCode) {
        roomStrategyRounds.remove(roomCode);
        log.debug("🧹 清理房间 {} 的轮次记录", roomCode);
    }

    @Transactional
    protected void finishGame(String roomCode) {
        GameRoom gameRoom = activeRooms.get(roomCode);
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        gameRoom.setFinished(true);
        gameRoom.clearPlayerStates();

        room.setStatus(RoomStatus.FINISHED);
        roomRepository.save(room);

        GameEntity game = gameRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

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

        saveGameResult(roomCode);
        clearRoomRounds(roomCode);

        RoomDTO roomDTO = toRoomDTO(room, gameRoom);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDTO);

        log.info("🎉 房间 {} 游戏结束，最终排名: {}", roomCode,
                buildLeaderboard(gameRoom).stream()
                        .limit(3)
                        .map(p -> p.getPlayerName() + ":" + p.getTotalScore())
                        .collect(Collectors.joining(", ")));
    }

    private void scheduleQuestionTimeout(GameRoom gameRoom, long seconds) {
        String roomCode = gameRoom.getRoomCode();

        ScheduledFuture<?> existingTimeout = questionTimeouts.get(roomCode);
        if (existingTimeout != null && !existingTimeout.isDone()) {
            existingTimeout.cancel(false);
        }

        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            synchronized (this.getInternedRoomCode(roomCode)) {
                GameRoom room = activeRooms.get(roomCode);
                if (room != null && room.isStarted()) {
                    advanceQuestionIfNeeded(room, "timeout", true);
                }
            }
        }, seconds, TimeUnit.SECONDS);

        questionTimeouts.put(roomCode, timeout);
    }

    private void cancelQuestionTimeout(String roomCode) {
        ScheduledFuture<?> future = questionTimeouts.remove(roomCode);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private RoomDTO toRoomDTO(RoomEntity roomEntity, GameRoom gameRoom) {
        RoomStatus status = RoomStatus.WAITING;
        if (gameRoom.isFinished()) {
            status = RoomStatus.FINISHED;
        } else if (gameRoom.isStarted()) {
            status = RoomStatus.PLAYING;
        }

        QuestionDTO currentQuestionDTO = null;
        if (gameRoom.getCurrentQuestion() != null) {
            currentQuestionDTO = DTOConverter.toQuestionDTOWithConfig(
                    gameRoom.getCurrentQuestion(),
                    choiceConfigRepository,
                    bidConfigRepository
            );
        }

        Integer questionCount = null;
        if (gameRoom.getQuestions() != null && !gameRoom.getQuestions().isEmpty()) {
            questionCount = gameRoom.getQuestions().size();
        } else if (roomEntity != null && roomEntity.getQuestionCount() != null) {
            questionCount = roomEntity.getQuestionCount();
        } else {
            questionCount = 10;
        }

        // 🔥 解析 winConditionsJson
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
                .currentQuestion(currentQuestionDTO)
                .questionCount(questionCount)
                // 🔥 新增字段
                .rankingMode(roomEntity != null ? roomEntity.getRankingMode() : "standard")
                .targetScore(roomEntity != null ? roomEntity.getTargetScore() : null)
                .winConditions(winConditions)
                .build();
    }

    private List<PlayerRankDTO> buildLeaderboard(GameRoom gameRoom) {
        // 🔥 获取房间配置
        RoomEntity roomEntity = roomRepository.findByRoomCode(gameRoom.getRoomCode()).orElse(null);
        String rankingMode = roomEntity != null ? roomEntity.getRankingMode() : "standard";
        Integer targetScore = roomEntity != null ? roomEntity.getTargetScore() : null;

        // 🔥 解析通关条件
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

        // 1️⃣ 构建玩家列表
        List<PlayerRankDTO> leaderboard = gameRoom.getPlayers().stream()
                .map(player -> PlayerRankDTO.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getName())
                        .totalScore(gameRoom.getScores().getOrDefault(player.getPlayerId(), 0))
                        .build())
                .collect(Collectors.toList());

        // 2️⃣ 根据排名模式排序
        switch (rankingMode) {
            case "closest_to_avg": {
                // 计算平均分
                double avgScore = leaderboard.stream()
                        .mapToInt(PlayerRankDTO::getTotalScore)
                        .average()
                        .orElse(0.0);

                log.info("📊 房间 {} 使用接近平均分排名，平均分: {}", gameRoom.getRoomCode(), avgScore);

                // 按离平均分的绝对差值排序
                leaderboard.sort(Comparator.comparingDouble(p ->
                        Math.abs(p.getTotalScore() - avgScore)
                ));
                break;
            }
            case "closest_to_target": {
                if (targetScore == null) {
                    log.warn("⚠️ 房间 {} 排名模式为 closest_to_target 但未设置目标分，使用标准排名",
                            gameRoom.getRoomCode());
                    leaderboard.sort(Comparator.comparing(PlayerRankDTO::getTotalScore).reversed());
                } else {
                    log.info("📊 房间 {} 使用接近目标分排名，目标分: {}", gameRoom.getRoomCode(), targetScore);

                    // 按离目标分的绝对差值排序
                    leaderboard.sort(Comparator.comparingInt(p ->
                            Math.abs(p.getTotalScore() - targetScore)
                    ));
                }
                break;
            }
            case "standard":
            default:
                // 标准排名：分数降序
                leaderboard.sort(Comparator.comparing(PlayerRankDTO::getTotalScore).reversed());
                break;
        }

        // 3️⃣ 分配排名（处理并列）
        for (int i = 0; i < leaderboard.size(); i++) {
            leaderboard.get(i).setRank(i + 1);
        }

        // 4️⃣ 判断是否通关
        boolean passed = checkWinConditions(leaderboard, winConditions);

        // 🔥 设置每个玩家的通关状态
        for (PlayerRankDTO player : leaderboard) {
            player.setPassed(passed);
        }

        if (!passed && winConditions != null) {
            log.warn("❌ 房间 {} 未达成通关条件", gameRoom.getRoomCode());
        } else {
            log.info("✅ 房间 {} 通关成功！", gameRoom.getRoomCode());
        }

        return leaderboard;
    }

    /**
     * 检查是否达成通关条件
     */
    private boolean checkWinConditions(List<PlayerRankDTO> leaderboard,
                                       RoomDTO.WinConditions conditions) {
        if (conditions == null) {
            return true; // 无条件限制，默认通关
        }

        // 检查：所有人最低分
        if (conditions.getMinScorePerPlayer() != null) {
            boolean allPass = leaderboard.stream()
                    .allMatch(p -> p.getTotalScore() >= conditions.getMinScorePerPlayer());
            if (!allPass) {
                log.info("❌ 未达成条件：所有人 ≥ {} 分", conditions.getMinScorePerPlayer());
                return false;
            }
        }

        // 检查：团队总分
        if (conditions.getMinTotalScore() != null) {
            int totalScore = leaderboard.stream()
                    .mapToInt(PlayerRankDTO::getTotalScore)
                    .sum();
            if (totalScore < conditions.getMinTotalScore()) {
                log.info("❌ 未达成条件：总分 {} < {}", totalScore, conditions.getMinTotalScore());
                return false;
            }
        }

        // 检查：平均分
        if (conditions.getMinAvgScore() != null) {
            double avgScore = leaderboard.stream()
                    .mapToInt(PlayerRankDTO::getTotalScore)
                    .average()
                    .orElse(0.0);
            if (avgScore < conditions.getMinAvgScore()) {
                log.info("❌ 未达成条件：平均分 {} < {}", avgScore, conditions.getMinAvgScore());
                return false;
            }
        }

        return true; // 所有条件都满足
    }

    private List<QuestionDetailDTO> buildQuestionDetails(GameRoom gameRoom) {
        List<QuestionDetailDTO> details = new ArrayList<>();

        for (int i = 0; i < gameRoom.getQuestions().size(); i++) {
            QuestionEntity question = gameRoom.getQuestions().get(i);
            Map<String, String> submissions = gameRoom.getSubmissions().get(i);

            if (submissions == null) {
                continue;
            }

            Map<String, Integer> choiceCounts = new HashMap<>();
            for (String choice : submissions.values()) {
                choiceCounts.put(choice, choiceCounts.getOrDefault(choice, 0) + 1);
            }

            Map<String, GameRoom.QuestionScoreDetail> questionScores =
                    gameRoom.getQuestionScores().getOrDefault(i, new HashMap<>());

            List<PlayerSubmissionDTO> playerSubmissions = new ArrayList<>();
            for (Map.Entry<String, String> entry : submissions.entrySet()) {
                String playerId = entry.getKey();
                String choice = entry.getValue();

                PlayerDTO player = gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .orElse(null);

                if (player != null) {
                    GameRoom.QuestionScoreDetail scoreDetail = questionScores.get(playerId);
                    Integer baseScore = scoreDetail != null ? scoreDetail.getBaseScore() : 0;
                    Integer finalScore = scoreDetail != null ? scoreDetail.getFinalScore() : 0;

                    playerSubmissions.add(PlayerSubmissionDTO.builder()
                            .playerId(playerId)
                            .playerName(player.getName())
                            .choice(choice)
                            .baseScore(baseScore)
                            .finalScore(finalScore)
                            .submittedAt(null)
                            .build());
                }
            }

            String optionText = formatOptions(question);

            details.add(QuestionDetailDTO.builder()
                    .questionIndex(i)
                    .questionText(question.getText())
                    .optionText(optionText)
                    .questionType(question.getType())
                    .playerSubmissions(playerSubmissions)
                    .choiceCounts(choiceCounts)
                    .build());
        }

        return details;
    }

    private String formatOptions(QuestionEntity question) {
        if (question == null) {
            return "题目数据错误";
        }

        if ("bid".equals(question.getType())) {
            return bidConfigRepository.findByQuestionId(question.getId())
                    .map(config -> "出价范围: " + config.getMinValue() + "-" + config.getMaxValue())
                    .orElse("自由出价");
        }

        if ("choice".equals(question.getType())) {
            return choiceConfigRepository.findByQuestionId(question.getId())
                    .map(config -> {
                        try {
                            List<QuestionOption> options = objectMapper.readValue(
                                    config.getOptionsJson(),
                                    new TypeReference<List<QuestionOption>>() {}
                            );

                            return options.stream()
                                    .sorted(Comparator.comparing(QuestionOption::getKey))
                                    .map(option -> option.getKey() + ". " + option.getText())
                                    .collect(Collectors.joining(" | "));

                        } catch (Exception e) {
                            log.error("解析选项 JSON 失败: {}", e.getMessage());
                            return "选项格式错误";
                        }
                    })
                    .orElse("无选项");
        }

        return "无选项";
    }
}