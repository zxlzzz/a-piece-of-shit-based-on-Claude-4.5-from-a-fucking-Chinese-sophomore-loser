package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 你们三人分别决定在一片牧场养牛的数量，如果牛的总数在3头及以下，每头牛可价值3分，以上则每超过一头牛的价值减1（可以减至负数），则你的选择为
 * 0-3
 */

@Component
public class Q011CattleRaiseStrategy extends BaseQuestionStrategy {
    public Q011CattleRaiseStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        int total = submissions.values().stream().mapToInt(Integer::parseInt).sum();
        int value = total <= 3 ? 3 : 3 - (total - 3);

        for (Map.Entry<String, String> e : submissions.entrySet()) {
            int count = Integer.parseInt(e.getValue());
            scores.put(e.getKey(), count * value);
        }
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q011";
    }
}
