package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 现在数字被分为两组，123一组，456一组，你们选择一个数，如果位于同一组，则获得该分数，否则改为扣除
 */
@Component
public class Q001NumberGroupStrategy extends BaseQuestionStrategy {
    private static final Set<Integer> GROUP_A = Set.of(1, 2, 3);

    public Q001NumberGroupStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

//    @Override
//    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
//        Map<String, Integer> scores = new HashMap<>();
//        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
//        Map.Entry<String, String> p1 = it.next(), p2 = it.next();
//
//        int n1 = Integer.parseInt(p1.getValue()), n2 = Integer.parseInt(p2.getValue());
//        boolean same = (GROUP_A.contains(n1) && GROUP_A.contains(n2)) ||
//                       (!GROUP_A.contains(n1) && !GROUP_A.contains(n2));
//
//        scores.put(p1.getKey(), same ? n1 : -n1);
//        scores.put(p2.getKey(), same ? n2 : -n2);
//        return scores;
//    }

    @Override
    public String getQuestionIdentifier() {
        return "Q001";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        var entries = submissions.entrySet().stream().toList();
        int a = Integer.parseInt(entries.get(0).getKey());
        int b = Integer.parseInt(entries.get(1).getKey());
        boolean diff = (a<=3&&b>3)||(b<=3&&a>3);
        return submissions.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        e->{
                            int v = Integer.parseInt(e.getValue());
                            return diff? -v:v;
                        }
                )
        );
    }
}
