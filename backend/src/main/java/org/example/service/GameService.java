package org.example.service;

import org.example.controller.GameController;
import org.example.entity.PlayerGameEntity;
import org.example.dto.GameHistoryDTO;
import org.example.dto.RoomDTO;

import java.util.List;

public interface GameService {
    // 🔥 恢复原版签名
    RoomDTO createRoom(Integer maxPlayers, Integer questionCount);

    // 🔥 新增：更新房间设置
    RoomDTO updateRoomSettings(String roomCode, GameController.UpdateRoomSettingsRequest request);

    /**
     * 加入房间
     */
    RoomDTO joinRoom(String roomCode, String playerId, String playerName);

    GameHistoryDTO getCurrentGameStatus(String roomCode);

    /**
     * 开始游戏
     */
    RoomDTO startGame(String roomCode);

    /**
     * 提交答案
     * @param roomCode 房间码
     * @param playerId 玩家ID
     * @param choice 选择答案
     * @param force 是否强制提交
     * @return 更新后的房间状态
     */
    RoomDTO submitAnswer(String roomCode, String playerId, String choice, boolean force);

    /**
     * 设置玩家准备状态
     */
    RoomDTO setPlayerReady(String roomCode, String playerId, boolean ready);

    /**
     * 获取房间状态
     */
    RoomDTO getRoomStatus(String roomCode);

    /**
     * 获取游戏结果
     */
    List<PlayerGameEntity> getGameResults(String roomCode);

    /**
     * 移除房间
     */
    void removeRoom(String roomCode);

    List<RoomDTO> getAllActiveRoom();

    /**
     * 玩家主动离开房间
     */
    RoomDTO leaveRoom(String roomCode, String playerId);

    /**
     * 玩家重连
     */
    RoomDTO reconnectRoom(String roomCode, String playerId);

    /**
     * 处理玩家断线
     */
    void handlePlayerDisconnect(String roomCode, String playerId);

    /**
     * 结束保存
     */
    void saveGameResult(String roomCode);
}

