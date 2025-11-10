package org.example.service.strategy.template;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 策略配置接口 - 用于参数化配置策略逻辑
 */
public interface StrategyConfig {

    /**
     * 基于排序的策略配置
     */
    interface SortBasedConfig extends StrategyConfig {
        /**
         * 排序逻辑（从提交中提取排序键）
         */
        Function<Map.Entry<String, String>, Integer> getSortKey();

        /**
         * 是否升序（true=升序，false=降序）
         */
        default boolean isAscending() {
            return true;
        }

        /**
         * 计算分数（输入：排序后的玩家列表）
         */
        Function<List<Map.Entry<String, String>>, Map<String, Integer>> getScoreCalculator();
    }

    /**
     * 基于条件的策略配置
     */
    interface ConditionBasedConfig extends StrategyConfig {
        /**
         * 判断全局条件是否满足
         */
        Function<Map<String, String>, Boolean> getConditionChecker();

        /**
         * 计算每个玩家的基础分值（不考虑条件）
         */
        Function<String, Integer> getBaseValueCalculator();

        /**
         * 是否对不满足条件的情况取反（true=扣分，false=不得分）
         */
        default boolean isNegativeOnFail() {
            return true;
        }
    }

    /**
     * 基于聚合的策略配置
     */
    interface AggregationBasedConfig extends StrategyConfig {
        /**
         * 聚合计算（如：求和、求平均等）
         */
        Function<Map<String, String>, Integer> getAggregator();

        /**
         * 根据聚合值计算每个玩家的分数
         */
        Function<AggregationContext, Map<String, Integer>> getScoreCalculator();
    }

    /**
     * 聚合上下文
     */
    record AggregationContext(
        Map<String, String> submissions,
        int aggregatedValue
    ) {}
}
