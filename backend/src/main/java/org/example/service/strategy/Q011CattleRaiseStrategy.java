package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


/**
 * Q011: 养牛
 *
 * 题目：你们三人分别选择养几头牛并获得数量对应的分数
 *
 * 规则：
 * 1. 牛的总数在3头及以下，则每头牛值3分
 * 2. 超过3头，每超过一头，牛的价值-1（可以减到负数）
 * 3. 三人题，范围0-3
 */
@Component
@Slf4j
public class Q011CattleRaiseStrategy extends BaseQuestionStrategy {

    public Q011CattleRaiseStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        Map<String, Integer> playerChoices = new HashMap<>();

        // 1. 解析所有玩家的选择
        for (Map.Entry<String, String> entry : submissions.entrySet()) {
            int choice = Integer.parseInt(entry.getValue());
            playerChoices.put(entry.getKey(), choice);
        }

        // 2. 计算总数
        int totalCattle = playerChoices.values().stream()
                .mapToInt(Integer::intValue)
                .sum();

        // 3. 计算每头牛的价值
        int valuePerCattle;
        if (totalCattle <= 3) {
            valuePerCattle = 3;
        } else {
            valuePerCattle = 3 - (totalCattle - 3);
        }

        // 4. 计算每个玩家的分数
        for (Map.Entry<String, Integer> entry : playerChoices.entrySet()) {
            String playerId = entry.getKey();
            int cattleCount = entry.getValue();
            int score = cattleCount * valuePerCattle;
            scores.put(playerId, score);
        }

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q011";  // 🔥 改成 Q011
    }
}
