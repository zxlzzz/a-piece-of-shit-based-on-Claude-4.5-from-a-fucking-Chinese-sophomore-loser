package org.example.service.history;

import org.example.dto.GameHistoryDTO;
import org.example.dto.GameHistorySummaryDTO;

import java.util.List;

/**
 * 游戏历史服务接口
 */
public interface GameHistoryService {

    /**
     * 获取历史记录列表
     * @param days 查询最近几天的记录（null表示全部）
     * @param playerId 玩家ID（null表示不过滤玩家）
     * @return 历史记录摘要列表
     */
    List<GameHistorySummaryDTO> getHistoryList(Integer days, String playerId);

    /**
     * 获取单场游戏的详细历史
     * @param gameId 游戏ID
     * @return 游戏详细历史
     */
    GameHistoryDTO getHistoryDetail(Long gameId);
    /**
     * 根据房间号获取游戏历史（优先返回已保存的结果，否则返回当前状态）
     */
    GameHistoryDTO getGameHistoryByRoomCode(String roomCode);
}