package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Q002PerformanceCostumeStrategy extends BaseQuestionStrategy {
    public Q002PerformanceCostumeStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
        Map.Entry<String, String> p1 = it.next(), p2 = it.next();

        String c1 = p1.getValue(), c2 = p2.getValue();
        boolean complete = (c1.equals("A") || c1.equals("C") || c2.equals("A") || c2.equals("C"))
                        && (c1.equals("B") || c2.equals("B"));

        int v1 = c1.equals("A") ? 7 : c1.equals("B") ? 5 : 3;
        int v2 = c2.equals("A") ? 7 : c2.equals("B") ? 5 : 3;

        scores.put(p1.getKey(), complete ? v1 : -v1);
        scores.put(p2.getKey(), complete ? v2 : -v2);
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q002";
    }
}
