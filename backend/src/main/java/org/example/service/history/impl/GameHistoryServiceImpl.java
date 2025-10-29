package org.example.service.history.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.entity.QuestionOption;
import org.example.entity.QuestionType;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.repository.*;
import org.example.service.cache.RoomCache;
import org.example.service.history.GameHistoryService;
import org.example.service.leaderboard.LeaderboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏历史服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GameHistoryServiceImpl implements GameHistoryService {

    private final GameResultRepository gameResultRepository;
    private final GameRepository gameRepository;
    private final ObjectMapper objectMapper;
    private final RoomCache roomCache;
    private final LeaderboardService leaderboardService;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;


    @Override
    public List<GameHistorySummaryDTO> getHistoryList(Integer days, String playerId) {
        log.info("=== 获取历史记录列表 ===");
        log.info("days: {}, playerId: {}", days, playerId);

        // 查询游戏结果
        List<GameResultEntity> results = queryGameResults(days);

        log.info("📊 数据库查询结果数量: {}", results.size());

        if (results.isEmpty()) {
            log.warn("❌ 数据库中没有任何游戏结果记录！");
            return List.of();
        }

        // 转换为摘要DTO
        List<GameHistorySummaryDTO> summaries = results.stream()
                .map(result -> convertToSummary(result, playerId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("✅ 返回 {} 条符合条件的记录", summaries.size());
        return summaries;
    }

    @Override
    public GameHistoryDTO getHistoryDetail(Long gameId) {
        log.info("=== 获取游戏详情 ===");
        log.info("gameId: {}", gameId);

        try {
            // 查询游戏实体
            GameEntity game = gameRepository.findById(gameId)
                    .orElseThrow(() -> new BusinessException("游戏不存在"));

            // 查询游戏结果
            GameResultEntity result = gameResultRepository.findByGameIdWithDetails(gameId)
                    .orElseThrow(() -> new BusinessException("游戏结果不存在"));

            // 解析JSON并构建DTO
            return parseGameResultEntity(result);

        } catch (BusinessException e) {
            log.error("获取游戏详情失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("解析游戏结果失败: gameId={}", gameId, e);
            throw new BusinessException("获取游戏详情失败: " + e.getMessage());
        }
    }

    @Override
    public GameHistoryDTO getGameHistoryByRoomCode(String roomCode) {
        log.info("=== 根据房间号获取游戏历史 ===");
        log.info("roomCode: {}", roomCode);

        try {
            // ✅ 改：使用新方法
            Optional<GameResultEntity> resultOpt = gameResultRepository.findByRoomCodeWithDetails(roomCode);

            if (resultOpt.isPresent()) {
                log.info("✅ 找到已保存的游戏结果");
                GameResultEntity result = resultOpt.get();
                return parseGameResultEntity(result);
            } else {
                log.info("⚠️ 未找到已保存的游戏结果，返回当前游戏状态");
                return getCurrentGameStatus(roomCode);
            }
        } catch (Exception e) {
            log.error("获取游戏历史失败: roomCode={}", roomCode, e);
            throw new BusinessException("获取游戏历史失败: " + e.getMessage());
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 查询游戏结果（支持时间过滤）
     */
    private List<GameResultEntity> queryGameResults(Integer days) {
        if (days != null) {
            LocalDateTime after = LocalDateTime.now().minusDays(days);
            log.info("查询 {} 天内的记录，after: {}", days, after);
            return gameResultRepository.findByCreatedAtAfterOrderByCreatedAtDesc(after);
        } else {
            log.info("查询所有记录");
            return gameResultRepository.findAllByOrderByCreatedAtDesc();
        }
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
                // 查找指定玩家
                targetPlayer = leaderboard.stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .orElse(null);

                if (targetPlayer == null) {
                    log.debug("未找到playerId={} 的玩家数据，跳过该记录", playerId);
                    return null;
                }
            } else {
                // 没有指定玩家，返回第一名
                targetPlayer = leaderboard.isEmpty() ? null : leaderboard.get(0);
                if (targetPlayer == null) {
                    log.warn("排行榜为空，跳过该记录");
                    return null;
                }
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
            log.error("转换DTO失败: gameId={}", entity.getGame().getId(), e);
            return null;
        }
    }

    /**
     * 解析 GameResultEntity 为详细DTO
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

    private GameHistoryDTO getCurrentGameStatus(String roomCode){
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);
        GameEntity game = gameRepository.findByRoomCodeWithRoom(roomCode)
                .orElseThrow(() -> new BusinessException("游戏记录不存在"));

        List<PlayerRankDTO> leaderboard = leaderboardService.buildLeaderboard(gameRoom);
        List<QuestionDetailDTO> questionDetails = buildQuestionDetails(gameRoom);

        return GameHistoryDTO.builder()
                .gameId(game.getId())
                .roomCode(roomCode)
                .startTime(game.getStartTime())
                .endTime(game.getEndTime())
                .questionCount(gameRoom.getQuestions().size())
                .playerCount(gameRoom.getPlayers().size())
                .leaderboard(leaderboard)
                .questionDetails(questionDetails)
                .build();
    }

    /**
     * 构建题目详情（从当前游戏状态）
     */
    private List<QuestionDetailDTO> buildQuestionDetails(GameRoom gameRoom) {
        List<QuestionDetailDTO> details = new ArrayList<>();

        for (int i = 0; i < gameRoom.getQuestions().size(); i++) {
            QuestionDTO question = gameRoom.getQuestions().get(i);
            Map<String, String> submissions = gameRoom.getSubmissions().get(i);

            if (submissions == null) {
                continue;
            }

            Map<String, Integer> choiceCounts = new HashMap<>();
            for (String choice : submissions.values()) {
                choiceCounts.put(choice, choiceCounts.getOrDefault(choice, 0) + 1);
            }

            Map<String, GameRoom.QuestionScoreDetail> questionScores =
                    gameRoom.getQuestionScores().getOrDefault(i, new HashMap<>());

            List<PlayerSubmissionDTO> playerSubmissions = new ArrayList<>();
            for (Map.Entry<String, String> entry : submissions.entrySet()) {
                String playerId = entry.getKey();
                String choice = entry.getValue();

                gameRoom.getPlayers().stream()
                        .filter(p -> p.getPlayerId().equals(playerId))
                        .findFirst()
                        .ifPresent(player -> {
                            GameRoom.QuestionScoreDetail scoreDetail = questionScores.get(playerId);
                            Integer baseScore = scoreDetail != null ? scoreDetail.getBaseScore() : 0;
                            Integer finalScore = scoreDetail != null ? scoreDetail.getFinalScore() : 0;

                            playerSubmissions.add(PlayerSubmissionDTO.builder()
                                    .playerId(playerId)
                                    .playerName(player.getName())
                                    .choice(choice)
                                    .baseScore(baseScore)
                                    .finalScore(finalScore)
                                    .submittedAt(null)
                                    .build());
                        });
            }

            String optionText = formatOptions(question);

            details.add(QuestionDetailDTO.builder()
                    .questionIndex(i)
                    .questionText(question.getText())
                    .optionText(optionText)
                    .questionType(question.getType())
                    .playerSubmissions(playerSubmissions)
                    .choiceCounts(choiceCounts)
                    .build());
        }

        return details;
    }

    /**
     * 格式化题目选项文本
     */
    private String formatOptions(QuestionDTO question) {
        if (question == null) {
            return "题目数据错误";
        }

        if (question.getType() == QuestionType.BID) {
            return bidConfigRepository.findByQuestion_Id(question.getId())
                    .map(config -> "出价范围: " + config.getMinValue() + "-" + config.getMaxValue())
                    .orElse("自由出价");
        }

        if (question.getType() == QuestionType.CHOICE) {
            return choiceConfigRepository.findByQuestion_Id(question.getId())
                    .map(config -> {
                        try {
                            List<QuestionOption> options = objectMapper.readValue(
                                    config.getOptionsJson(),
                                    new TypeReference<List<QuestionOption>>() {}
                            );

                            return options.stream()
                                    .sorted(Comparator.comparing(QuestionOption::getKey))
                                    .map(option -> option.getKey() + ". " + option.getText())
                                    .collect(Collectors.joining(" | "));

                        } catch (Exception e) {
                            log.error("解析选项 JSON 失败: questionId={}, error={}",
                                    question.getId(), e.getMessage());
                            return "选项格式错误";
                        }
                    })
                    .orElse("无选项");
        }

        return "未知题型";
    }
}