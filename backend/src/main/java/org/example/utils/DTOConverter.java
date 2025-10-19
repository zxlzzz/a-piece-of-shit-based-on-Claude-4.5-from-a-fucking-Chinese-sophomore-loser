package org.example.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.PlayerDTO;
import org.example.dto.QuestionDTO;
import org.example.entity.PlayerEntity;
import org.example.entity.QuestionEntity;
import org.example.entity.QuestionOption;
import org.example.entity.QuestionType;
import org.example.repository.BidQuestionConfigRepository;
import org.example.repository.ChoiceQuestionConfigRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DTO 转换工具类
 * 统一处理 Entity → DTO 的转换逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor  // ✅ 添加 Lombok 注解
public class DTOConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ✅ 改为非静态字段（使用依赖注入）
    private final ChoiceQuestionConfigRepository choiceConfigRepo;
    private final BidQuestionConfigRepository bidConfigRepo;

    /**
     * QuestionEntity → QuestionDTO（不带配置）
     */
    public QuestionDTO toQuestionDTO(QuestionEntity entity) {
        if (entity == null) {
            return null;
        }

        return QuestionDTO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .text(entity.getText())
                .strategyId(entity.getStrategyId())
                .defaultChoice(entity.getDefaultChoice())
                .minPlayers(entity.getMinPlayers())
                .maxPlayers(entity.getMaxPlayers())
                .build();
    }

    /**
     * QuestionEntity → QuestionDTO（带配置）
     * ✅ 推荐使用这个方法
     */
    public QuestionDTO toQuestionDTOWithConfig(QuestionEntity entity) {
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

        // 🔥 选择题：优先用 JOIN FETCH，否则查库
        if (entity.getType() == QuestionType.CHOICE) {
            if (entity.getChoiceConfig() != null) {
                dto.setOptions(parseOptions(entity.getChoiceConfig().getOptionsJson()));
            } else {
                choiceConfigRepo.findByQuestion_Id(entity.getId())
                        .ifPresent(config -> dto.setOptions(parseOptions(config.getOptionsJson())));
            }
        }

        // 🔥 竞价题：优先用 JOIN FETCH，否则查库
        if (entity.getType() == QuestionType.BID) {
            if (entity.getBidConfig() != null) {
                dto.setMin(entity.getBidConfig().getMinValue());
                dto.setMax(entity.getBidConfig().getMaxValue());
                dto.setStep(entity.getBidConfig().getStep());
            } else {
                bidConfigRepo.findByQuestion_Id(entity.getId())
                        .ifPresent(config -> {
                            dto.setMin(config.getMinValue());
                            dto.setMax(config.getMaxValue());
                            dto.setStep(config.getStep());
                        });
            }
        }

        return dto;
    }

    /**
     * PlayerEntity → PlayerDTO
     */
    public PlayerDTO toPlayerDTO(PlayerEntity entity) {
        if (entity == null) {
            return null;
        }

        return PlayerDTO.builder()
                .playerId(entity.getPlayerId())
                .name(entity.getName())
                .score(0)
                .ready(entity.getReady())
                .build();
    }

    /**
     * 解析 optionsJson 为 QuestionOption 列表
     */
    private List<QuestionOption> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(
                    optionsJson,
                    new TypeReference<List<QuestionOption>>() {}
            );
        } catch (Exception e) {
            log.error("解析 optionsJson 失败: {}", optionsJson, e);
            return null;
        }
    }

    /**
     * 序列化 options 为 JSON 字符串
     */
    public String toOptionsJson(List<QuestionOption> options) {
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