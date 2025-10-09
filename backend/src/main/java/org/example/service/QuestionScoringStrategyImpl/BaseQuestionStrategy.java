package org.example.service.QuestionScoringStrategyImpl;

import org.example.pojo.*;
import org.example.service.QuestionScoringStrategy;

import java.util.*;

public abstract class BaseQuestionStrategy implements QuestionScoringStrategy {
    /**
     * 子类只需实现这个方法来计算基础分数
     * @param submissions 所有提交
     * @return playerId -> baseScore
     */
    protected abstract Map<String, Integer> calculateBaseScores(Map<String, String> submissions);

    protected List<GameEvent> generateEvents(Map<String, String> submissions, Map<String, Integer> baseScores) {
        return new ArrayList<>();
    }
    @Override
    public QuestionResult calculateResult(GameContext context) {
        Map<String, String> submissions = context.getCurrentSubmissions();

        // 1. 计算基础分数
        Map<String, Integer> baseScores = calculateBaseScores(submissions);

        // 2. 生成事件
        List<GameEvent> events = generateEvents(submissions, baseScores);

        // 3. 应用buff
        Map<String, Integer> finalScores = applyBuffs(context, baseScores, events);

        // 4. 减少buff持续时间
        decreaseBuffDuration(context);

        return QuestionResult.builder()
                .questionIndex(context.getCurrentQuestionIndex())
                .baseScores(baseScores)
                .finalScores(finalScores)
                .events(events)
                .submissions(submissions)
                .build();
    }

    /**
     * 应用所有生效的buff
     */
    private Map<String, Integer> applyBuffs(GameContext context, Map<String, Integer> baseScores, List<GameEvent> events) {
        Map<String, Integer> finalScores = new HashMap<>();

        for (Map.Entry<String, Integer> entry : baseScores.entrySet()) {
            String playerId = entry.getKey();
            int score = entry.getValue();

            PlayerGameState state = context.getPlayerStates().get(playerId);
            if (state != null && state.getActiveBuffs() != null) {
                for (Buff buff : state.getActiveBuffs()) {
                    if (buff.getDuration() == 0) {
                        score = applyBuff(buff, score, playerId, events);
                    }
                }
            }
            finalScores.put(playerId, score);
        }

        return finalScores;
    }

    /**
     * 应用单个buff
     */
    private int applyBuff(Buff buff, int score, String playerId, List<GameEvent> events) {
        switch (buff.getType()) {
            case "SCORE_DOUBLE":
                int doubled = score * 2;
                events.add(GameEvent.builder()
                        .type("BUFF_APPLIED")
                        .targetPlayerId(playerId)
                        .description("得分翻倍")
                        .build());
                return doubled;

            case "SCORE_HALVED":
                int halved = score / 2;
                events.add(GameEvent.builder()
                        .type("DEBUFF_APPLIED")
                        .targetPlayerId(playerId)
                        .description("得分减半")
                        .build());
                return halved;

            default:
                return score;
        }
    }

    /**
     * 减少buff持续时间
     */
    private void decreaseBuffDuration(GameContext context) {
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

    protected Map.Entry<String, String>[] getTwoPlayers(Map<String, String> submissions) {
        if (submissions.size() != 2) {
            throw new IllegalArgumentException("需要2人游戏");
        }
        List<Map.Entry<String, String>> list = new ArrayList<>(submissions.entrySet());
        return new Map.Entry[]{list.get(0), list.get(1)};
    }
}
