package org.example.service.leaderboard.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerRankDTO;
import org.example.dto.RoomDTO;
import org.example.entity.RoomEntity;
import org.example.pojo.GameRoom;
import org.example.repository.RoomRepository;
import org.example.service.leaderboard.LeaderboardService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 排行榜服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardServiceImpl implements LeaderboardService {

    private final RoomRepository roomRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<PlayerRankDTO> buildLeaderboard(GameRoom gameRoom) {
        // 🔥 获取房间配置
        RoomEntity roomEntity = roomRepository.findByRoomCode(gameRoom.getRoomCode()).orElse(null);
        String rankingMode = roomEntity != null ? roomEntity.getRankingMode() : "standard";
        Integer targetScore = roomEntity != null ? roomEntity.getTargetScore() : null;

        // 🔥 解析通关条件
        RoomDTO.WinConditions winConditions = null;
        if (roomEntity != null && roomEntity.getWinConditionsJson() != null) {
            try {
                winConditions = objectMapper.readValue(
                        roomEntity.getWinConditionsJson(),
                        RoomDTO.WinConditions.class
                );
            } catch (Exception e) {
                log.error("解析通关条件失败", e);
            }
        }

        // 1️⃣ 构建玩家列表（🔥 排除观战者）
        List<PlayerRankDTO> leaderboard = gameRoom.getPlayers().stream()
                .filter(player -> !Boolean.TRUE.equals(player.getSpectator()))  // 🔥 过滤观战者
                .map(player -> PlayerRankDTO.builder()
                        .playerId(player.getPlayerId())
                        .playerName(player.getName())
                        .totalScore(gameRoom.getScores().getOrDefault(player.getPlayerId(), 0))
                        .build())
                .collect(Collectors.toList());

        // 2️⃣ 根据排名模式排序
        switch (rankingMode) {
            case "closest_to_avg": {
                // 计算平均分
                double avgScore = leaderboard.stream()
                        .mapToInt(PlayerRankDTO::getTotalScore)
                        .average()
                        .orElse(0.0);

                log.info("📊 房间 {} 使用接近平均分排名，平均分: {}", gameRoom.getRoomCode(), avgScore);

                // 按离平均分的绝对差值排序
                leaderboard.sort(Comparator.comparingDouble(p ->
                        Math.abs(p.getTotalScore() - avgScore)
                ));
                break;
            }
            case "closest_to_target": {
                if (targetScore == null) {
                    log.warn("⚠️ 房间 {} 排名模式为 closest_to_target 但未设置目标分，使用标准排名",
                            gameRoom.getRoomCode());
                    leaderboard.sort(Comparator.comparing(PlayerRankDTO::getTotalScore).reversed());
                } else {
                    log.info("📊 房间 {} 使用接近目标分排名，目标分: {}", gameRoom.getRoomCode(), targetScore);

                    // 按离目标分的绝对差值排序
                    leaderboard.sort(Comparator.comparingInt(p ->
                            Math.abs(p.getTotalScore() - targetScore)
                    ));
                }
                break;
            }
            case "standard":
            default:
                // 标准排名：分数降序
                leaderboard.sort(Comparator.comparing(PlayerRankDTO::getTotalScore).reversed());
                break;
        }

        // 3️⃣ 分配排名（处理并列）
        for (int i = 0; i < leaderboard.size(); i++) {
            leaderboard.get(i).setRank(i + 1);
        }

        // 4️⃣ 判断是否通关
        boolean passed = checkWinConditions(leaderboard, winConditions);

        // 🔥 设置每个玩家的通关状态
        for (PlayerRankDTO player : leaderboard) {
            player.setPassed(passed);
        }

        if (!passed && winConditions != null) {
            log.warn("❌ 房间 {} 未达成通关条件", gameRoom.getRoomCode());
        } else {
            log.info("✅ 房间 {} 通关成功！", gameRoom.getRoomCode());
        }

        return leaderboard;
    }

    /**
     * 检查是否达成通关条件
     */
    private boolean checkWinConditions(List<PlayerRankDTO> leaderboard,
                                       RoomDTO.WinConditions conditions) {
        if (conditions == null) {
            return true; // 无条件限制，默认通关
        }

        // 检查：所有人最低分
        if (conditions.getMinScorePerPlayer() != null) {
            boolean allPass = leaderboard.stream()
                    .allMatch(p -> p.getTotalScore() >= conditions.getMinScorePerPlayer());
            if (!allPass) {
                log.info("❌ 未达成条件：所有人 ≥ {} 分", conditions.getMinScorePerPlayer());
                return false;
            }
        }

        // 检查：团队总分
        if (conditions.getMinTotalScore() != null) {
            int totalScore = leaderboard.stream()
                    .mapToInt(PlayerRankDTO::getTotalScore)
                    .sum();
            if (totalScore < conditions.getMinTotalScore()) {
                log.info("❌ 未达成条件：总分 {} < {}", totalScore, conditions.getMinTotalScore());
                return false;
            }
        }

        // 检查：平均分
        if (conditions.getMinAvgScore() != null) {
            double avgScore = leaderboard.stream()
                    .mapToInt(PlayerRankDTO::getTotalScore)
                    .average()
                    .orElse(0.0);
            if (avgScore < conditions.getMinAvgScore()) {
                log.info("❌ 未达成条件：平均分 {} < {}", avgScore, conditions.getMinAvgScore());
                return false;
            }
        }

        return true; // 所有条件都满足
    }
}