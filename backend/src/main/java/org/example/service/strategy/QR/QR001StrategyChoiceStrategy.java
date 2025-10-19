package org.example.service.strategy.QR;
import lombok.extern.slf4j.Slf4j;
import org.example.pojo.*;
import org.example.service.buff.BuffApplier;
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

    public QR001StrategyChoiceStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

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
            int currentRound) {  // ❌ 删除 List<GameEvent> events 参数

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
                    Map<String, Object> params = new HashMap<>();
                    params.put("repeatableOnly", true);
                    params.put("triggerOnScore", true);

                    Buff doubleBuff = Buff.builder()
                            .type(BuffType.MULTIPLIER)
                            .value(2.0)
                            .duration(-1)
                            .params(params)
                            .build();
                    state.getActiveBuffs().add(doubleBuff);

                    log.info("玩家 {} 选择B，获得翻倍buff", playerId);
                    break;

                case "C":
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
                                    .type(BuffType.MULTIPLIER)
                                    .value(0.5)
                                    .duration(1)
                                    .params(Map.of("repeatableOnly", true))
                                    .build();
                            opponentState.getActiveBuffs().add(halvedBuff);

                            log.info("玩家 {} 选择C，给对手 {} 添加减半buff", playerId, opponentId);
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
