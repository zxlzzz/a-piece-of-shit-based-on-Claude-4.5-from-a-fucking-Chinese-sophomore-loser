package org.example.service.statistics;

import org.example.entity.ChoiceRecordEntity;

import java.util.Map;

/**
 * 题目统计服务
 */
public interface QuestionStatisticsService {

    /**
     * 记录一次选择
     *
     * @param questionId 题目ID
     * @param choice 选择的选项
     * @param playerId 玩家ID（可为null）
     * @param playerCount 当局玩家人数
     * @param gameType 游戏类型
     * @param roomCode 房间代码（可选）
     */
    void recordChoice(Long questionId, String choice, String playerId,
                      Integer playerCount, ChoiceRecordEntity.GameType gameType, String roomCode);

    /**
     * 获取题目统计（按人数区分）
     *
     * @param questionId 题目ID
     * @return Map<人数, Map<选项, 次数>>
     */
    Map<Integer, Map<String, Integer>> getQuestionStatistics(Long questionId);

    /**
     * 获取题目的总玩次数
     */
    Integer getTotalPlays(Long questionId);

    /**
     * 根据统计数据为Bot生成选择（加权随机）
     *
     * @param questionId 题目ID
     * @param playerCount 当局玩家人数
     * @return 选择的选项
     */
    String generateBotChoice(Long questionId, Integer playerCount);
}
