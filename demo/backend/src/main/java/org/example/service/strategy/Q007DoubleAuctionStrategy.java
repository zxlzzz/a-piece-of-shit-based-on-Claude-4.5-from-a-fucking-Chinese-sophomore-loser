package org.example.service.strategy;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;

/**
 * 你们二人拍两件拍品：第一件价值10分，第二件价值13分。每人只有 10 分预算，第二件的出价为 10-第一件出价。
 * 0-10
 */
@Component
public class Q007DoubleAuctionStrategy extends BaseQuestionStrategy {

    public Q007DoubleAuctionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q007";
    }

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        // 按第一件出价排序（升序）
        var sorted = submissions.entrySet().stream()
            .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getValue())))
            .toList();

        // 低价者赢第一件（价值10），得分=10-出价
        // 高价者赢第二件（价值13），得分=13-(10-第一件出价)=出价+3
        return Map.of(
            sorted.get(0).getKey(), 10 - Integer.parseInt(sorted.get(0).getValue()),
            sorted.get(1).getKey(), Integer.parseInt(sorted.get(1).getValue()) + 3
        );
    }
}
