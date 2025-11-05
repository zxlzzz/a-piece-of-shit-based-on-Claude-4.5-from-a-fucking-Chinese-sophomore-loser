package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 你们二人共同修路，总共需要 6 分。出分较高的人得 8 分，较低的得 4 分，相同则均得 4 分。
 * 2-8
 */

@Component
public class Q006RoadBuildingStrategy extends BaseQuestionStrategy {
    public Q006RoadBuildingStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
//        Map.Entry<String, String> p1 = it.next(), p2 = it.next();
//
//        int i1 = Integer.parseInt(p1.getValue()), i2 = Integer.parseInt(p2.getValue());
//        int g1 = 0, g2 = 0;
//
//        if (i1 + i2 >= 6) {
//            g1 = i1 > i2 ? 8 : 4;
//            g2 = i2 > i1 ? 8 : 4;
//        }
//
//        scores.put(p1.getKey(), g1 - i1);
//        scores.put(p2.getKey(), g2 - i2);
//        return scores;
//    }
    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
       boolean fixed = submissions.values().stream()
               .mapToInt(Integer::parseInt)
               .sum()>=6;
       List<Map.Entry<String, String>> sorted = submissions.entrySet().stream()
               .sorted(Comparator.comparingInt(e->Integer.parseInt(e.getValue())))
               .toList();
       return Map.of(
               sorted.get(0).getKey(), fixed?4-Integer.parseInt(sorted.get(0).getValue()):-Integer.parseInt(sorted.get(0).getValue()),
               sorted.get(1).getKey(), fixed?8-Integer.parseInt(sorted.get(1).getValue()):-Integer.parseInt(sorted.get(0).getValue())
       );
    }
    @Override
    public String getQuestionIdentifier() {
        return "Q006";
    }


}
