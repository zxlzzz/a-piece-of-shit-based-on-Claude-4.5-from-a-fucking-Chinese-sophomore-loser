package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.ConditionBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 现在数字被分为两组，123一组，456一组，你们选择一个数，如果位于同一组，则获得该分数，否则改为扣除
 */
@Component
public class Q001NumberGroupStrategy extends ConditionBasedTemplateStrategy {
    private static final Set<Integer> GROUP_A = Set.of(1, 2, 3);

    public Q001NumberGroupStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q001";
    }

    @Override
    protected StrategyConfig.ConditionBasedConfig getConfig() {
        return new StrategyConfig.ConditionBasedConfig() {
            @Override
            public java.util.function.Function<java.util.Map<String, String>, Boolean> getConditionChecker() {
                return submissions -> {
                    var values = submissions.values().stream()
                        .map(Integer::parseInt)
                        .toList();
                    int a = values.get(0);
                    int b = values.get(1);
                    // 同组返回 true
                    return (GROUP_A.contains(a) && GROUP_A.contains(b)) ||
                           (!GROUP_A.contains(a) && !GROUP_A.contains(b));
                };
            }

            @Override
            public java.util.function.Function<String, Integer> getBaseValueCalculator() {
                return choice -> Integer.parseInt(choice);
            }
        };
    }
}
