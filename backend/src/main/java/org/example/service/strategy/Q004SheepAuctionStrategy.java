package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.SortBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 村里拍卖一只稀有羊，价值 8 分。出价低者获得2分（无花费），价高者获得 8-出价 分数。
 * 2-7
 */
@Component
public class Q004SheepAuctionStrategy extends SortBasedTemplateStrategy {

    public Q004SheepAuctionStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q004";
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
                    sorted.get(0).getKey(), 2,
                    sorted.get(1).getKey(), 8 - Integer.parseInt(sorted.get(1).getValue())
                );
            }
        };
    }
}
