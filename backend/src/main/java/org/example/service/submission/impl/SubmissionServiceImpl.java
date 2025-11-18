package org.example.service.submission.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.entity.QuestionType;
import org.example.repository.GameRepository;
import org.example.repository.PlayerRepository;
import org.example.repository.QuestionRepository;
import org.example.repository.SubmissionRepository;
import org.example.service.cache.RoomCache;
import org.example.service.submission.SubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 答题提交服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionServiceImpl implements SubmissionService {

    private final RoomCache roomCache;
    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;
    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;

    @Override
    @Transactional(timeout = 10)  // 🔥 P0-4修复：添加10秒超时，防止长时间占用连接
    public void submitAnswer(String roomCode, String playerId, String choice) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();  // ✅ DTO

        if (currentQuestion == null) {
            throw new BusinessException("当前没有有效题目");
        }

        // 🔥 检查是否是观战者
        boolean isSpectator = gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .map(PlayerDTO::getSpectator)
                .orElse(false);

        if (isSpectator) {
            throw new BusinessException("观战者不能提交答案");
        }

        // 🔥 Bot 玩家：只更新内存，不保存到数据库
        boolean isBot = playerId.startsWith("BOT_");

        if (!isBot) {
            // 🔥 真实玩家：保存到数据库
            QuestionEntity questionEntity = questionRepository.findById(currentQuestion.getId())
                    .orElseThrow(() -> new BusinessException("题目不存在: " + currentQuestion.getId()));

            PlayerEntity player = playerRepository.findByPlayerId(playerId)
                    .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

            GameEntity game = gameRepository.findById(gameRoom.getGameId())
                    .orElseThrow(() -> new BusinessException("游戏不存在"));

            SubmissionEntity submission = SubmissionEntity.builder()
                    .player(player)
                    .question(questionEntity)
                    .game(game)
                    .choice(choice)
                    .build();

            submissionRepository.save(submission);
        }

        // 更新内存状态（Bot 和真实玩家都需要）
        gameRoom.getSubmissions()
                .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                .put(playerId, choice);

        // 记录提交时间
        if (gameRoom.getCurrentContext() != null) {
            gameRoom.getCurrentContext().recordSubmissionTime(playerId);
        }

        // 标记玩家已提交
        gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(true));

        log.info("💾 玩家 {} 提交答案: {} {}", playerId, choice, isBot ? "(Bot)" : "");
    }

    @Override
    @Transactional(timeout = 10)  // 🔥 P0-4修复：添加10秒超时，防止长时间占用连接
    public void fillDefaultAnswers(GameRoom gameRoom) {
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();
        if (currentQuestion == null) {
            log.warn("⚠️ 当前题目为空，无法填充默认答案");
            return;
        }

        // 查询 Entity
        QuestionEntity questionEntity = questionRepository.findById(currentQuestion.getId())
                .orElseThrow(() -> new BusinessException("题目不存在: " + currentQuestion.getId()));

        GameEntity game = gameRepository.findById(gameRoom.getGameId())
                .orElseThrow(() -> new BusinessException("游戏不存在"));

        Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                .get(gameRoom.getCurrentIndex());

        // 🔥 修改：遍历所有玩家（包括断线的），但跳过观战者和Bot
        for (PlayerDTO player : gameRoom.getPlayers()) {
            // 🔥 跳过观战者
            if (Boolean.TRUE.equals(player.getSpectator())) {
                continue;
            }

            String playerId = player.getPlayerId();

            // 🔥 跳过 Bot 玩家（Bot 应该已经提交了）
            if (playerId.startsWith("BOT_")) {
                continue;
            }

            // 🔥 检查是否已提交
            if (currentRoundSubmissions == null || !currentRoundSubmissions.containsKey(playerId)) {

                // 获取默认答案
                String defaultChoice = currentQuestion.getDefaultChoice() != null
                        ? currentQuestion.getDefaultChoice()
                        : "4";

                PlayerEntity playerEntity = playerRepository.findByPlayerId(playerId)
                        .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

                // 保存到数据库
                SubmissionEntity submission = SubmissionEntity.builder()
                        .player(playerEntity)
                        .question(questionEntity)
                        .game(game)
                        .choice(defaultChoice)
                        .build();

                submissionRepository.save(submission);

                // 保存到内存
                gameRoom.getSubmissions()
                        .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                        .put(playerId, defaultChoice);

                // 🔥 添加：标记玩家状态
                boolean isDisconnected = gameRoom.getDisconnectedPlayers().containsKey(playerId);
                log.info("📝 为玩家 {} 填充默认答案: {} {}",
                        player.getName(),
                        defaultChoice,
                        isDisconnected ? "(断线)" : "(超时)");
            }
        }

        // 🔥 添加：日志统计
        int filledCount = gameRoom.getPlayers().size() - (currentRoundSubmissions != null ? currentRoundSubmissions.size() : 0);
        if (filledCount > 0) {
            log.info("✅ 已为 {} 个玩家填充默认答案", filledCount);
        }
    }

    @Override
    public boolean allSubmitted(GameRoom gameRoom) {
        Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                .get(gameRoom.getCurrentIndex());

        if (currentRoundSubmissions == null) {
            return false;
        }

        // 🔥 只检查非观战者玩家
        return gameRoom.getPlayers().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getSpectator()))
                .allMatch(p -> currentRoundSubmissions.containsKey(p.getPlayerId()));
    }

    @Override
    /*
      自动为Bot提交随机答案
     */
    public void autoSubmitBots(GameRoom gameRoom) {
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();
        if (currentQuestion == null) {
            return;
        }

        Random random = new Random();
        int currentIndex = gameRoom.getCurrentIndex();
        Map<String, String> currentSubmissions = gameRoom.getSubmissions()
                .computeIfAbsent(currentIndex, k -> new HashMap<>());

        // 为所有Bot提交答案
        gameRoom.getPlayers().stream()
                .filter(player -> player.getPlayerId().startsWith("BOT_"))
                .forEach(bot -> {
                    // 如果Bot还没提交，生成随机答案
                    if (!currentSubmissions.containsKey(bot.getPlayerId())) {
                        String botAnswer;

                        if (currentQuestion.getType() == QuestionType.CHOICE) {
                            // CHOICE题：随机选择一个选项
                            List<String> options = Optional.ofNullable(currentQuestion.getOptions())
                                    .orElse(Collections.emptyList())
                                    .stream()
                                    .map(QuestionOption::getKey)
                                    .toList();
                            if (options != null && !options.isEmpty()) {
                                botAnswer = options.get(random.nextInt(options.size()));
                            } else {
                                botAnswer = "A";  // 默认选项
                            }
                        } else if (currentQuestion.getType() == QuestionType.BID) {
                            // BID题：在范围内随机数
                            Integer min = currentQuestion.getMin();
                            Integer max = currentQuestion.getMax();
                            Integer step = currentQuestion.getStep();
                            if (min != null && max != null) {
                                botAnswer = String.valueOf((random.nextInt((max - min) / step + 1) * step) + min);
                            } else {
                                botAnswer = "5";  // 默认值
                            }
                        } else {
                            botAnswer = "A";  // 未知题型默认
                        }

                        // 提交Bot答案
                        submitAnswer(gameRoom.getRoomCode(), bot.getPlayerId(), botAnswer);
                        log.info("Bot {} 自动提交答案: {}", bot.getName(), botAnswer);
                    }
                });
    }

}