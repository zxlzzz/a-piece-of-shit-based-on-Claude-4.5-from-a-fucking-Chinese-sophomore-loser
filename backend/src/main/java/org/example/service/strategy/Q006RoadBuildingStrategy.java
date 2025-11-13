package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.AggregationBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * 你们二人共同修路，总共需要 6 分。出分较高的人得 8 分，较低的得 4 分，相同则均得 4 分。
 * 2-8
 */
@Component
public class Q006RoadBuildingStrategy extends AggregationBasedTemplateStrategy {

    public Q006RoadBuildingStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q006";
    }

    @Override
    protected StrategyConfig.AggregationBasedConfig getConfig() {
        return new StrategyConfig.AggregationBasedConfig() {
            @Override
            public java.util.function.Function<Map<String, String>, Integer> getAggregator() {
                return submissions -> submissions.values().stream()
                    .mapToInt(Integer::parseInt)
                    .sum();
            }

            @Override
            public java.util.function.Function<AggregationContext, Map<String, Integer>> getScoreCalculator() {
                return context -> {
                    int total = context.aggregatedValue();
                    boolean roadFixed = total >= 6;

                    var sorted = context.submissions().entrySet().stream()
                        .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getValue())))
                        .toList();

                    Map<String, Integer> scores = new HashMap<>();

                    int lowContribution = Integer.parseInt(sorted.get(0).getValue());
                    int highContribution = Integer.parseInt(sorted.get(1).getValue());

                    if (roadFixed) {
                        scores.put(sorted.get(0).getKey(), 4 - lowContribution);
                        scores.put(sorted.get(1).getKey(), 8 - highContribution);
                    } else {
                        scores.put(sorted.get(0).getKey(), -lowContribution);
                        scores.put(sorted.get(1).getKey(), -highContribution);
                    }

                    return scores;
                };
            }
        };
    }
}
