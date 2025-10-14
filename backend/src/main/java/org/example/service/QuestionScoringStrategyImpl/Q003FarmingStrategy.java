package org.example.service.QuestionScoringStrategyImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Q003: 共同拥有土地种植题
 * 题目：你们二人共同拥有一片土地，若种相同作物则收益减半
 * A. 粮食作物（收获4，买种子消耗1）
 * B. 经济作物（收获8，买种子消耗4）
 * C. 什么都不种（获得对方一半分数）
 * 规则：
 * - 相同作物：收益减半
 * - 不同作物：正常收益
 * - C选项：获得对方得分的一半（向下取整）
 */
@Component
@Slf4j
public class Q003FarmingStrategy extends BaseQuestionStrategy {

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String p1Id = players[0].getKey();
        String p2Id = players[1].getKey();
        String choice1 = players[0].getValue();
        String choice2 = players[1].getValue();

        // 初始分数（未考虑是否相同）
        int score1 = getOriginalScore(choice1);
        int score2 = getOriginalScore(choice2);

        // 如果有人选C，需要特殊处理
        if (choice1.equals("C") || choice2.equals("C")) {
            handleChoiceC(scores, p1Id, p2Id, choice1, choice2, score1, score2);
        } else {
            // 都选A或B
            boolean sameChoice = choice1.equals(choice2);
            scores.put(p1Id, sameChoice ? score1 / 2 : score1);
            scores.put(p2Id, sameChoice ? score2 / 2 : score2);
        }

        return scores;
    }

    /**
     * 获取选项的原始分数（收获-成本）
     */
    private int getOriginalScore(String choice) {
        return switch (choice) {
            case "A" -> 4 - 1;  // 粮食：收获4-成本1=3
            case "B" -> 8 - 4;  // 经济：收获8-成本4=4
            case "C" -> 0;      // 不种植：初始0分
            default -> 0;
        };
    }

    /**
     * 处理有人选择C的情况
     */
    private void handleChoiceC(Map<String, Integer> scores, String p1Id, String p2Id,
                               String choice1, String choice2, int score1, int score2) {
        if (choice1.equals("C") && choice2.equals("C")) {
            // 都选C：都获得0分
            scores.put(p1Id, 0);
            scores.put(p2Id, 0);
        } else if (choice1.equals("C")) {
            // 玩家1选C，获得玩家2的一半
            scores.put(p2Id, score2);
            scores.put(p1Id, score2 / 2);
        } else {
            // 玩家2选C，获得玩家1的一半
            scores.put(p1Id, score1);
            scores.put(p2Id, score1 / 2);
        }
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q003";
    }
}
