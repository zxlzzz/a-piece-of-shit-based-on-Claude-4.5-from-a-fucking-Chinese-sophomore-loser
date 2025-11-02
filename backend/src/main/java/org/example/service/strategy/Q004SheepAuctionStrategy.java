package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 村里拍卖一只稀有羊，价值 8 分。出价低者获得2分（无花费），价高者获得 8-出价 分数。
 * 2-7
 */

@Component
public class Q004SheepAuctionStrategy extends BaseQuestionStrategy {
    public Q004SheepAuctionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        Map<String, Integer> scores = new HashMap<>();
        Iterator<Map.Entry<String, String>> it = submissions.entrySet().iterator();
        Map.Entry<String, String> p1 = it.next(), p2 = it.next();

        int b1 = Integer.parseInt(p1.getValue()), b2 = Integer.parseInt(p2.getValue());
        
        if (b1 == b2) {
            scores.put(p1.getKey(), 8 - b1);
            scores.put(p2.getKey(), 8 - b2);
        } else if (b1 > b2) {
            scores.put(p1.getKey(), 8 - b1);
            scores.put(p2.getKey(), 2);
        } else {
            scores.put(p1.getKey(), 2);
            scores.put(p2.getKey(), 8 - b2);
        }
        return scores;
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q004";
    }
}
