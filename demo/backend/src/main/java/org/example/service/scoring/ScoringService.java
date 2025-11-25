package org.example.service.scoring;

import org.example.pojo.GameRoom;

/**
 * 分数计算服务
 * 负责调用策略计算分数、管理重复题轮次
 */
public interface ScoringService {

    /**
     * 计算当前题目的分数
     * @param gameRoom 游戏房间
     * @return 计分结果
     */
    ScoringResult calculateScores(GameRoom gameRoom);

    /**
     * 检查是否需要继续重复题
     * @param gameRoom 游戏房间
     * @param result 计分结果
     * @return true-继续下一轮，false-已完成所有轮次
     */
    boolean shouldContinueRepeating(GameRoom gameRoom, ScoringResult result);

    /**
     * 清理房间的轮次记录
     * @param roomCode 房间码
     */
    void clearRounds(String roomCode);
}