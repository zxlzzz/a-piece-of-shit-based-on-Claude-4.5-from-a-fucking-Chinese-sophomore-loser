package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.SortBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 你们二人遗失了同一个物品，向保险公司索赔。如果两人价格相同，则都获得该分数，否则高价者得 出价-3，低价者得出价。
 * 2-8
 */
@Component
public class Q005InsuranceClaimStrategy extends SortBasedTemplateStrategy {

    public Q005InsuranceClaimStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q005";
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
                return sorted -> {
                    int lowClaim = Integer.parseInt(sorted.get(0).getValue());
                    int highClaim = Integer.parseInt(sorted.get(1).getValue());
                    boolean same = (lowClaim == highClaim);

                    return Map.of(
                        sorted.get(0).getKey(), lowClaim,
                        sorted.get(1).getKey(), same ? highClaim : highClaim - 3
                    );
                };
            }
        };
    }
}
