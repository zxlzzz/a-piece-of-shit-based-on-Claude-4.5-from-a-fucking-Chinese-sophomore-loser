package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.SortBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 你们二人拍两件拍品：第一件价值10分，第二件价值13分。每人只有 10 分预算，第二件的出价为 10-第一件出价。
 * 0-10
 */
@Component
public class Q007DoubleAuctionStrategy extends SortBasedTemplateStrategy {

    public Q007DoubleAuctionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q007";
    }

    @Override
    protected StrategyConfig.SortBasedConfig getConfig() {
        return new StrategyConfig.SortBasedConfig() {
            @Override
            public java.util.function.Function<Map.Entry<String, String>, Integer> getSortKey() {
                return e -> Integer.parseInt(e.getValue());
            }

            @Override
            public java.util.function.Function<java.util.List<Map.Entry<String, String>>, Map<String, Integer>> getScoreCalculator() {
                return sorted -> Map.of(
                    sorted.get(0).getKey(), Integer.parseInt(sorted.get(0).getValue()) + 3,
                    sorted.get(1).getKey(), 10 - Integer.parseInt(sorted.get(1).getValue())
                );
            }
        };
    }
}
