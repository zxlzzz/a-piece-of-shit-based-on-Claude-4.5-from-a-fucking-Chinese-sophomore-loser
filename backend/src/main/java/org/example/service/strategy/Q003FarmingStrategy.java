package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 你们二人共同拥有一片土地，若种相同作物则收益减半。
 *         "key": "A",
 *         "text": "粮食作物（收获4，买种子消耗1）"
 *         "key": "B",
 *         "text": "经济作物（收获8，买种子消耗4）"
 *         "key": "C",
 *         "text": "什么都不种（获得对方一半分数）"
 */
@Component
public class Q003FarmingStrategy extends BaseQuestionStrategy {
    public Q003FarmingStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
        Map.Entry<String, String> p1 = it.next(), p2 = it.next();

        String c1 = p1.getValue(), c2 = p2.getValue();
        int s1 = c1.equals("A") ? 4 : c1.equals("B") ? 8 : 0;
        int s1c = c1.equals("A") ? 1 : c1.equals("B") ? 4 : 0;
        int s2 = c2.equals("A") ? 4 : c2.equals("B") ? 8 : 0;
        int s2c = c2.equals("A") ? 1 : c2.equals("B") ? 4 : 0;

        if (c1.equals("C") && c2.equals("C")) {
            scores.put(p1.getKey(), 0);
            scores.put(p2.getKey(), 0);
        } else if (c1.equals("C")) {
            scores.put(p1.getKey(), s2 / 2);
            scores.put(p2.getKey(), s2 - s2c);
        } else if (c2.equals("C")) {
            scores.put(p1.getKey(), s1 - s1c);
            scores.put(p2.getKey(), s1 / 2);
        } else {
            boolean same = c1.equals(c2);
            scores.put(p1.getKey(), same ? s1 / 2 - s1c: s1 - s1c);
            scores.put(p2.getKey(), same ? s2 / 2 - s2c: s2 - s2c);
        }
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q003";
    }
}
