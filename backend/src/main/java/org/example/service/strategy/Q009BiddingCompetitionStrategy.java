package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 你们二人都想要一个物品（得到后可以十分的价格出售），现从第三人手里购买（均可购买），物品的价格由分别报价，如果你们的报价相差大于等于3分，则出价较低的人无法购买，否则都按自己的出价购买，则你的报价为
 * 1-9
 */

@Component
public class Q009BiddingCompetitionStrategy extends BaseQuestionStrategy {
    public Q009BiddingCompetitionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
//        Map.Entry<String, String> p1 = it.next(), p2 = it.next();
//
//        int b1 = Integer.parseInt(p1.getValue()), b2 = Integer.parseInt(p2.getValue());
//        int diff = Math.abs(b1 - b2);
//
//        if (diff < 3) {
//            scores.put(p1.getKey(), 10 - b1);
//            scores.put(p2.getKey(), 10 - b2);
//        } else {
//            scores.put(p1.getKey(), b1 > b2 ? 10 - b1 : 0);
//            scores.put(p2.getKey(), b2 > b1 ? 10 - b2 : 0);
//        }
//        return scores;
//    }

    @Override
    public String getQuestionIdentifier() {
        return "Q009";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        List<Map.Entry<String, String>> sorted = submissions.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .toList();
        return Map.of(
                sorted.get(0).getKey(), Integer.parseInt(sorted.get(0).getValue())-Integer.parseInt(sorted.get(1).getValue())>=3? 0:10-Integer.parseInt(sorted.get(0).getValue()),
                sorted.get(1).getKey(), 10-Integer.parseInt(sorted.get(1).getValue())
        );
    }
}
