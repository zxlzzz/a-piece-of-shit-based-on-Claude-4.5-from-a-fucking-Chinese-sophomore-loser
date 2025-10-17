package org.example.service.scoring.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.QuestionEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameContext;
import org.example.pojo.GameRoom;
import org.example.pojo.PlayerGameState;
import org.example.pojo.QuestionResult;
import org.example.service.QuestionFactory;
import org.example.service.QuestionScoringStrategy;
import org.example.service.strategy.QR.RepeatableQuestionStrategy;
import org.example.service.cache.RoomCache;
import org.example.service.scoring.ScoringResult;
import org.example.service.scoring.ScoringService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分数计算服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoringServiceImpl implements ScoringService {

    private final RoomCache roomCache;
    private final QuestionFactory questionFactory;

    /**
     * 轮次追踪器
     * 外层键：roomCode
     * 内层键：strategyId
     * 值：当前轮次（从1开始）
     */
    private final Map<String, Map<String, Integer>> roomStrategyRounds = new ConcurrentHashMap<>();

    @Override
    public ScoringResult calculateScores(GameRoom gameRoom) {
        QuestionEntity currentQuestion = gameRoom.getCurrentQuestion();
        int currentIndex = gameRoom.getCurrentIndex();
        Map<String, String> submissions = gameRoom.getSubmissions().get(currentIndex);

        if (submissions == null || submissions.isEmpty()) {
            log.warn("⚠️ 房间 {} 题目索引 {} 没有提交记录", gameRoom.getRoomCode(), currentIndex);
            return ScoringResult.builder()
                    .baseScores(new HashMap<>())
                    .finalScores(new HashMap<>())
                    .scoreDetails(new HashMap<>())
                    .repeatableQuestion(false)
                    .currentRound(0)
                    .totalRounds(0)
                    .build();
        }

        // 1. 构建玩家状态
        Map<String, PlayerGameState> playerStates = new HashMap<>();
        gameRoom.getPlayers().forEach(player -> {
            int currentScore = gameRoom.getScores().getOrDefault(player.getPlayerId(), 0);
            PlayerGameState state = gameRoom.getOrCreatePlayerState(
                    player.getPlayerId(),
                    player.getName(),
                    currentScore
            );
            state.setTotalScore(currentScore);
            playerStates.put(player.getPlayerId(), state);
        });

        // 2. 构建游戏上下文
        GameContext context = GameContext.builder()
                .roomCode(gameRoom.getRoomCode())
                .currentQuestion(currentQuestion)
                .currentSubmissions(submissions)
                .playerStates(playerStates)
                .currentQuestionIndex(currentIndex)
                .build();

        // 3. 获取策略并计算分数
        QuestionScoringStrategy strategy = questionFactory.getStrategy(currentQuestion.getStrategyId());
        if (strategy == null) {
            throw new BusinessException("无法获取题目策略: " + currentQuestion.getStrategyId());
        }

        QuestionResult result;
        boolean isRepeatable = false;
        int currentRound = 0;
        int totalRounds = 0;

        if (strategy instanceof RepeatableQuestionStrategy repeatStrategy) {
            isRepeatable = true;
            currentRound = getCurrentRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());
            totalRounds = repeatStrategy.getTotalRounds();

            log.info("💯 房间 {} 计算重复题分数: {} 第 {}/{} 轮",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId(), currentRound, totalRounds);

            result = repeatStrategy.calculateRoundResult(context, currentRound);

            // 🔥 增加轮次
            incrementRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());

        } else {
            log.info("💯 房间 {} 计算普通题分数: {}",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId());

            result = strategy.calculateResult(context);
        }

        // 4. 构建得分详情
        Map<String, GameRoom.QuestionScoreDetail> scoreDetails = new HashMap<>();
        for (Map.Entry<String, Integer> entry : result.getFinalScores().entrySet()) {
            String playerId = entry.getKey();
            Integer finalScore = entry.getValue();
            Integer baseScore = result.getBaseScores().getOrDefault(playerId, finalScore);

            scoreDetails.put(playerId, GameRoom.QuestionScoreDetail.builder()
                    .baseScore(baseScore)
                    .finalScore(finalScore)
                    .build());
        }

        return ScoringResult.builder()
                .baseScores(result.getBaseScores())
                .finalScores(result.getFinalScores())
                .scoreDetails(scoreDetails)
                .repeatableQuestion(isRepeatable)
                .currentRound(currentRound)
                .totalRounds(totalRounds)
                .build();
    }

    @Override
    public boolean shouldContinueRepeating(GameRoom gameRoom, ScoringResult result) {
        if (!result.isRepeatableQuestion()) {
            return false;
        }

        // 🔥 判断：currentRound <= totalRounds 时继续
        return result.getCurrentRound() < result.getTotalRounds();
    }

    @Override
    public void clearRounds(String roomCode) {
        roomStrategyRounds.remove(roomCode);
        log.debug("🧹 清理房间 {} 的轮次记录", roomCode);
    }

    // ==================== 私有方法 ====================

    /**
     * 获取当前轮次（从1开始）
     */
    private int getCurrentRound(String roomCode, String strategyId) {
        Map<String, Integer> strategyRounds = roomStrategyRounds
                .computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>());

        return strategyRounds.getOrDefault(strategyId, 1);
    }

    /**
     * 增加轮次计数
     */
    private void incrementRound(String roomCode, String strategyId) {
        Map<String, Integer> strategyRounds = roomStrategyRounds
                .computeIfAbsent(roomCode, k -> new ConcurrentHashMap<>());

        int current = strategyRounds.getOrDefault(strategyId, 1);
        strategyRounds.put(strategyId, current + 1);

        log.debug("📈 房间 {} 题目 {} 轮次递增: {} -> {}", roomCode, strategyId, current, current + 1);
    }
}