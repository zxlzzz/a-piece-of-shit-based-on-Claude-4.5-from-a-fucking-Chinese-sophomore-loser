package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.repository.GameRepository;
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
    private GameRepository gameRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取所有历史记录列表
     */
    @GetMapping("/history")
    public ResponseEntity<List<GameHistorySummaryDTO>> getHistoryList(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String playerId) {

        List<GameResultEntity> results;

        if (days != null) {
            LocalDateTime after = LocalDateTime.now().minusDays(days);
            results = gameResultRepository.findByCreatedAtAfterOrderByCreatedAtDesc(after);
        } else {
            results = gameResultRepository.findAllByOrderByCreatedAtDesc();
        }

        List<GameHistorySummaryDTO> summaries = results.stream()
                .map(result -> convertToSummary(result, playerId))
                .filter(summary -> summary != null)
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    /**
     * 获取单场游戏的详细历史（用于弹窗）
     */
    @GetMapping("/history/{gameId}")
    public ResponseEntity<GameHistoryDTO> getHistoryDetail(@PathVariable Long gameId) {
        try {
            GameEntity game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new org.example.exception.BusinessException("游戏不存在"));

            GameResultEntity result = gameResultRepository.findByGame(game)
                    .orElseThrow(() -> new org.example.exception.BusinessException("游戏结果不存在"));

            GameHistoryDTO dto = parseGameResultEntity(result);
            return ResponseEntity.ok(dto);

        } catch (org.example.exception.BusinessException e) {
            log.error("获取游戏详情失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("解析游戏结果失败: gameId={}", gameId, e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 解析 GameResultEntity 为 DTO
     */
    private GameHistoryDTO parseGameResultEntity(GameResultEntity result) throws Exception {
        List<PlayerRankDTO> leaderboard = objectMapper.readValue(
                result.getLeaderboardJson(),
                new TypeReference<List<PlayerRankDTO>>() {}
        );

        List<QuestionDetailDTO> questionDetails = objectMapper.readValue(
                result.getQuestionDetailsJson(),
                new TypeReference<List<QuestionDetailDTO>>() {}
        );

        return GameHistoryDTO.builder()
                .gameId(result.getGame().getId())
                .roomCode(result.getGame().getRoom().getRoomCode())
                .startTime(result.getGame().getStartTime())
                .endTime(result.getGame().getEndTime())
                .questionCount(result.getQuestionCount())
                .playerCount(result.getPlayerCount())
                .leaderboard(leaderboard)
                .questionDetails(questionDetails)
                .build();
    }

    /**
     * 转换为摘要 DTO
     */
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
                    .gameId(entity.getGame().getId())
                    .roomCode(entity.getGame().getRoom().getRoomCode())
                    .endTime(entity.getGame().getEndTime())
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