package org.example.service.strategy.template;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.BaseQuestionStrategy;

import java.util.Map;

/**
 * 基于聚合计算的模板策略
 *
 * 适用场景：
 * - 合作博弈（Q006: 修路）
 * - 公共资源（Q011: 养牛）
 *
 * 逻辑：
 * 1. 先计算聚合值（如总和、平均值等）
 * 2. 根据聚合值分配每个玩家的分数
 */
public abstract class AggregationBasedTemplateStrategy extends BaseQuestionStrategy {

    public AggregationBasedTemplateStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    /**
     * 子类提供配置
     */
    protected abstract StrategyConfig.AggregationBasedConfig getConfig();

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        StrategyConfig.AggregationBasedConfig config = getConfig();

        // 1. 计算聚合值
        int aggregatedValue = config.getAggregator().apply(submissions);

        // 2. 构建上下文并计算分数
        StrategyConfig.AggregationContext context =
            new StrategyConfig.AggregationContext(submissions, aggregatedValue);

        return config.getScoreCalculator().apply(context);
    }
}
