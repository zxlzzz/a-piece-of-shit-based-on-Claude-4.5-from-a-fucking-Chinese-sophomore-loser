package org.example.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.PlayerEntity;
import org.example.entity.QuestionEntity;
import org.example.entity.QuestionOption;
import org.example.repository.BidQuestionConfigRepository;
import org.example.repository.ChoiceQuestionConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.example.config.WebSocketConfig.WebSocketChannelInterceptor.log;

/**
 * DTO 转换工具类
 * 统一处理 Entity → DTO 的转换逻辑
 */
@Slf4j
@Component
public class DTOConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * QuestionEntity → QuestionDTO
     */
    public static QuestionDTO toQuestionDTO(QuestionEntity entity) {
        if (entity == null) {
            return null;
        }

        return QuestionDTO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .text(entity.getText())
                .strategyId(entity.getStrategyId())  // ← 新增
                .defaultChoice(entity.getDefaultChoice())  // ← 新增
                .minPlayers(entity.getMinPlayers())  // ← 新增
                .maxPlayers(entity.getMaxPlayers())  // ← 新增
                // ❌ 删除：.min(entity.getMin())
                // ❌ 删除：.max(entity.getMax())
                // ❌ 删除：.options(parseOptions(entity.getOptionsJson()))
                .build();
    }

    public static QuestionDTO toQuestionDTOWithConfig(
            QuestionEntity entity,
            ChoiceQuestionConfigRepository choiceConfigRepo,
            BidQuestionConfigRepository bidConfigRepo) {

        if (entity == null) {
            return null;
        }

        QuestionDTO dto = QuestionDTO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .text(entity.getText())
                .strategyId(entity.getStrategyId())
                .defaultChoice(entity.getDefaultChoice())
                .minPlayers(entity.getMinPlayers())
                .maxPlayers(entity.getMaxPlayers())
                .build();

        // 如果是选择题，查询选项配置
        if ("choice".equals(entity.getType()) && entity.getHasChoiceConfig()) {
            choiceConfigRepo.findByQuestionId(entity.getId())
                    .ifPresent(config -> {
                        dto.setOptions(parseOptions(config.getOptionsJson()));
                    });
        }

        // 如果是竞价题，查询竞价配置
        if ("bid".equals(entity.getType()) && entity.getHasBidConfig()) {
            bidConfigRepo.findByQuestionId(entity.getId())
                    .ifPresent(config -> {
                        dto.setMin(config.getMinValue());
                        dto.setMax(config.getMaxValue());
                        dto.setStep(config.getStep());
                    });
        }

        return dto;
    }

    /**
     * PlayerEntity → PlayerDTO
     */
    public static PlayerDTO toPlayerDTO(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }

        return PlayerDTO.builder()
                .playerId(entity.getPlayerId())
                .name(entity.getName())
                .score(0)  // 初始分数为0
                .ready(entity.getReady())
                .build();
    }

    /**
     * 解析 optionsJson 为 QuestionOption 列表
     */
    private static List<QuestionOption> parseOptions(String optionsJson) {  // ← 改返回类型
        if (optionsJson == null || optionsJson.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    optionsJson,
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class,
                            QuestionOption.class  // ← 改类型
                    )
            );
        } catch (Exception e) {
            log.error("解析 optionsJson 失败: {}", optionsJson, e);
            return null;
        }
    }

    /**
     * 序列化 options 为 JSON 字符串
     */
    public static String toOptionsJson(List<QuestionOption> options) {  // ← 改参数类型
        if (options == null || options.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            log.error("序列化 options 失败", e);
            return null;
        }
    }
}
