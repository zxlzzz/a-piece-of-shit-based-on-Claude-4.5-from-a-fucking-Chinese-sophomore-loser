package org.example.service.submission.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.GameEntity;
import org.example.entity.PlayerEntity;
import org.example.entity.QuestionEntity;
import org.example.entity.SubmissionEntity;
import org.example.exception.BusinessException;
import org.example.pojo.GameRoom;
import org.example.repository.GameRepository;
import org.example.repository.PlayerRepository;
import org.example.repository.QuestionRepository;
import org.example.repository.SubmissionRepository;
import org.example.service.cache.RoomCache;
import org.example.service.submission.SubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 答题提交服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubmissionServiceImpl implements SubmissionService {

    private final RoomCache roomCache;
    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;
    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;

    @Override
    @Transactional
    public void submitAnswer(String roomCode, String playerId, String choice) {
        GameRoom gameRoom = roomCache.getOrThrow(roomCode);
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();  // ✅ DTO

        if (currentQuestion == null) {
            throw new BusinessException("当前没有有效题目");
        }

        // 🔥 根据 DTO 的 ID 查询 Entity
        QuestionEntity questionEntity = questionRepository.findById(currentQuestion.getId())
                .orElseThrow(() -> new BusinessException("题目不存在: " + currentQuestion.getId()));

        PlayerEntity player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new BusinessException("玩家不存在: " + playerId));

        GameEntity game = gameRepository.findById(gameRoom.getGameId())
                .orElseThrow(() -> new BusinessException("游戏不存在"));

        SubmissionEntity submission = SubmissionEntity.builder()
                .player(player)
                .question(questionEntity)  // ✅ 使用 Entity
                .game(game)
                .choice(choice)
                .build();

        submissionRepository.save(submission);

        // 更新内存状态
        gameRoom.getSubmissions()
                .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                .put(playerId, choice);

        // 记录提交时间
        if (gameRoom.getCurrentContext() != null) {
            gameRoom.getCurrentContext().recordSubmissionTime(playerId);
        }

        // 标记玩家已提交
        gameRoom.getPlayers().stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .findFirst()
                .ifPresent(p -> p.setReady(true));

        log.info("💾 玩家 {} 提交答案: {}", playerId, choice);
    }

    @Override
    @Transactional
    public void fillDefaultAnswers(GameRoom gameRoom) {
        QuestionDTO currentQuestion = gameRoom.getCurrentQuestion();  // ✅ DTO
        if (currentQuestion == null) {
            return;
        }

        // 🔥 查询 Entity
        QuestionEntity questionEntity = questionRepository.findById(currentQuestion.getId())
                .orElseThrow(() -> new BusinessException("题目不存在: " + currentQuestion.getId()));

        GameEntity game = gameRepository.findById(gameRoom.getGameId())
                .orElseThrow(() -> new BusinessException("游戏不存在"));

        Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                .get(gameRoom.getCurrentIndex());

        for (PlayerDTO player : gameRoom.getPlayers()) {
            if (currentRoundSubmissions == null || !currentRoundSubmissions.containsKey(player.getPlayerId())) {
                String defaultChoice = currentQuestion.getDefaultChoice() != null
                        ? currentQuestion.getDefaultChoice()
                        : "4";

                PlayerEntity playerEntity = playerRepository.findByPlayerId(player.getPlayerId())
                        .orElseThrow(() -> new BusinessException("玩家不存在: " + player.getPlayerId()));

                SubmissionEntity submission = SubmissionEntity.builder()
                        .player(playerEntity)
                        .question(questionEntity)  // ✅ 使用 Entity
                        .game(game)
                        .choice(defaultChoice)
                        .build();

                submissionRepository.save(submission);

                gameRoom.getSubmissions()
                        .computeIfAbsent(gameRoom.getCurrentIndex(), k -> new ConcurrentHashMap<>())
                        .put(player.getPlayerId(), defaultChoice);

                log.info("玩家 {} 超时，填充默认答案: {}", player.getName(), defaultChoice);
            }
        }
    }

    @Override
    public boolean allSubmitted(GameRoom gameRoom) {
        Map<String, String> currentRoundSubmissions = gameRoom.getSubmissions()
                .get(gameRoom.getCurrentIndex());

        if (currentRoundSubmissions == null) {
            return false;
        }

        return gameRoom.getPlayers().stream()
                .allMatch(p -> currentRoundSubmissions.containsKey(p.getPlayerId()));
    }
}