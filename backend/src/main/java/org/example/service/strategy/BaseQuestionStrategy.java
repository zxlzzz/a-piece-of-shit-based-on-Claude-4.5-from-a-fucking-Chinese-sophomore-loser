package org.example.service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.*;
import org.example.service.QuestionScoringStrategy;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public abstract class BaseQuestionStrategy implements QuestionScoringStrategy {

    protected final BuffApplier buffApplier;

    /**
     * 子类实现：计算基础分数
     */
    protected abstract Map<String, Integer> calculateBaseScores(Map<String, String> submissions);

    @Override
    public QuestionDetailDTO calculateResult(GameContext context) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 计算基础分数
        Map<String, Integer> baseScores = calculateBaseScores(submissions);

        // 2. 应用 Buff
        Map<String, Integer> finalScores = applyBuffs(context, baseScores);

        // 3. 减少 Buff 持续时间
        decreaseBuffDuration(context);

        // 4. 构建玩家提交列表
        List<PlayerSubmissionDTO> playerSubmissions = buildPlayerSubmissions(
                context, submissions, baseScores, finalScores
        );

        // 5. 计算选项分布
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        // 6. 获取选项文本
        String optionText = getOptionText(context.getCurrentQuestion());

        // 7. 返回 DTO
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
     * 应用 Buff
     */
    protected Map<String, Integer> applyBuffs(
            GameContext context,
            Map<String, Integer> baseScores
    ) {
        Map<String, Integer> finalScores = new HashMap<>();

        for (Map.Entry<String, Integer> entry : baseScores.entrySet()) {
            String playerId = entry.getKey();
            int score = entry.getValue();

            PlayerGameState state = context.getPlayerStates().get(playerId);
            if (state != null && state.getActiveBuffs() != null) {
                for (Buff buff : state.getActiveBuffs()) {
                    if (buff.getDuration() == 0) {
                        int[] result = buffApplier.applyBuff(buff, score, playerId);
                        score = result[0];
                    }
                }
            }
            finalScores.put(playerId, score);
        }

        return finalScores;
    }

    /**
     * 减少 Buff 持续时间
     */
    protected void decreaseBuffDuration(GameContext context) {
        for (PlayerGameState state : context.getPlayerStates().values()) {
            if (state.getActiveBuffs() == null) continue;

            Iterator<Buff> iterator = state.getActiveBuffs().iterator();
            while (iterator.hasNext()) {
                Buff buff = iterator.next();
                buff.setDuration(buff.getDuration() - 1);
                if (buff.getDuration() < 0) {
                    iterator.remove();
                }
            }
        }
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

    /**
     * 辅助方法：获取两人游戏的玩家
     */
    protected Map.Entry<String, String>[] getTwoPlayers(Map<String, String> submissions) {
        if (submissions.size() != 2) {
            throw new IllegalArgumentException("需要2人游戏");
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(submissions.entrySet());
        @SuppressWarnings("unchecked")
        Map.Entry<String, String>[] array = new Map.Entry[]{list.get(0), list.get(1)};
        return array;
    }

    public Map<String, Integer> test(Map<String, String> submissions) {
        return calculateBaseScores(submissions);
    }
}