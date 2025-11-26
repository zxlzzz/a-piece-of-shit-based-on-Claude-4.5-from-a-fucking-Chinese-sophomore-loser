package org.example.service.leaderboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerRankDTO;
import org.example.pojo.GameRoom;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 排行榜服务（简化版：只支持标准排名）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    public List<PlayerRankDTO> buildLeaderboard(GameRoom gameRoom) {
        // 构建玩家列表并按分数降序排序
        List<PlayerRankDTO> leaderboard = gameRoom.getPlayers().stream()
                .map(player -> PlayerRankDTO.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getName())
                        .totalScore(gameRoom.getScores().getOrDefault(player.getPlayerId(), 0))
                        .build())
                .sorted(Comparator.comparing(PlayerRankDTO::getTotalScore).reversed())
                .collect(Collectors.toList());

        // 分配排名
        for (int i = 0; i < leaderboard.size(); i++) {
            leaderboard.get(i).setRank(i + 1);
            leaderboard.get(i).setPassed(true);  // 简化版：默认通关
        }

        return leaderboard;
    }
}
