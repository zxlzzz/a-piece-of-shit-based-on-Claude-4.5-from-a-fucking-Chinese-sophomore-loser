package org.example.service.statistics.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.ChoiceRecordEntity;
import org.example.entity.PlayerEntity;
import org.example.entity.QuestionEntity;
import org.example.entity.QuestionStatisticsEntity;
import org.example.repository.ChoiceRecordRepository;
import org.example.repository.PlayerRepository;
import org.example.repository.QuestionRepository;
import org.example.repository.QuestionStatisticsRepository;
import org.example.service.statistics.QuestionStatisticsService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionStatisticsServiceImpl implements QuestionStatisticsService {

    private final ChoiceRecordRepository choiceRecordRepository;
    private final QuestionStatisticsRepository questionStatisticsRepository;
    private final QuestionRepository questionRepository;
    private final PlayerRepository playerRepository;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Override
    @Async
    @Transactional
    public void recordChoice(Long questionId, String choice, String playerId,
                              Integer playerCount, ChoiceRecordEntity.GameType gameType, String roomCode) {
        try {
            // 1. 保存详细记录
            QuestionEntity question = questionRepository.getReferenceById(questionId);
            PlayerEntity player = null;
            if (playerId != null) {
                player = playerRepository.findByPlayerId(playerId).orElse(null);
            }

            ChoiceRecordEntity record = ChoiceRecordEntity.builder()
                    .question(question)
                    .player(player)
                    .choice(choice)
                    .playerCount(playerCount)
                    .gameType(gameType)
                    .roomCode(roomCode)
                    .build();

            choiceRecordRepository.save(record);

            // 2. 更新聚合统计
            updateAggregatedStats(questionId, choice, playerCount);

            log.debug("✅ 记录选择: questionId={}, choice={}, playerCount={}, gameType={}",
                    questionId, choice, playerCount, gameType);

        } catch (Exception e) {
            log.error("❌ 记录选择失败: questionId={}, choice={}", questionId, choice, e);
        }
    }

    @Transactional
    protected void updateAggregatedStats(Long questionId, String choice, Integer playerCount) {
        try {
            QuestionStatisticsEntity stats = questionStatisticsRepository
                    .findByQuestionId(questionId)
                    .orElseGet(() -> createNewStats(questionId));

            // 解析现有分布
            Map<Integer, Map<String, Integer>> distribution = parseDistribution(stats.getChoiceDistributionJson());

            // 更新对应人数的统计
            distribution
                    .computeIfAbsent(playerCount, k -> new HashMap<>())
                    .merge(choice, 1, Integer::sum);

            // 保存回去
            stats.setChoiceDistributionJson(objectMapper.writeValueAsString(distribution));
            stats.setTotalPlays(stats.getTotalPlays() + 1);
            stats.setLastPlayedAt(LocalDateTime.now());

            questionStatisticsRepository.save(stats);

        } catch (Exception e) {
            log.error("❌ 更新聚合统计失败: questionId={}", questionId, e);
        }
    }

    @Override
    public Map<Integer, Map<String, Integer>> getQuestionStatistics(Long questionId) {
        return questionStatisticsRepository.findByQuestionId(questionId)
                .map(stats -> parseDistribution(stats.getChoiceDistributionJson()))
                .orElse(new HashMap<>());
    }

    @Override
    public Integer getTotalPlays(Long questionId) {
        return questionStatisticsRepository.findByQuestionId(questionId)
                .map(QuestionStatisticsEntity::getTotalPlays)
                .orElse(0);
    }

    @Override
    public String generateBotChoice(Long questionId, Integer playerCount) {
        Map<Integer, Map<String, Integer>> stats = getQuestionStatistics(questionId);
        Map<String, Integer> distribution = stats.get(playerCount);

        // 如果没有该人数的统计数据，使用所有人数的统计
        if (distribution == null || distribution.isEmpty()) {
            distribution = stats.values().stream()
                    .flatMap(m -> m.entrySet().stream())
                    .collect(HashMap::new,
                            (map, entry) -> map.merge(entry.getKey(), entry.getValue(), Integer::sum),
                            HashMap::putAll);
        }

        // 如果还是没有数据，返回null（由调用者处理）
        if (distribution.isEmpty()) {
            return null;
        }

        // 加权随机选择
        return weightedRandom(distribution);
    }

    // ==================== 私有方法 ====================

    private QuestionStatisticsEntity createNewStats(Long questionId) {
        QuestionEntity question = questionRepository.getReferenceById(questionId);
        return QuestionStatisticsEntity.builder()
                .question(question)
                .choiceDistributionJson("{}")
                .totalPlays(0)
                .build();
    }

    private Map<Integer, Map<String, Integer>> parseDistribution(String json) {
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(json,
                    new TypeReference<Map<Integer, Map<String, Integer>>>() {});
        } catch (Exception e) {
            log.error("解析分布JSON失败: {}", json, e);
            return new HashMap<>();
        }
    }

    private String weightedRandom(Map<String, Integer> distribution) {
        int total = distribution.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            // 均匀随机
            List<String> choices = new ArrayList<>(distribution.keySet());
            return choices.get(random.nextInt(choices.size()));
        }

        int rand = random.nextInt(total);
        int cumulative = 0;

        for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
            cumulative += entry.getValue();
            if (rand < cumulative) {
                return entry.getKey();
            }
        }

        // 兜底
        return distribution.keySet().iterator().next();
    }
}
