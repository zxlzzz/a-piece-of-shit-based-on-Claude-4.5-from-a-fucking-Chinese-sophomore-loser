package org.example.service.strategy.template;

import org.example.service.buff.BuffApplier;
import org.example.service.strategy.BaseQuestionStrategy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 基于排序的模板策略
 *
 * 适用场景：
 * - 拍卖竞价（Q004, Q007, Q009）
 * - 排名比较（Q005）
 *
 * 使用方式：
 * 1. 子类实现 getConfig() 提供配置
 * 2. 配置中指定排序键、排序方向、分数计算逻辑
 */
public abstract class SortBasedTemplateStrategy extends BaseQuestionStrategy {

    public SortBasedTemplateStrategy(BuffApplier buffApplier) {
        super(buffApplier);
    }

    /**
     * 子类提供配置
     */
    protected abstract StrategyConfig.SortBasedConfig getConfig();

    @Override
    protected Map<String, Integer> calculateBaseScores(Map<String, String> submissions) {
        StrategyConfig.SortBasedConfig config = getConfig();

        // 1. 排序
        Comparator<Map.Entry<String, String>> comparator =
            Comparator.comparingInt(e -> config.getSortKey().apply(e));

        if (!config.isAscending()) {
            comparator = comparator.reversed();
        }

        List<Map.Entry<String, String>> sorted = submissions.entrySet().stream()
            .sorted(comparator)
            .toList();

        // 2. 计算分数
        return config.getScoreCalculator().apply(sorted);
    }
}
