package org.example.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.*;
import org.example.repository.*;
import org.example.service.GameService;
import org.example.service.QuesService;
import org.example.service.QuestionFactory;
import org.example.service.QuestionSelectorService;
import org.example.utils.DTOConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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
    private final QuesService questionService;
    private final SubmissionRepository submissionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameResultRepository gameResultRepository;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;
    private final QuestionSelectorService questionSelector;

    // 活跃房间（内存/Redis 存储）
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();

    // 定时任务调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

    // 题目超时定时器
    private final Map<String, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();

    // 推进锁（防止并发推进）
    private final Map<String, AtomicBoolean> advancing = new ConcurrentHashMap<>();

    // 默认答题时间
    private final long defaultQuestionTimeoutSeconds = 30L;

    // ==================== 公开方法 ====================

    @Override
    @Transactional
    public RoomDTO createRoom(Integer maxPlayers, Integer questionCount) {
        String roomCode = generateRoomCode();

        // 创建房间实体（数据库）
        RoomEntity roomEntity = RoomEntity.builder()
                .roomCode(roomCode)
                .status(RoomStatus.WAITING)
                .maxPlayers(maxPlayers)
                .questionCount(questionCount)
                .build();
        RoomEntity savedRoom = roomRepository.save(roomEntity);

        // 创建游戏房间（内存）
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

        activeRooms.put(roomCode, gameRoom);
        advancing.put(roomCode, new AtomicBoolean(false));

        log.info("创建房间: {}, 最大人数: {}, 题目数: {}", roomCode, maxPlayers, questionCount);
        return toRoomDTO(savedRoom, gameRoom);
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

        synchronized (gameRoom) {
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
                // ✅ 使用 playerId 查询或创建玩家
                PlayerEntity player = playerRepository.findByPlayerId(playerId)
                        .orElse(null);

                if (player == null) {
                    // 第一次加入，创建玩家实体
                    player = PlayerEntity.builder()
                            .playerId(playerId)
                            .name(playerName)
                            .ready(false)
                            .room(room)
                            .build();
                } else {
                    // 已存在，更新房间
                    player.setRoom(room);
                    player.setReady(false);
                }
                playerRepository.save(player);

                // 添加到内存房间
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

        synchronized (gameRoom) {
            if (gameRoom.isStarted()) {
                return toRoomDTO(room, gameRoom);
            }

            // 更新房间状态
            room.setStatus(RoomStatus.PLAYING);
            roomRepository.save(room);

            // 创建游戏记录
            GameEntity game = GameEntity.builder()
                    .roomCode(roomCode)
                    .startTime(LocalDateTime.now())
                    .build();
            GameEntity savedGame = gameRepository.save(game);

            // 创建玩家-游戏关联记录
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

            // 初始化游戏数据
            List<QuestionEntity> questions = questionSelector.selectQuestions(
                    room.getQuestionCount(),           // 总题数
                    gameRoom.getPlayers().size()       // 玩家数
            );
            gameRoom.setQuestions(questions);
            gameRoom.setGameId(savedGame.getId());
            gameRoom.setStarted(true);
            gameRoom.setCurrentIndex(0);
            gameRoom.setQuestionStartTime(LocalDateTime.now());
            gameRoom.setTimeLimit(30);

            // 启动超时定时器
            scheduleQuestionTimeout(gameRoom, defaultQuestionTimeoutSeconds);

            log.info("房间 {} 开始游戏，题目数: {}, 玩家数: {}",
                    roomCode, questions.size(), gameRoom.getPlayers().size());
            return toRoomDTO(room, gameRoom);
        }
    }

    @Transactional
    @Override
    public RoomDTO submitAnswer(String roomCode, String playerId, String choice, boolean force) {
        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom == null || !gameRoom.isStarted()) {
            throw new BusinessException("游戏未开始");
        }

        QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
        if (currentQuestion == null) {
            throw new BusinessException("当前没有有效题目");
        }

        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        // ✅ 使用 playerId 查询玩家
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        GameEntity game = gameRepository.findById(gameRoom.getGameId())
                .orElseThrow(() -> new BusinessException("游戏不存在"));

        // 检查是否已提交
        Optional<SubmissionEntity> existingSubmission = submissionRepository
                .findByPlayerAndQuestionAndGame(player, currentQuestion, game);

        if (existingSubmission.isPresent()) {
            throw new BusinessException("已经提交过答案");
        }

        // 保存提交记录
        SubmissionEntity submission = SubmissionEntity.builder()
                .player(player)
                .question(currentQuestion)
                .game(game)
                .choice(choice)
                .build();
        submissionRepository.save(submission);

        // 更新内存数据
        gameRoom.getSubmissions()
                .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                .put(playerId, choice);

        // 标记玩家已提交
        gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(true));

        // 检查是否所有人都已提交
        boolean allSubmitted = gameRoom.getPlayers().stream()
                .allMatch(p -> gameRoom.getSubmissions()
                        .get(gameRoom.getCurrentIndex())
                        .containsKey(p.getPlayerId()));

        if (allSubmitted || force) {
            cancelQuestionTimeout(roomCode);
            advanceQuestionIfNeeded(gameRoom, force ? "force" : "allSubmitted", force);
        }

        log.info("玩家 {} 在房间 {} 提交答案: {}", playerId, roomCode, choice);

        return toRoomDTO(room, gameRoom);
    }

    @Override
    @Transactional
    public RoomDTO setPlayerReady(String roomCode, String playerId, boolean ready) {
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        // ✅ 使用 playerId 查询
        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        if (!player.getRoom().getRoomCode().equals(roomCode)) {
            throw new BusinessException("玩家不在该房间中");
        }

        player.setReady(ready);
        playerRepository.save(player);

        // 同步更新内存
        GameRoom gameRoom = activeRooms.get(roomCode);
        if (gameRoom != null) {
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setReady(ready));
        }

        log.info("玩家 {} 设置准备状态: {}", playerId, ready);
        return toRoomDTO(room, gameRoom);
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

        synchronized (gameRoom) {
            // 记录断线（替代 leftPlayers）
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            PlayerDTO leavingPlayer = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            String playerName = leavingPlayer != null ? leavingPlayer.getName() : "未知玩家";

            if (!gameRoom.isStarted()) {
                // 游戏未开始，直接移除玩家
                boolean isRoomOwner = !gameRoom.getPlayers().isEmpty() &&
                        gameRoom.getPlayers().get(0).getPlayerId().equals(playerId);

                if (isRoomOwner) {
                    // 房主离开，解散房间
                    removeRoom(roomCode);
                    room.setStatus(RoomStatus.FINISHED);
                    roomRepository.save(room);

                    log.info("房主 {} 离开，房间 {} 已解散", playerName, roomCode);
                    return null;
                } else {
                    // 普通玩家离开
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
                // 游戏进行中，标记为断线但不移除
                log.info("玩家 {} 离开房间 {}（游戏进行中，后续自动提交）", playerName, roomCode);

                // 如果所有人都离开了，解散房间
                long connectedCount = gameRoom.getPlayers().stream()
                        .filter(p -> !gameRoom.getDisconnectedPlayers().containsKey(p.getPlayerId()))
                        .count();

                if (connectedCount == 0) {
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

        synchronized (gameRoom) {
            if (gameRoom.getDisconnectedPlayers().containsKey(playerId)) {
                gameRoom.getDisconnectedPlayers().remove(playerId);

                PlayerDTO player = gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .orElse(null);

                if (player != null) {
                    log.info("玩家 {} 重新连接到房间 {}", player.getName(), roomCode);
                }
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

        synchronized (gameRoom) {
            gameRoom.getDisconnectedPlayers().put(playerId, LocalDateTime.now());

            PlayerDTO player = gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .orElse(null);

            if (player != null) {
                log.info("玩家 {} 从房间 {} 断开连接", player.getName(), roomCode);
            }
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
                    .roomCode(roomCode)  // ← 修复1：添加 roomCode
                    .questionCount(gameRoom.getQuestions().size())
                    .playerCount(gameRoom.getPlayers().size())
                    .leaderboardJson(leaderboardJson)
                    .questionDetailsJson(questionDetailsJson)
                    .build();

            gameResultRepository.save(entity);
            log.info("游戏结果已保存: roomCode={}, gameId={}", roomCode, gameRoom.getGameId());

        } catch (Exception e) {
            log.error("保存游戏结果失败: roomCode={}", roomCode, e);
            // ← 修复2：抛出异常，中断事务
            throw new RuntimeException("保存游戏结果失败", e);
        }
    }

    // ==================== 私有方法 ====================

    private void advanceQuestionIfNeeded(GameRoom gameRoom, String reason, boolean fillDefaults) {
        String roomCode = gameRoom.getRoomCode();
        AtomicBoolean isAdvancing = advancing.get(roomCode);

        if (isAdvancing != null && !isAdvancing.compareAndSet(false, true)) {
            return;
        }

        try {
            log.info("推进房间 {} 到下一题，原因: {}", roomCode, reason);

            if (fillDefaults) {
                fillDefaultAnswers(gameRoom);
            }

            calculateCurrentQuestionScores(gameRoom);

            // 重置所有玩家的准备状态
            gameRoom.getPlayers().forEach(p -> p.setReady(false));

            if (gameRoom.nextQuestion()) {
                // 推进到下一题
                gameRoom.setQuestionStartTime(LocalDateTime.now());
                scheduleQuestionTimeout(gameRoom, defaultQuestionTimeoutSeconds);
                log.info("房间 {} 推进到第 {} 题", roomCode, gameRoom.getCurrentIndex() + 1);

                // 推送房间更新
                RoomEntity room = roomRepository.findByRoomCode(roomCode).orElse(null);
                if (room != null) {
                    RoomDTO roomDTO = toRoomDTO(room, gameRoom);
                    messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDTO);
                }

            } else {
                // 游戏结束
                finishGame(gameRoom.getRoomCode());
                log.info("房间 {} 游戏结束", roomCode);
            }
        } finally {
            if (isAdvancing != null) {
                isAdvancing.set(false);
            }
        }
    }

    private void fillDefaultAnswers(GameRoom gameRoom) {
        int currentIndex = gameRoom.getCurrentIndex();
        Map<String, String> submissions = gameRoom.getSubmissions()
                .computeIfAbsent(currentIndex, k -> new ConcurrentHashMap<>());

        QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
        String defaultChoice = currentQuestion.getDefaultChoice();

        for (PlayerDTO player : gameRoom.getPlayers()) {
            if (!submissions.containsKey(player.getPlayerId())) {
                submissions.put(player.getPlayerId(), defaultChoice != null ? defaultChoice : "A");

                if (gameRoom.getDisconnectedPlayers().containsKey(player.getPlayerId())) {
                    log.info("玩家 {} 未提交，自动填充默认答案: {}",
                            player.getName(), defaultChoice);
                }
            }
        }
    }

    private void calculateCurrentQuestionScores(GameRoom gameRoom) {
        QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
        int currentIndex = gameRoom.getCurrentIndex();
        Map<String, String> submissions = gameRoom.getSubmissions().get(currentIndex);

        if (submissions == null || submissions.isEmpty()) {
            return;
        }

        // 构建玩家状态
        Map<String, PlayerGameState> playerStates = new HashMap<>();
        for (PlayerDTO player : gameRoom.getPlayers()) {
            PlayerGameState state = PlayerGameState.builder()
                    .playerId(player.getPlayerId())
                    .name(player.getName())
                    .totalScore(gameRoom.getScores().getOrDefault(player.getPlayerId(), 0))
                    .activeBuffs(new ArrayList<>())
                    .customData(new HashMap<>())
                    .build();
            playerStates.put(player.getPlayerId(), state);
        }

        // 构建计分上下文
        GameContext context = GameContext.builder()
                .roomCode(gameRoom.getRoomCode())
                .currentQuestion(currentQuestion)
                .currentSubmissions(submissions)
                .playerStates(playerStates)
                .currentQuestionIndex(currentIndex)
                .build();

        // 调用计分策略
        QuestionResult result = questionFactory.calculateScores(context);

        // 更新分数
        Map<String, GameRoom.QuestionScoreDetail> currentQuestionScores = new HashMap<>();

        for (Map.Entry<String, Integer> entry : result.getFinalScores().entrySet()) {
            String playerId = entry.getKey();
            Integer finalScore = entry.getValue();
            Integer baseScore = result.getBaseScores().getOrDefault(playerId, finalScore);

            gameRoom.addScore(playerId, finalScore);

            // 更新玩家DTO的分数
            gameRoom.getPlayers().stream()
                    .filter(p -> p.getPlayerId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> p.setScore(gameRoom.getScores().get(playerId)));

            currentQuestionScores.put(playerId, GameRoom.QuestionScoreDetail.builder()
                    .baseScore(baseScore)
                    .finalScore(finalScore)
                    .build());
        }

        gameRoom.getQuestionScores().put(currentIndex, currentQuestionScores);
    }

    @Transactional
    protected void finishGame(String roomCode) {
        GameRoom gameRoom = activeRooms.get(roomCode);
        RoomEntity room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("房间不存在"));

        gameRoom.setFinished(true);

        // 更新房间状态
        room.setStatus(RoomStatus.FINISHED);
        roomRepository.save(room);

        // 更新游戏记录
        GameEntity game = gameRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));
        game.setEndTime(LocalDateTime.now());
        gameRepository.save(game);

        // 更新玩家-游戏记录的分数
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

        // 保存游戏结果
        saveGameResult(roomCode);

        // 推送最终状态
        RoomDTO roomDTO = toRoomDTO(room, gameRoom);
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, roomDTO);

        log.info("房间 {} 游戏结束，最终排名: {}", roomCode,
                buildLeaderboard(gameRoom).stream()
                        .map(p -> p.getPlayerName() + ":" + p.getTotalScore())
                        .collect(Collectors.joining(", ")));
    }

    private void scheduleQuestionTimeout(GameRoom gameRoom, long seconds) {
        cancelQuestionTimeout(gameRoom.getRoomCode());
        Runnable timeoutTask = () -> {
            try {
                advanceQuestionIfNeeded(gameRoom, "timeout", true);
            } catch (Exception e) {
                log.error("题目超时处理异常，房间: {}", gameRoom.getRoomCode(), e);
            }
        };
        ScheduledFuture<?> future = scheduler.schedule(timeoutTask, seconds, TimeUnit.SECONDS);
        roomTimers.put(gameRoom.getRoomCode(), future);
    }

    private void cancelQuestionTimeout(String roomCode) {
        ScheduledFuture<?> future = roomTimers.remove(roomCode);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    private String generateRoomCode() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private RoomDTO toRoomDTO(RoomEntity roomEntity, GameRoom gameRoom) {
        // 确定房间状态
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
                .currentQuestion(currentQuestionDTO)  // ✅ 使用转换器
                .questionCount(gameRoom.getQuestions() != null ? gameRoom.getQuestions().size() : 0)
                .build();
    }

    private List<PlayerRankDTO> buildLeaderboard(GameRoom gameRoom) {
        List<PlayerRankDTO> leaderboard = gameRoom.getPlayers().stream()
                .map(player -> PlayerRankDTO.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getName())
                        .totalScore(gameRoom.getScores().getOrDefault(player.getPlayerId(), 0))
                        .build())
                .sorted(Comparator.comparing(PlayerRankDTO::getTotalScore).reversed())
                .collect(Collectors.toList());

        // 设置排名
        for (int i = 0; i < leaderboard.size(); i++) {
            leaderboard.get(i).setRank(i + 1);
        }

        return leaderboard;
    }

    private List<QuestionDetailDTO> buildQuestionDetails(GameRoom gameRoom) {
        List<QuestionDetailDTO> details = new ArrayList<>();

        for (int i = 0; i < gameRoom.getQuestions().size(); i++) {
            QuestionEntity question = gameRoom.getQuestions().get(i);
            Map<String, String> submissions = gameRoom.getSubmissions().get(i);

            if (submissions == null) {
                continue;
            }

            // 统计选择分布
            Map<String, Integer> choiceCounts = new HashMap<>();
            for (String choice : submissions.values()) {
                choiceCounts.put(choice, choiceCounts.getOrDefault(choice, 0) + 1);
            }

            // 获取该题的得分详情
            Map<String, GameRoom.QuestionScoreDetail> questionScores =
                    gameRoom.getQuestionScores().getOrDefault(i, new HashMap<>());

            // 构建玩家提交列表
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
                            .submittedAt(null)  // TODO: 可以从 SubmissionEntity 中获取
                            .build());
                }
            }

            // 格式化选项文本
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
            // 查询 bid 配置
            return bidConfigRepository.findByQuestionId(question.getId())
                    .map(config -> "出价范围: " + config.getMinValue() + "-" + config.getMaxValue())
                    .orElse("自由出价");
        }

        if ("choice".equals(question.getType())) {
            // 查询 choice 配置
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