package org.example.service.scoring;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.exception.BusinessException;
import org.example.pojo.GameContext;
import org.example.pojo.GameRoom;
import org.example.pojo.PlayerGameState;
import org.example.service.question.QuestionScoringStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 分数计算服务实现
 */
@Service
@Slf4j
public class ScoringService {

    @Autowired
    private List<QuestionScoringStrategy> allStrategies;

    private final Map<String, QuestionScoringStrategy> STRATEGIES = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        allStrategies.forEach(strategy ->
                STRATEGIES.put(strategy.getQuestionIdentifier(), strategy)
        );
        log.info(" 已自动注册 {} 个题目策略: {}",
                STRATEGIES.size(),
                String.join(", ", STRATEGIES.keySet()));
    }

    private QuestionScoringStrategy getStrategy(String strategyId) {
        QuestionScoringStrategy strategy = STRATEGIES.get(strategyId);
        if (strategy == null) {
            throw new BusinessException("未找到计分策略: " + strategyId);
        }
        return strategy;
    }

    /**
     * 轮次追踪器
     * 外层键：roomCode
     * 内层键：strategyId
     * 值：当前轮次（从1开始）
     */
    private final Map<String, Map<String, Integer>> roomStrategyRounds = new ConcurrentHashMap<>();

    /**
     * 计算分数
     *  注意：此方法不是线程安全的，必须在调用方使用 RoomLock 进行同步
     * 调用方必须持有 RoomLock.getLock(roomCode) 的锁
     */
    public ScoringResult calculateScores(GameRoom gameRoom) {
        
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();
        int currentIndex = gameRoom.getCurrentIndex();
        Map<String, String> submissions = gameRoom.getSubmissions().get(currentIndex);

        if (submissions == null || submissions.isEmpty()) {
            log.warn(" 房间 {} 题目索引 {} 没有提交记录", gameRoom.getRoomCode(), currentIndex);
            return ScoringResult.builder()
                    .baseScores(new HashMap<>())
                    .finalScores(new HashMap<>())
                    .scoreDetails(new HashMap<>())
                    .repeatableQuestion(false)
                    .currentRound(0)
                    .totalRounds(0)
                    .build();
        }

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


        GameContext context = GameContext.builder()
                .roomCode(gameRoom.getRoomCode())
                .currentQuestion(currentQuestion)
                .currentSubmissions(submissions)
                .playerStates(playerStates)
                .currentQuestionIndex(currentIndex)
                .build();

        QuestionScoringStrategy strategy = getStrategy(currentQuestion.getStrategyId());

        QuestionDetailDTO detailDTO;
        boolean isRepeatable = false;
        int currentRound = 0;
        int totalRounds = 0;

        detailDTO = strategy.calculateResult(context);


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
}