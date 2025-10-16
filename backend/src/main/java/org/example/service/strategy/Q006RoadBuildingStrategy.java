package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Q006: 共同修路
 * 题目：你们二人共同修路，总共需要 6 分。
 *      出分较高的人得 8 分，较低的得 4 分，相同则均得 4 分。
 * 类型：bid (0-6)
 *
 * 规则：
 * - 实际得分 = 基础得分 - 出资
 * - 出资高者基础分8分
 * - 出资低者基础分4分
 * - 出资相同均为4分
 */
@Component
@Slf4j
public class Q006RoadBuildingStrategy extends BaseQuestionStrategy {

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        var players = getTwoPlayers(submissions);

        String p1Id = players[0].getKey();
        String p2Id = players[1].getKey();
        int investment1 = Integer.parseInt(players[0].getValue());
        int investment2 = Integer.parseInt(players[1].getValue());
        int p1Get=0, p2Get=0;

        if(investment1 + investment2 >= 6){
            p1Get = investment1>investment2?8:4;
            p2Get = investment2>investment1?8:4;
        }

        scores.put(p1Id, p1Get-investment1);
        scores.put(p2Id, p2Get-investment2);
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q006";
    }
}
