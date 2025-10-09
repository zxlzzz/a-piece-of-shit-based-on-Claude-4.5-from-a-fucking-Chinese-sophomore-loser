package org.example.service.QuestionScoringStrategyImpl;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Q004: 拍卖稀有羊
 * 题目：村里拍卖一只稀有羊，价值 8 分。出价低者获得2分（无花费），价高者获得 8-出价 分数
 * 类型：bid (2-7)
 *
 * 规则：
 * - 出价低者：获得2分（安慰奖）
 * - 出价高者：获得 8-出价 分（拍到羊但要付钱）
 * - 出价相同：都按高价处理（8-出价）
 */
@Slf4j
public class Q004SheepAuctionStrategy extends BaseQuestionStrategy {

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String p1Id = players[0].getKey();
        String p2Id = players[1].getKey();
        int bid1 = Integer.parseInt(players[0].getValue());
        int bid2 = Integer.parseInt(players[1].getValue());

        if (bid1 == bid2) {
            // 出价相同：都按高价者计算
            int score = 8 - bid1;
            scores.put(p1Id, score);
            scores.put(p2Id, score);
        } else if (bid1 > bid2) {
            // 玩家1出价高
            scores.put(p1Id, 8 - bid1);  // 高价者：羊的价值 - 出价
            scores.put(p2Id, 2);         // 低价者：安慰奖2分
        } else {
            // 玩家2出价高
            scores.put(p1Id, 2);         // 低价者：安慰奖2分
            scores.put(p2Id, 8 - bid2);  // 高价者：羊的价值 - 出价
        }

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q004";
    }
}

