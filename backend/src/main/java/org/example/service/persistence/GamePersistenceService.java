package org.example.service.persistence;

import org.example.pojo.GameRoom;

/**
 * 游戏持久化服务
 * 负责保存游戏结果到数据库
 */
public interface GamePersistenceService {

    /**
     * 保存游戏结果
     * @param roomCode 房间码
     */
    void saveGameResult(String roomCode);
}