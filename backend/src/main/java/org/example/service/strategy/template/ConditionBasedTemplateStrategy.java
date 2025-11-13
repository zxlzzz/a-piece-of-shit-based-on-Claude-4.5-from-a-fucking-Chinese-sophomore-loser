package org.example.service.strategy.template;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.BaseQuestionStrategy;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 基于条件判断的模板策略
 *
 * 适用场景：
 * - 分组判断（Q001: 数字分组）
 * - 组合判断（Q002: 服装组合）
 * - 条件触发（Q008: 商店选址）
 *
 * 逻辑：
 * 1. 判断全局条件是否满足
 * 2. 满足：给予正分
 * 3. 不满足：扣分或不得分
 */
public abstract class ConditionBasedTemplateStrategy extends BaseQuestionStrategy {

    public ConditionBasedTemplateStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    /**
     * 子类提供配置
     */
    protected abstract StrategyConfig.ConditionBasedConfig getConfig();

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        StrategyConfig.ConditionBasedConfig config = getConfig();

        // 1. 判断全局条件
        boolean conditionMet = config.getConditionChecker().apply(submissions);

        // 2. 计算每个玩家的分数
        return submissions.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> {
                    int baseValue = config.getBaseValueCalculator().apply(entry.getValue());
                    if (conditionMet) {
                        return baseValue;
                    } else {
                        return config.isNegativeOnFail() ? -baseValue : 0;
                    }
                }
            ));
    }
}
