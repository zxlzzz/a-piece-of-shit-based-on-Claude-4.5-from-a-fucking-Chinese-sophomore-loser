package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 你们二人都想在商店街开店，分核心地段和边缘地段，若选择相同则收益减少。高人流量：1核心（14）1边缘（10） 2核心（8） 2边缘（5）低人流量：核心（12） 边缘（8）你选择
 *         "key": "A",
 *         "text": "核心地段（消耗5分）"
 *         "key": "B",
 *         "text": "边缘地段（消耗3分）"
 *         "key": "C",
 *         "text": "什么都不做"
 */

@Component
public class Q008ShopLocationStrategy extends BaseQuestionStrategy {
    public Q008ShopLocationStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
//        Map.Entry<String, String> p1 = it.next(), p2 = it.next();
//
//        String c1 = p1.getValue(), c2 = p2.getValue();
//        boolean high = !c1.equals("C") && !c2.equals("C");
//
//        int s1 = calc(c1, c2, high);
//        int s2 = calc(c2, c1, high);
//
//        scores.put(p1.getKey(), s1);
//        scores.put(p2.getKey(), s2);
//        return scores;
//    }
//
//    private int calc(String my, String opp, boolean high) {
//        if (my.equals("C")) return 0;
//        int cost = my.equals("A") ? 5 : 3;
//        int rev = 0;
//        boolean same = my.equals(opp);
//
//        if (my.equals("A")) {
//            rev = high ? (same ? 8 : 14) : 12;
//        } else {
//            rev = high ? (same ? 5 : 10) : 8;
//        }
//        return rev - cost;
//    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions){
        boolean low = submissions.values().stream()
                .anyMatch(submission -> submission.contains("C"));
        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e-> "A".equals(e.getValue()) ? (low ? 7: 9)
                                :"B".equals(e.getValue())?(low ? 7:5)
                                :0
                ));
    }


    @Override
    public String getQuestionIdentifier() {
        return "Q008";
    }
}
