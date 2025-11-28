package org.example.service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.*;
import org.example.service.question.QuestionScoringStrategy;
import org.springframework.stereotype.Component;

import java.util.*;
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

        Map<String, Integer> baseScores = calculateBaseScores(submissions);
        Map<String, Integer> finalScores = baseScores;

        List<PlayerSubmissionDTO> playerSubmissions = buildPlayerSubmissions(
                context, submissions, baseScores, finalScores
        );

        // 5. 计算选项分布
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        // 6. 获取选项文本
        String optionText = getOptionText(context.getCurrentQuestion());

        
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
            Map<String, Integer> baseScores,
            Map<String, Integer> finalScores
    ) {
        return submissions.entrySet().stream()
                .map(entry -> {
                    String playerId = entry.getKey();
                    return PlayerSubmissionDTO.builder()
                            .playerId(playerId)
                            .playerName(context.getPlayerName(playerId))
                            .choice(entry.getValue())
                            .baseScore(baseScores.getOrDefault(playerId, 0))
                            .finalScore(finalScores.getOrDefault(playerId, 0))
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
        // 默认返回空，子类可以根据需要覆盖
        return "";
    }

    @Override
    public Map<String, Integer> calculateScores(Map<String, String> submissions) {
        return calculateBaseScores(submissions);
    }

    public Map<String, Integer> test(Map<String, String> submissions) {
        return calculateBaseScores(submissions);
    }
}