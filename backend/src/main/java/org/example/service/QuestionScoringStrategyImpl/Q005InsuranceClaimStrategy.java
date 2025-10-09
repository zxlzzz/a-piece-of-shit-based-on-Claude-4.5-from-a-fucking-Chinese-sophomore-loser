package org.example.service.QuestionScoringStrategyImpl;


import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Q005: 保险索赔
 * 题目：你们二人遗失了同一个物品，向保险公司索赔。
 *      如果两人价格相同，则都获得该分数；
 *      否则高价者得 出价-3，低价者得出价。
 * 类型：bid (2-8)
 *
 * 规则：
 * - 价格相同：都获得该分数
 * - 价格不同：高价者被怀疑欺诈，扣3分；低价者全额赔付
 */
@Slf4j
public class Q005InsuranceClaimStrategy extends BaseQuestionStrategy {

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String p1Id = players[0].getKey();
        String p2Id = players[1].getKey();
        int claim1 = Integer.parseInt(players[0].getValue());
        int claim2 = Integer.parseInt(players[1].getValue());

        if (claim1 == claim2) {
            // 价格相同：都获得该分数
            scores.put(p1Id, claim1);
            scores.put(p2Id, claim2);
        } else if (claim1 > claim2) {
            // 玩家1索赔高
            scores.put(p1Id, claim1 - 3);  // 高价者：出价-3（被怀疑）
            scores.put(p2Id, claim2);      // 低价者：全额赔付
        } else {
            // 玩家2索赔高
            scores.put(p1Id, claim1);      // 低价者：全额赔付
            scores.put(p2Id, claim2 - 3);  // 高价者：出价-3（被怀疑）
        }

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q005";
    }
}
