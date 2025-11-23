package org.example.service.persistence.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerRankDTO;
import org.example.dto.PlayerSubmissionDTO;
import org.example.dto.QuestionDTO;
import org.example.dto.QuestionDetailDTO;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.entity.QuestionType;
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
    @Transactional(timeout = 10)  // 🔥 P0-4修复：添加10秒超时，防止长时间占用连接
    public void saveGameResult(GameRoom gameRoom) {
        if (gameRoom == null) {
            log.warn("⚠️ GameRoom对象为null，跳过保存");
            return;
        }

        String roomCode = gameRoom.getRoomCode();
        log.info("📝 开始保存游戏结果: roomCode={}, finished={}", roomCode, gameRoom.isFinished());

        if (!gameRoom.isFinished()) {
            log.warn("⚠️ 房间 {} 未结束(finished=false)，跳过保存", roomCode);
            return;
        }

        try {
            log.info("📝 正在查找游戏实体: gameId={}, roomCode={}, isTestRoom={}",
                    gameRoom.getGameId(), roomCode, gameRoom.isTestRoom());

            GameEntity game = gameRepository.findById(gameRoom.getGameId())
                    .orElseThrow(() -> new BusinessException("游戏不存在: gameId=" + gameRoom.getGameId()));

            log.info("📝 游戏实体找到: gameId={}, isTest={}", game.getId(), game.getIsTest());

            log.info("📝 正在构建排行榜数据: playerCount={}", gameRoom.getPlayers().size());
            List<PlayerRankDTO> leaderboard = leaderboardService.buildLeaderboard(gameRoom);
            log.info("📝 排行榜构建完成: leaderboardSize={}", leaderboard.size());

            log.info("📝 正在构建题目详情: questionCount={}", gameRoom.getQuestions().size());
            List<QuestionDetailDTO> questionDetails = buildQuestionDetails(gameRoom);
            log.info("📝 题目详情构建完成: detailsSize={}", questionDetails.size());

            String leaderboardJson = objectMapper.writeValueAsString(leaderboard);
            String questionDetailsJson = objectMapper.writeValueAsString(questionDetails);

            log.info("📝 JSON序列化完成: leaderboardLength={}, questionDetailsLength={}",
                    leaderboardJson.length(), questionDetailsJson.length());

            GameResultEntity entity = GameResultEntity.builder()
                    .game(game)
                    .room(game.getRoom())
                    .questionCount(gameRoom.getQuestions().size())
                    .playerCount(gameRoom.getPlayers().size())
                    .leaderboardJson(leaderboardJson)
                    .questionDetailsJson(questionDetailsJson)
                    .build();

            log.info("📝 正在保存GameResultEntity到数据库...");
            GameResultEntity saved = gameResultRepository.save(entity);
            log.info("✅ 游戏结果保存成功: roomCode={}, resultId={}, gameId={}, isTest={}",
                    roomCode, saved.getId(), game.getId(), game.getIsTest());

        } catch (Exception e) {
            log.error("❌ 保存游戏结果失败: roomCode={}, gameId={}, error={}",
                    roomCode, gameRoom.getGameId(), e.getMessage(), e);
            throw new RuntimeException("保存游戏结果失败", e);
        }
    }

    // ==================== 私有方法 ====================

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

    private String formatOptions(QuestionDTO question) {
        if (question == null) {
            return "题目数据错误";
        }

        // ✅ 修复1: 使用枚举比较,而不是字符串
        if (question.getType() == QuestionType.BID) {
            // ✅ 修复2: 使用正确的 Repository 方法
            return bidConfigRepository.findByQuestion_Id(question.getId())
                    .map(config -> "出价范围: " + config.getMinValue() + "-" + config.getMaxValue())
                    .orElse("自由出价");
        }

        // ✅ 修复3: 使用枚举比较
        if (question.getType() == QuestionType.CHOICE) {
            // ✅ 修复4: 使用正确的 Repository 方法
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