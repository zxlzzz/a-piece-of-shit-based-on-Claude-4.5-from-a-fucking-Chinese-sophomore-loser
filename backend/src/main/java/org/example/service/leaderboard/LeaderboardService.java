package org.example.service.leaderboard;

import org.example.dto.PlayerRankDTO;
import org.example.pojo.GameRoom;

import java.util.List;

/**
 * 排行榜服务
 * 负责构建排行榜和检查通关条件
 */
public interface LeaderboardService {

    /**
     * 构建排行榜
     * @param gameRoom 游戏房间
     * @return 排行榜列表
     */
    List<PlayerRankDTO> buildLeaderboard(GameRoom gameRoom);
}