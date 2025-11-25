package org.example.service.game;

import org.example.controller.GameController;
import org.example.dto.GameHistorySummaryDTO;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.entity.PlayerGameEntity;
import org.example.dto.GameHistoryDTO;
import org.example.dto.RoomDTO;
import org.example.pojo.GameRoom;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface GameService {

    @Transactional
    RoomDTO createRoom(Integer maxPlayers, Integer questionCount, Integer timeLimit, String password, List<Long> questionTagIds);

    /**
     * 创建测试房间（自动填充虚拟玩家）
     */
    RoomDTO createTestRoom(Integer maxPlayers, Integer questionCount);

    // 🔥 新增：更新房间设置
    RoomDTO updateRoomSettings(String roomCode, GameController.UpdateRoomSettingsRequest request);

    /**
     * 加入房间     * @param password 房间密码（如果房间有密码保护）
     */
    RoomDTO joinRoom(String roomCode, String playerId, String playerName, String password);
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

    GameHistoryDTO getGameHistoryByRoomCode(String roomCode);

    void removeDisconnectedPlayer(String roomCode, String playerId);

    GameRoom getGameRoom(String roomCode);

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
     * 处理玩家断线
     */
    void handlePlayerDisconnect(String roomCode, String playerId);

    /**
     * 获取历史记录列表（委托给 GameHistoryService）
     */
    List<GameHistorySummaryDTO> getHistoryList(Integer days, String playerId);

    /**
     * 获取单场游戏的详细历史（委托给 GameHistoryService）
     */
    GameHistoryDTO getHistoryDetail(Long gameId);

    /**
     * 踢出玩家（仅房主可用）
     */
    RoomDTO kickPlayer(String roomCode, String ownerId, String targetPlayerId);

}

