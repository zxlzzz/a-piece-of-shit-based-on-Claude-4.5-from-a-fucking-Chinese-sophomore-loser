package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.AggregationBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * 你们三人分别决定在一片牧场养牛的数量，如果牛的总数在3头及以下，每头牛可价值3分，以上则每超过一头牛的价值减1（可以减至负数），则你的选择为
 * 0-3
 */
@Component
public class Q011CattleRaiseStrategy extends AggregationBasedTemplateStrategy {

    public Q011CattleRaiseStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q011";
    }

    @Override
    protected StrategyConfig.AggregationBasedConfig getConfig() {
        return new StrategyConfig.AggregationBasedConfig() {
            @Override
            public java.util.function.Function<java.util.Map<String, String>, Integer> getAggregator() {
                return submissions -> submissions.values().stream()
                    .mapToInt(Integer::parseInt)
                    .sum();
            }

            @Override
            public java.util.function.Function<AggregationContext, java.util.Map<String, Integer>> getScoreCalculator() {
                return context -> {
                    int total = context.aggregatedValue();
                    int value = total <= 3 ? 3 : 3 - (total - 3);

                    return context.submissions().entrySet().stream()
                        .collect(Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            e -> Integer.parseInt(e.getValue()) * value
                        ));
                };
            }
        };
    }
}
