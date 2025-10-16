package org.example.service.flow;

import org.example.pojo.GameRoom;

/**
 * 游戏流程控制服务
 * 负责游戏开始、题目推进、游戏结束
 */
public interface GameFlowService {

    /**
     * 开始游戏
     * @param roomCode 房间码
     */
    void startGame(String roomCode);

    /**
     * 推进题目（计算分数 + 推进到下一题或下一轮）
     * @param roomCode 房间码
     * @param reason 推进原因（timeout/allSubmitted/force）
     * @param fillDefaults 是否填充默认答案
     */
    void advanceQuestion(String roomCode, String reason, boolean fillDefaults);

    /**
     * 结束游戏
     * @param roomCode 房间码
     */
    void finishGame(String roomCode);
}