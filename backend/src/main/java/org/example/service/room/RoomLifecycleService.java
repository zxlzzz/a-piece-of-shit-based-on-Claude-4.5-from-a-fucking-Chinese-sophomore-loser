package org.example.service.room;

import org.example.controller.GameController;
import org.example.dto.RoomDTO;
import org.example.entity.RoomEntity;
import org.example.pojo.GameRoom;
import org.springframework.transaction.annotation.Transactional;

/**
 * 房间生命周期服务
 * 负责房间的创建、加入、离开、设置等操作
 */
public interface RoomLifecycleService {

    /**
     * 初始化房间（创建数据库实体 + 内存房间）
     */
    RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, GameRoom gameRoom);

    /**
     * 初始化房间（支持标签筛选）
     */
    RoomEntity initializeRoom(Integer maxPlayers, Integer questionCount, GameRoom gameRoom, List<Long> questionTagIds);

    /**
     * 加入房间
     * @param spectator 是否为观战者
     */
    void handleJoin(String roomCode, String playerId, String playerName, Boolean spectator);

    /**
     * 离开房间
     * @return 房间是否仍然存在（false 表示房间已解散）
     */
    boolean handleLeave(String roomCode, String playerId);

    /**
     * 重连房间
     */
    void handleReconnect(String roomCode, String playerId);

    /**
     * 更新房间设置
     */
    void updateSettings(String roomCode, GameController.UpdateRoomSettingsRequest request);

    /**
     * 设置玩家准备状态
     */
    void setPlayerReady(String roomCode, String playerId, boolean ready);

    /**
     * 转换为 RoomDTO
     */
    RoomDTO toRoomDTO(String roomCode);

    @Transactional
    void handlePlayerDisconnect(String roomCode, String playerId);

    @Transactional
    void removeDisconnectedPlayer(String roomCode, String playerId);
}