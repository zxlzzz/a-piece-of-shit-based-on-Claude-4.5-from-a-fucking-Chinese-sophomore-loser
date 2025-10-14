package org.example.service.QuestionScoringStrategyImpl.QR;
import lombok.extern.slf4j.Slf4j;
import org.example.pojo.Buff;
import org.example.pojo.GameContext;
import org.example.pojo.GameEvent;
import org.example.pojo.PlayerGameState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * QR001: 策略选择题（重复4次）
 * 题目：选择一项（本题共进行4次）（这4道题中获得的额外效果仅在这四道题生效）
 * A. 获得2分
 * B. 你的下一次得分翻倍（下次得分>0时生效，可叠加）
 * C. 你对手下一题得分减半（明确指定下一题）
 *
 * 规则：
 * - 选A：直接获得2分
 * - 选B：本次不得分，获得一个"下次得分翻倍"buff
 *        · buff在下次得分>0时触发并消耗
 *        · 可以叠加：连续选两次B再选A，得分为 2×2×2=8
 * - 选C：本次不得分，对手下一题得分减半（明确指定下一题，不管得分多少都会消耗）
 * - 所有buff在第4轮结束后自动清除
 *
 * 技术实现：
 * - 选B的buff：duration=-1（永久），triggerOnScore=true（得分>0时触发并消耗）
 * - 选C的buff：duration=1（下一题自动生效并消耗）
 * - 都标记repeatableOnly=true，第4轮结束清除
 */
@Component
@Slf4j
public class QR001StrategyChoiceStrategy extends BaseRepeatableStrategy {

    private static final int TOTAL_ROUNDS = 4;

    @Override
    public int getTotalRounds() {
        return TOTAL_ROUNDS;
    }

    @Override
    protected Map<String, Integer> calculateRoundBaseScores(
            Map<String, String> submissions, int currentRound) {
        Map<String, Integer> scores = new HashMap<>();

        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            String choice = entry.getValue();

            int score = 0;

            switch (choice) {
                case "A":
                    score = 2;  // 直接获得2分
                    break;
                case "B":
                case "C":
                    score = 0;  // 本次不得分，效果体现在buff上
                    break;
            }

            scores.put(playerId, score);
        }

        return scores;
    }

    @Override
    protected void applyNextRoundBuffs(
            GameContext context,
            Map<String, String> submissions,
            int currentRound,
            List<GameEvent> events) {

        List<String> playerIds = new ArrayList<>(submissions.keySet());

        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            String playerId = entry.getKey();
            String choice = entry.getValue();

            PlayerGameState state = context.getPlayerStates().get(playerId);
            if (state == null) continue;

            if (state.getActiveBuffs() == null) {
                state.setActiveBuffs(new ArrayList<>());
            }

            switch (choice) {
                case "B":
                    // 选B：给自己添加"下次得分翻倍"buff
                    // 这个buff会一直存在，直到玩家得分>0时触发并消耗
                    Map<String, Object> params = new HashMap<>();
                    params.put("repeatableOnly", true);      // 标记为重复题专用
                    params.put("triggerOnScore", true);      // 只在得分>0时触发

                    Buff doubleBuff = Buff.builder()
                            .type("SCORE_DOUBLE")
                            .duration(-1)  // 永久持续，直到触发
                            .params(params)
                            .build();
                    state.getActiveBuffs().add(doubleBuff);

                    events.add(GameEvent.builder()
                            .type("BUFF_GAINED")
                            .targetPlayerId(playerId)
                            .description("获得下次得分翻倍buff（可叠加）")
                            .build());
                    break;

                case "C":
                    // 选C：给对手添加"下一题减半"debuff
                    // 这个buff会在下一题自动生效并消耗（不管得分多少）
                    String opponentId = playerIds.stream()
                            .filter(id -> !id.equals(playerId))
                            .findFirst()
                            .orElse(null);

                    if (opponentId != null) {
                        PlayerGameState opponentState = context.getPlayerStates().get(opponentId);
                        if (opponentState != null) {
                            if (opponentState.getActiveBuffs() == null) {
                                opponentState.setActiveBuffs(new ArrayList<>());
                            }

                            Buff halvedBuff = Buff.builder()
                                    .type("SCORE_HALVED")
                                    .duration(1)  // 下一题生效（duration=0时）
                                    .params(Map.of("repeatableOnly", true))
                                    .build();
                            opponentState.getActiveBuffs().add(halvedBuff);

                            events.add(GameEvent.builder()
                                    .type("DEBUFF_GIVEN")
                                    .sourcePlayerId(playerId)
                                    .targetPlayerId(opponentId)
                                    .description("对手下一题得分减半")
                                    .build());
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public String getQuestionIdentifier() {
        return "QR001";
    }
}
