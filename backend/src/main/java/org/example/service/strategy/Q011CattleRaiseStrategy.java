package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

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
