package org.example.service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Q001: 数字分组题
 * 数字分为两组：123和456
 * 规则：同组获得该分数，不同组扣除该分数
 * 类型：bid (1-6)
 */
@Component
@Slf4j
public class Q001NumberGroupStrategy extends BaseQuestionStrategy {
    private static final Set<Integer> GROUP_A = Set.of(1, 2, 3);

    public Q001NumberGroupStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        List<Map.Entry<String, String>> players = new ArrayList<>(submissions.entrySet());

        int n1 = Integer.parseInt(players.get(0).getValue());
        int n2 = Integer.parseInt(players.get(1).getValue());

        boolean sameGroup = (GROUP_A.contains(n1) && GROUP_A.contains(n2)) ||
                (!GROUP_A.contains(n1) && !GROUP_A.contains(n2));

        scores.put(players.get(0).getKey(), sameGroup ? n1 : -n1);
        scores.put(players.get(1).getKey(), sameGroup ? n2 : -n2);

        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q001";
    }
}
