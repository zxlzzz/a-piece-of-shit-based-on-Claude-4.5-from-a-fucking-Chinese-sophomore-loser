package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

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
    public String getQuestionIdentifier() {
        return "Q004";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 按出价排序（升序）
        var sorted = submissions.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getValue())))
            .toList();

        // 低价者得2分，高价者得8-出价
        return Map.of(
            sorted.get(0).getKey(), 2,
            sorted.get(1).getKey(), 8 - Integer.parseInt(sorted.get(1).getValue())
        );
    }
}
