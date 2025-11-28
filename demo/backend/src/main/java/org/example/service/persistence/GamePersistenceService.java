package org.example.service.persistence;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamePersistenceService {

    private final RoomCache roomCache;
    private final GameRepository gameRepository;
    private final GameResultRepository gameResultRepository;
    private final LeaderboardService leaderboardService;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;

    @Transactional(timeout = 10)
    public void saveGameResult(GameRoom gameRoom) {
        if (gameRoom == null || !gameRoom.isFinished()) {
            return;
        }

        try {
            GameEntity game = gameRepository.findById(gameRoom.getGameId())
                    .orElseThrow(() -> new BusinessException("Game not found: " + gameRoom.getGameId()));

            List<PlayerRankDTO> leaderboard = leaderboardService.buildLeaderboard(gameRoom);
            List<QuestionDetailDTO> questionDetails = buildQuestionDetails(gameRoom);

            String leaderboardJson = objectMapper.writeValueAsString(leaderboard);
            String questionDetailsJson = objectMapper.writeValueAsString(questionDetails);

            GameResultEntity entity = GameResultEntity.builder()
                    .game(game)
                    .room(game.getRoom())
                    .questionCount(gameRoom.getQuestions().size())
                    .playerCount(gameRoom.getPlayers().size())
                    .leaderboardJson(leaderboardJson)
                    .questionDetailsJson(questionDetailsJson)
                    .build();

            gameResultRepository.save(entity);

        } catch (Exception e) {
            log.error("Failed to save game result for room {}: {}", gameRoom.getRoomCode(), e.getMessage(), e);
            throw new RuntimeException("Failed to save game result", e);
        }
    }

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
            return "Invalid question";
        }

        if (question.getType() == QuestionType.BID) {
            return bidConfigRepository.findByQuestion_Id(question.getId())
                    .map(config -> "Bid range: " + config.getMinValue() + "-" + config.getMaxValue())
                    .orElse("Free bid");
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
                            log.error("Failed to parse options JSON for question {}: {}", 
                                    question.getId(), e.getMessage());
                            return "Invalid options format";
                        }
                    })
                    .orElse("No options");
        }

        return "Unknown question type";
    }
}
