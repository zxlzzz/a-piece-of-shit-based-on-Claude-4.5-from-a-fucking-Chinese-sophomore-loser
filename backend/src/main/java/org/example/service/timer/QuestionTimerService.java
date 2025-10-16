package org.example.service.timer;

/**
 * 题目超时调度服务
 * 负责管理题目倒计时和超时处理
 */
public interface QuestionTimerService {

    /**
     * 启动超时定时器
     * @param roomCode 房间码
     * @param seconds 超时时间（秒）
     * @param onTimeout 超时回调
     */
    void scheduleTimeout(String roomCode, long seconds, Runnable onTimeout);

    /**
     * 取消超时定时器
     * @param roomCode 房间码
     */
    void cancelTimeout(String roomCode);

    /**
     * 关闭调度器（应用关闭时调用）
     */
    void shutdown();
}