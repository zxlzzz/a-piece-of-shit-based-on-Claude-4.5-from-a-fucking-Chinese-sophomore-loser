package org.example.service.QuestionScoringStrategyImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Q007: 双拍品拍卖
 * 题目：你们二人拍两件拍品：第一件价值10分，第二件价值13分。
 *      每人只有 10 分预算，第二件的出价为 10-第一件出价。
 * 类型：bid (0-10) - 这里bid的是第一件的出价
 *
 * 规则：
 * - 第一件出价高者获得第一件（10 - 第一件出价）
 * - 第二件出价高者获得第二件（13 - 第二件出价）
 * - 第二件出价 = 10 - 第一件出价
 * - 若某件出价相同，都不获得该件
 */
@Component
@Slf4j
public class Q007DoubleAuctionStrategy extends BaseQuestionStrategy {

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String p1Id = players[0].getKey();
        String p2Id = players[1].getKey();
        int bid1_item1 = Integer.parseInt(players[0].getValue());  // 玩家1对第一件的出价
        int bid2_item1 = Integer.parseInt(players[1].getValue());  // 玩家2对第一件的出价

        // 计算第二件的出价（预算10分）
        int bid1_item2 = 10 - bid1_item1;  // 玩家1对第二件的出价
        int bid2_item2 = 10 - bid2_item1;  // 玩家2对第二件的出价

        int score1 = 0;
        int score2 = 0;

        // 处理第一件拍品（价值10分）
        if (bid1_item1 > bid2_item1) {
            score1 += 10 - bid1_item1;  // 玩家1拍得第一件
        } else if (bid2_item1 > bid1_item1) {
            score2 += 10 - bid2_item1;  // 玩家2拍得第一件
        }
        // 出价相同：都不获得

        // 处理第二件拍品（价值13分）
        if (bid1_item2 > bid2_item2) {
            score1 += 13 - bid1_item2;  // 玩家1拍得第二件
        } else if (bid2_item2 > bid1_item2) {
            score2 += 13 - bid2_item2;  // 玩家2拍得第二件
        }
        // 出价相同：都不获得

        scores.put(p1Id, score1);
        scores.put(p2Id, score2);

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q007";
    }
}
