package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.GameContext;
import org.example.service.question.QuestionScoringStrategy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public abstract class BaseQuestionStrategy implements QuestionScoringStrategy {

    /**
     * 子类实现：计算基础分数
     */
    protected abstract Map<String, Integer> calculateBaseScores(Map<String, String> submissions);

    @Override
    public QuestionDetailDTO calculateResult(GameContext context) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 计算基础分数（demo 不需要 Buff，直接作为最终分数）
        Map<String, Integer> scores = calculateBaseScores(submissions);

        // 2. 构建玩家提交列表
        List<PlayerSubmissionDTO> playerSubmissions = buildPlayerSubmissions(
                context, submissions, scores
        );

        // 3. 计算选项分布
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        // 4. 获取选项文本
        String optionText = getOptionText(context.getCurrentQuestion());

        // 5. 返回 DTO
        return QuestionDetailDTO.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .questionText(context.getCurrentQuestion().getText())
                .optionText(optionText)
                .questionType(context.getCurrentQuestion().getType())
                .playerSubmissions(playerSubmissions)
                .choiceCounts(choiceCounts)
                .build();
    }

    /**
     * 构建玩家提交记录
     */
    protected List<PlayerSubmissionDTO> buildPlayerSubmissions(
            GameContext context,
            Map<String, String> submissions,
            Map<String, Integer> scores
    ) {
        return submissions.entrySet().stream()
                .map(entry -> {
                    String playerId = entry.getKey();
                    int score = scores.getOrDefault(playerId, 0);
                    return PlayerSubmissionDTO.builder()
                            .playerId(playerId)
                            .playerName(context.getPlayerName(playerId))
                            .choice(entry.getValue())
                            .baseScore(score)
                            .finalScore(score)  // demo 没有 Buff，base = final
                            .submittedAt(context.getSubmittedAt(playerId))
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 计算选项分布（用于前端展示分布图）
     */
    protected Map<String, Integer> calculateChoiceCounts(Map<String, String> submissions) {
        return submissions.values().stream()
                .collect(Collectors.groupingBy(
                        choice -> choice,
                        Collectors.summingInt(e -> 1)
                ));
    }

    /**
     * 获取选项文本（子类可覆盖）
     */
    protected String getOptionText(QuestionDTO question) {
        return "";
    }
}
