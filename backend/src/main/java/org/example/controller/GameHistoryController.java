package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.GameResultEntity;
import org.example.exception.BusinessException;
import org.example.repository.GameResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@Slf4j
public class GameHistoryController {

    @Autowired
    private GameResultRepository gameResultRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取所有历史记录列表
     */
    @GetMapping("/history")
    public ResponseEntity<List<GameHistorySummaryDTO>> getHistoryList(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String playerId) {  // ✅ 可选参数

        List<GameResultEntity> results;

        if (days != null) {
            LocalDateTime after = LocalDateTime.now().minusDays(days);
            results = gameResultRepository.findByCreatedAtAfterOrderByCreatedAtDesc(after);
        } else {
            results = gameResultRepository.findAllByOrderByCreatedAtDesc();
        }

        List<GameHistorySummaryDTO> summaries = results.stream()
                .map(result -> convertToSummary(result, playerId))
                .filter(summary -> summary != null)  // ✅ 过滤掉用户没参加的游戏
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    /**
     * 获取单场游戏的详细历史（用于弹窗）
     */
    @GetMapping("/history/{gameId}")
    public ResponseEntity<GameHistoryDTO> getHistoryDetail(@PathVariable Long gameId) {
        // ✅ 修改：使用 game 关联查询
        GameResultEntity result = gameResultRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));

        try {
            List<PlayerRankDTO> leaderboard = objectMapper.readValue(
                    result.getLeaderboardJson(),
                    new TypeReference<List<PlayerRankDTO>>() {}
            );

            List<QuestionDetailDTO> questionDetails = objectMapper.readValue(
                    result.getQuestionDetailsJson(),
                    new TypeReference<List<QuestionDetailDTO>>() {}
            );

            return ResponseEntity.ok(GameHistoryDTO.builder()
                    .gameId(result.getGame().getId())  // ✅ 添加 gameId
                    .roomCode(result.getGame().getRoomCode())  // ✅ 从 game 获取
                    .startTime(result.getGame().getStartTime())  // ✅ 从 game 获取
                    .endTime(result.getGame().getEndTime())  // ✅ 从 game 获取
                    .questionCount(result.getQuestionCount())
                    .playerCount(result.getPlayerCount())
                    .leaderboard(leaderboard)
                    .questionDetails(questionDetails)
                    .build());

        } catch (Exception e) {
            log.error("解析游戏结果失败: gameId={}", gameId, e);
            throw new BusinessException("解析游戏结果失败");
        }
    }

    private GameHistorySummaryDTO convertToSummary(GameResultEntity entity, String playerId) {
        try {
            List<PlayerRankDTO> leaderboard = objectMapper.readValue(
                    entity.getLeaderboardJson(),
                    new TypeReference<List<PlayerRankDTO>>() {}
            );

            PlayerRankDTO targetPlayer;
            if (playerId != null) {
                targetPlayer = leaderboard.stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .orElse(null);

                if (targetPlayer == null) {
                    return null;
                }
            } else {
                targetPlayer = leaderboard.get(0);
            }

            return GameHistorySummaryDTO.builder()
                    .gameId(entity.getGame().getId())  // ✅ 从 game 获取
                    .roomCode(entity.getGame().getRoomCode())  // ✅ 从 game 获取
                    .endTime(entity.getGame().getEndTime())  // ✅ 从 game 获取
                    .questionCount(entity.getQuestionCount())
                    .playerCount(entity.getPlayerCount())
                    .myScore(targetPlayer.getTotalScore())
                    .myRank(targetPlayer.getRank())
                    .build();

        } catch (Exception e) {
            log.error("转换DTO失败", e);
            return null;
        }
    }
}
