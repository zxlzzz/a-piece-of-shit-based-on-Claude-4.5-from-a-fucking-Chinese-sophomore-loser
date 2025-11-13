package org.example.service.strategy;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.template.ConditionBasedTemplateStrategy;
import org.example.service.strategy.template.StrategyConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 你们二人参加演出，盲选服装。如果集齐侍卫+王子，则获得选项分数，否则扣分。
 *         "key": "A",
 *         "text": "精致的侍卫服装（7）"
 *         "key": "B",
 *         "text": "王子服装（5）"
 *         "key": "C",
 *         "text": "普通侍卫服装（3）"
 */
@Component
public class Q002PerformanceCostumeStrategy extends ConditionBasedTemplateStrategy {

    private static final Map<String, Integer> COSTUME_VALUES = Map.of(
        "A", 7,  // 精致侍卫
        "B", 5,  // 王子
        "C", 3   // 普通侍卫
    );

    public Q002PerformanceCostumeStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    @Override
    public String getQuestionIdentifier() {
        return "Q002";
    }

    @Override
    protected StrategyConfig.ConditionBasedConfig getConfig() {
        return new StrategyConfig.ConditionBasedConfig() {
            @Override
            public java.util.function.Function<Map<String, String>, Boolean> getConditionChecker() {
                return submissions -> {
                    // 集齐侍卫+王子：有A（精致侍卫） 或者 (有B（王子） 且 有C（普通侍卫）)
                    return submissions.containsValue("A") ||
                           (submissions.containsValue("B") && submissions.containsValue("C"));
                };
            }

            @Override
            public java.util.function.Function<String, Integer> getBaseValueCalculator() {
                return choice -> COSTUME_VALUES.getOrDefault(choice, 3);
            }
        };
    }
}
