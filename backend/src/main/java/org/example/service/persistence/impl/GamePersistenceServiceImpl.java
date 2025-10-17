package org.example.service.persistence.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerRankDTO;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.entity.QuestionEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.entity.QuestionOption;
import org.example.repository.*;
import org.example.service.cache.RoomCache;
import org.example.service.leaderboard.LeaderboardService;
import org.example.service.persistence.GamePersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏持久化服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GamePersistenceServiceImpl implements GamePersistenceService {

    private final RoomCache roomCache;
    private final GameRepository gameRepository;
    private final GameResultRepository gameResultRepository;
    private final LeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;

    @Override
    @Transactional
    public void saveGameResult(String roomCode) {
        GameRoom gameRoom = roomCache.get(roomCode);
        if (gameRoom == null || !gameRoom.isFinished()) {
            log.warn("⚠️ 房间 {} 不存在或未结束，跳过保存", roomCode);
            return;
        }

        try {
            GameEntity game = gameRepository.findById(gameRoom.getGameId())
                    .orElseThrow(() -> new BusinessException("游戏不存在"));

            List<PlayerRankDTO> leaderboard = leaderboardService.buildLeaderboard(gameRoom);
            List<QuestionDetailDTO> questionDetails = buildQuestionDetails(gameRoom);

            String leaderboardJson = objectMapper.writeValueAsString(leaderboard);
            String questionDetailsJson = objectMapper.writeValueAsString(questionDetails);

            GameResultEntity entity = GameResultEntity.builder()
                    .game(game)
                    .questionCount(gameRoom.getQuestions().size())
                    .playerCount(gameRoom.getPlayers().size())
                    .leaderboardJson(leaderboardJson)
                    .questionDetailsJson(questionDetailsJson)
                    .build();

            gameResultRepository.save(entity);
            log.info("✅ 游戏结果已保存: roomCode={}, gameId={}", roomCode, gameRoom.getGameId());

        } catch (Exception e) {
            log.error("❌ 保存游戏结果失败: roomCode={}", roomCode, e);
            throw new RuntimeException("保存游戏结果失败", e);
        }
    }

    // ==================== 私有方法 ====================

    private List<QuestionDetailDTO> buildQuestionDetails(GameRoom gameRoom) {
        List<QuestionDetailDTO> details = new ArrayList<>();

        for (int i = 0; i < gameRoom.getQuestions().size(); i++) {
            QuestionEntity question = gameRoom.getQuestions().get(i);
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

    private String formatOptions(QuestionEntity question) {
        if (question == null) {
            return "题目数据错误";
        }

        if ("bid".equals(question.getType())) {
            return bidConfigRepository.findByQuestionId(question.getId())
                    .map(config -> "出价范围: " + config.getMinValue() + "-" + config.getMaxValue())
                    .orElse("自由出价");
        }

        if ("choice".equals(question.getType())) {
            return choiceConfigRepository.findByQuestionId(question.getId())
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
                            log.error("解析选项 JSON 失败: {}", e.getMessage());
                            return "选项格式错误";
                        }
                    })
                    .orElse("无选项");
        }

        return "无选项";
    }
}