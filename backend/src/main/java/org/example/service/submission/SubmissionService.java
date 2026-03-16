package org.example.service.submission;

import org.example.pojo.GameRoom;

/**
 * 答题提交服务
 * 负责答案提交和默认答案填充
 */
public interface SubmissionService {

    /**
     * 提交答案（保存到数据库 + 更新内存），使用房间当前 currentIndex 作为题目索引
     */
    void submitAnswer(String roomCode, String playerId, String choice);

    /**
     * ASYNC 模式：提交答案到指定题目索引（不依赖 gameRoom.currentIndex）
     */
    void submitAnswerAt(String roomCode, String playerId, String choice, int questionIndex);

    /**
     * 填充默认答案（超时未提交的玩家）
     * @param gameRoom 游戏房间
     */
    void fillDefaultAnswers(GameRoom gameRoom);

    /**
     * 检查是否所有人都已提交
     * @param gameRoom 游戏房间
     * @return true-全部提交，false-还有人未提交
     */
    boolean allSubmitted(GameRoom gameRoom);
    /**
     * 自动为Bot提交随机答案（测试房间专用）
     * @param gameRoom 游戏房间
     */
    void autoSubmitBots(GameRoom gameRoom);
}