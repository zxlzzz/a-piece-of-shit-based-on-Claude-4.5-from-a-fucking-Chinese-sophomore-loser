package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Q009BiddingCompetitionStrategy extends BaseQuestionStrategy {
    public Q009BiddingCompetitionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
        Map.Entry<String, String> p1 = it.next(), p2 = it.next();

        int b1 = Integer.parseInt(p1.getValue()), b2 = Integer.parseInt(p2.getValue());
        int diff = Math.abs(b1 - b2);

        if (diff < 3) {
            scores.put(p1.getKey(), 10 - b1);
            scores.put(p2.getKey(), 10 - b2);
        } else {
            scores.put(p1.getKey(), b1 > b2 ? 10 - b1 : 0);
            scores.put(p2.getKey(), b2 > b1 ? 10 - b2 : 0);
        }
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q009";
    }
}
