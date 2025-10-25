package org.example.service.scoring.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.entity.QuestionEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameContext;
import org.example.pojo.GameRoom;
import org.example.pojo.PlayerGameState;
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
import java.util.stream.Collectors;

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
        // 🔥 改成 QuestionDTO
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();
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

        // 构建玩家状态（🔥 过滤观战者）
        Map<String, PlayerGameState> playerStates = new HashMap<>();
        gameRoom.getPlayers().stream()
                .filter(player -> !Boolean.TRUE.equals(player.getSpectator()))  // 🔥 排除观战者
                .forEach(player -> {
                    int currentScore = gameRoom.getScores().getOrDefault(player.getPlayerId(), 0);
                    PlayerGameState state = gameRoom.getOrCreatePlayerState(
                            player.getPlayerId(),
                            player.getName(),
                            currentScore
                    );
                    state.setTotalScore(currentScore);
                    playerStates.put(player.getPlayerId(), state);
                });

        // 构建游戏上下文（使用 DTO）
        GameContext context = GameContext.builder()
                .roomCode(gameRoom.getRoomCode())
                .currentQuestion(currentQuestion)  // ✅ 现在是 DTO
                .currentSubmissions(submissions)
                .playerStates(playerStates)
                .currentQuestionIndex(currentIndex)
                .build();

        // 获取策略并计算分数
        QuestionScoringStrategy strategy = questionFactory.getStrategy(currentQuestion.getStrategyId());
        if (strategy == null) {
            throw new BusinessException("无法获取题目策略: " + currentQuestion.getStrategyId());
        }

        QuestionDetailDTO detailDTO;
        boolean isRepeatable = false;
        int currentRound = 0;
        int totalRounds = 0;

        if (strategy instanceof RepeatableQuestionStrategy repeatStrategy) {
            isRepeatable = true;
            currentRound = getCurrentRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());
            totalRounds = repeatStrategy.getTotalRounds();

            log.info("💯 房间 {} 计算重复题分数: {} 第 {}/{} 轮",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId(), currentRound, totalRounds);

            detailDTO = repeatStrategy.calculateRoundResult(context, currentRound);
            incrementRound(gameRoom.getRoomCode(), currentQuestion.getStrategyId());

        } else {
            log.info("💯 房间 {} 计算普通题分数: {}",
                    gameRoom.getRoomCode(), currentQuestion.getStrategyId());

            detailDTO = strategy.calculateResult(context);
        }

        // 从 DTO 中提取分数信息
        Map<String, Integer> baseScores = detailDTO.getPlayerSubmissions().stream()
                .collect(Collectors.toMap(
                        PlayerSubmissionDTO::getPlayerId,
                        PlayerSubmissionDTO::getBaseScore
                ));

        Map<String, Integer> finalScores = detailDTO.getPlayerSubmissions().stream()
                .collect(Collectors.toMap(
                        PlayerSubmissionDTO::getPlayerId,
                        PlayerSubmissionDTO::getFinalScore
                ));

        // 构建得分详情
        Map<String, GameRoom.QuestionScoreDetail> scoreDetails = new HashMap<>();
        for (PlayerSubmissionDTO submission : detailDTO.getPlayerSubmissions()) {
            scoreDetails.put(submission.getPlayerId(), GameRoom.QuestionScoreDetail.builder()
                    .baseScore(submission.getBaseScore())
                    .finalScore(submission.getFinalScore())
                    .build());
        }

        return ScoringResult.builder()
                .baseScores(baseScores)
                .finalScores(finalScores)
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

        // 🔥 判断：currentRound < totalRounds 时继续
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