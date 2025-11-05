package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 你们三人分别决定在一片牧场养牛的数量，如果牛的总数在3头及以下，每头牛可价值3分，以上则每超过一头牛的价值减1（可以减至负数），则你的选择为
 * 0-3
 */

@Component
public class Q011CattleRaiseStrategy extends BaseQuestionStrategy {
    public Q011CattleRaiseStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        int total = submissions.values().stream().mapToInt(Integer::parseInt).sum();
//        int value = total <= 3 ? 3 : 3 - (total - 3);
//
//        for (Map.Entry<String, String> e : submissions.entrySet()) {
//            int count = Integer.parseInt(e.getValue());
//            scores.put(e.getKey(), count * value);
//        }
//        return scores;
//    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions){
        int value = Math.min(3,6-submissions.values().stream()
                .mapToInt(Integer::parseInt)
                .sum());
        return submissions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e->Integer.parseInt(e.getValue())*value
                ));
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        return submissions.entrySet().stream()
//                .collect(Collectors.collectingAndThen(
//                        Collectors.toMap(
//                                Map.Entry::getKey,
//                                e -> Integer.parseInt(e.getValue())
//                        ),
//                        map -> {
//                            int total = map.values().stream().mapToInt(i -> i).sum();
//                            int value = total <= 3 ? 3 : 3 - (total - 3);
//                            return map.entrySet().stream()
//                                    .collect(Collectors.toMap(
//                                            Map.Entry::getKey,
//                                            e -> e.getValue() * value
//                                    ));
//                        }
//                ));
//    }

    @Override
    public String getQuestionIdentifier() {
        return "Q011";
    }
}
