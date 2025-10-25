package org.example.service.strategy.QR;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.pojo.*;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public abstract class BaseRepeatableStrategy implements RepeatableQuestionStrategy {

    protected final BuffApplier buffApplier;

    @Override
    public QuestionDetailDTO calculateRoundResult(GameContext context, int currentRound) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 计算本轮基础分数
        Map<String, Integer> baseScores = calculateRoundBaseScores(submissions, currentRound);

        // 2. 应用 Buff
        Map<String, Integer> finalScores = applyBuffs(context, baseScores);

        // 3. 减少 Buff 持续时间
        decreaseBuffDuration(context);

        // 4. 如果是最后一轮，清理可重复 Buff
        if (currentRound == getTotalRounds()) {
            clearRepeatableBuffs(context);
        }

        // 5. 应用下一轮的 Buff（如果有）
        applyNextRoundBuffs(context, submissions, currentRound);

        // 6. 构建玩家提交列表
        List<PlayerSubmissionDTO> playerSubmissions = buildPlayerSubmissions(
                context, submissions, baseScores, finalScores
        );

        // 7. 计算选项分布
        Map<String, Integer> choiceCounts = calculateChoiceCounts(submissions);

        // 8. 获取选项文本
        String optionText = getOptionText(context.getCurrentQuestion());

        // 9. 返回 DTO
        return QuestionDetailDTO.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .questionText(context.getCurrentQuestion().getText() + " (第" + currentRound + "/" + getTotalRounds() + "轮)")
                .optionText(optionText)
                .questionType(context.getCurrentQuestion().getType())
                .playerSubmissions(playerSubmissions)
                .choiceCounts(choiceCounts)
                .build();
    }

    /**
     * 子类实现：计算本轮基础分数
     */
    protected abstract Map<String, Integer> calculateRoundBaseScores(
            Map<String, String> submissions,
            int currentRound
    );

    /**
     * 子类可选实现：应用下一轮的 Buff
     */
    protected void applyNextRoundBuffs(
            GameContext context,
            Map<String, String> submissions,
            int currentRound
    ) {
        // 默认不做任何事，子类按需覆盖
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
                List<Buff> buffsToRemove = new ArrayList<>();

                for (Buff buff : state.getActiveBuffs()) {
                    boolean shouldCheck = (buff.getDuration() == 0) ||
                            (buff.getDuration() == -1 && buff.getParams() != null &&
                                    Boolean.TRUE.equals(buff.getParams().get("triggerOnScore")));

                    if (shouldCheck) {
                        int[] result = buffApplier.applyBuff(buff, score, playerId);
                        score = result[0];

                        if (result[1] == 1) {
                            buffsToRemove.add(buff);
                        }
                    }
                }

                state.getActiveBuffs().removeAll(buffsToRemove);
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
                if (buff.getDuration() >= 0) {
                    buff.setDuration(buff.getDuration() - 1);
                    if (buff.getDuration() < 0) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    /**
     * 清理可重复 Buff
     */
    protected void clearRepeatableBuffs(GameContext context) {
        for (PlayerGameState state : context.getPlayerStates().values()) {
            if (state.getActiveBuffs() == null) continue;

            Iterator<Buff> iterator = state.getActiveBuffs().iterator();
            while (iterator.hasNext()) {
                Buff buff = iterator.next();
                if (buff.getParams() != null &&
                        Boolean.TRUE.equals(buff.getParams().get("repeatableOnly"))) {
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
     * 计算选项分布
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

    /**
     * 子类实现：总轮数
     */
    public abstract int getTotalRounds();

    public Map<String, Integer> test(Map<String, String> submissions) {
        return calculateRoundBaseScores(submissions, getTotalRounds());
    }

}