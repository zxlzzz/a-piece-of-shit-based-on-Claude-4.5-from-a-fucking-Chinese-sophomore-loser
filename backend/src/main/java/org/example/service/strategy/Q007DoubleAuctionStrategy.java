package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Q007DoubleAuctionStrategy extends BaseQuestionStrategy {
    public Q007DoubleAuctionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
        Map.Entry<String, String> p1 = it.next(), p2 = it.next();

        int b1 = Integer.parseInt(p1.getValue()), b2 = Integer.parseInt(p2.getValue());
        int s1 = 0, s2 = 0;

        if (b1 > b2) s1 += 10 - b1;
        else if (b2 > b1) s2 += 10 - b2;

        int b12 = 10 - b1, b22 = 10 - b2;
        if (b12 > b22) s1 += 13 - b12;
        else if (b22 > b12) s2 += 13 - b22;

        scores.put(p1.getKey(), s1);
        scores.put(p2.getKey(), s2);
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q007";
    }
}
