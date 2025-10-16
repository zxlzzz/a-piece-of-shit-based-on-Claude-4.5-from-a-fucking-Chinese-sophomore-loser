package org.example.service.strategy.QR;

import org.example.pojo.*;
import org.example.service.QuestionScoringStrategy;

// ==================== 重复题目专用接口 ====================

/**
 * 重复题目计分策略接口
 * 用于处理需要重复进行多次的题目（如QR001重复4次）
 *
 * 与普通题目的区别：
 * - 需要知道当前是第几次重复（由调用方维护并传入）
 * - 可能有跨轮次的buff效果
 * - 需要在最后一轮清理重复题专用buff
 *
 * 调用方式：
 * 必须使用 calculateRoundResult(context, currentRound) 方法
 * 不能使用 calculateResult(context) 方法
 */
public interface RepeatableQuestionStrategy extends QuestionScoringStrategy {

    /**
     * 获取该题目的总重复次数
     * @return 重复次数（如4次）
     */
    int getTotalRounds();

    /**
     * 计算当前轮次的结果
     * @param context 游戏上下文
     * @param currentRound 当前轮次（1-based，第1次、第2次...）由调用方传入
     * @return 本轮结果
     */
    QuestionResult calculateRoundResult(GameContext context, int currentRound);

    /**
     * 禁止使用此方法
     * 重复题必须使用 calculateRoundResult(context, currentRound) 方法
     */
    @Override
    default QuestionResult calculateResult(GameContext context) {
        throw new UnsupportedOperationException(
                "重复题策略请使用 calculateRoundResult(context, currentRound) 方法，" +
                        "轮次由调用方（Service层）维护并传入"
        );
    }
}