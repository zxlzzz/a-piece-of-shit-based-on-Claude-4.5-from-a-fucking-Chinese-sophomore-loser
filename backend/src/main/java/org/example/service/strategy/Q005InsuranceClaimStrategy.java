package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 你们二人遗失了同一个物品，向保险公司索赔。如果两人价格相同，则都获得该分数，否则高价者得 出价-3，低价者得出价。
 * 2-8
 */

@Component
public class Q005InsuranceClaimStrategy extends BaseQuestionStrategy {
    public Q005InsuranceClaimStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
//        Map.Entry<String, String> p1 = it.next(), p2 = it.next();
//
//        int c1 = Integer.parseInt(p1.getValue()), c2 = Integer.parseInt(p2.getValue());
//
//        if (c1 == c2) {
//            scores.put(p1.getKey(), c1);
//            scores.put(p2.getKey(), c2);
//        } else if (c1 > c2) {
//            scores.put(p1.getKey(), c1 - 3);
//            scores.put(p2.getKey(), c2);
//        } else {
//            scores.put(p1.getKey(), c1);
//            scores.put(p2.getKey(), c2 - 3);
//        }
//        return scores;
//    }

    @Override
    public String getQuestionIdentifier() {
        return "Q005";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        List<Map.Entry<String, String>> sorted = submissions.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList();
        return Map.of(
                sorted.get(0).getKey(),sorted.get(0).getValue().equals(sorted.get(1).getValue())? Integer.parseInt(sorted.get(0).getValue()): Integer.parseInt(sorted.get(0).getValue())-3,
                sorted.get(1).getKey(), Integer.parseInt(sorted.get(1).getValue())
        );
    }
}
