package org.example.service.QuestionScoringStrategyImpl;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Q008: 商店街选址
 * 题目：你们二人都想在商店街开店，分核心地段和边缘地段，若选择相同则收益减少。
 * 高人流量：1核心（14）1边缘（10）2核心（8） 2边缘（5）
 * 低人流量：核心（12） 边缘（8）
 * A. 核心地段（消耗5分）
 * B. 边缘地段（消耗3分）
 * C. 什么都不做
 *
 * 规则：
 * - 需要先随机"人流量"（高/低）
 * - 根据选址和人流量计算收益
 * - 相同选址收益减少
 * - 最终得分 = 收益 - 成本
 */
@Slf4j
public class Q008ShopLocationStrategy extends BaseQuestionStrategy {

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String p1Id = players[0].getKey();
        String p2Id = players[1].getKey();
        String choice1 = players[0].getValue();
        String choice2 = players[1].getValue();

        boolean highTraffic = !Objects.equals(choice1, "C") && !Objects.equals(choice2, "C");

        // 计算每个玩家的得分
        int score1 = calculateShopScore(choice1, choice2, highTraffic, true);
        int score2 = calculateShopScore(choice2, choice1, highTraffic, false);

        scores.put(p1Id, score1);
        scores.put(p2Id, score2);

        return scores;
    }

    /**
     * 计算单个玩家的商店得分
     * @param myChoice 我的选择
     * @param opponentChoice 对手的选择
     * @param highTraffic 是否高人流量
     * @param isPlayer1 是否是玩家1（用于区分同选A或B时的收益）
     */
    private int calculateShopScore(String myChoice, String opponentChoice,
                                   boolean highTraffic, boolean isPlayer1) {
        if (myChoice.equals("C")) {
            return 0;  // 不开店：0分
        }

        // 确定成本
        int cost = myChoice.equals("A") ? 5 : 3;

        // 计算收益
        int revenue = 0;
        boolean sameLocation = myChoice.equals(opponentChoice);

        if (myChoice.equals("A")) {
            // 选核心地段
            if (highTraffic) {
                revenue = sameLocation ? 8 : 14;  // 高流量：独占14，共享8
            } else {
                revenue = 12;  // 低流量：固定12
            }
        } else {
            // 选边缘地段
            if (highTraffic) {
                revenue = sameLocation ? 5 : 10;  // 高流量：独占10，共享5
            } else {
                revenue = 8;  // 低流量：固定8
            }
        }

        return revenue - cost;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q008";
    }
}
