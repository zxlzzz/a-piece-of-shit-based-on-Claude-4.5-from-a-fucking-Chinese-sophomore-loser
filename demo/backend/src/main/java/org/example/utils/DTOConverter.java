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
@RequiredArgsConstructor  //  添加 Lombok 注解
public class DTOConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 改为非静态字段（使用依赖注入）
    private final ChoiceQuestionConfigRepository choiceConfigRepo;
    private final BidQuestionConfigRepository bidConfigRepo;


    /**
     * QuestionEntity → QuestionDTO（带配置）
     *  推荐使用这个方法
     */
    public QuestionDTO toQuestionDTOWithConfig(QuestionEntity entity) {
        if (entity == null) {
            return null;
        }

        QuestionDTO dto = QuestionDTO.builder()
                .id(entity.getId())
                .type(entity.getType())
                .text(entity.getText())
                .calculateRule(entity.getCalculateRule())  //  添加计分规则
                .strategyId(entity.getStrategyId())
                .defaultChoice(entity.getDefaultChoice())
                .minPlayers(entity.getMinPlayers())
                .maxPlayers(entity.getMaxPlayers())
                .build();

        // 选择题：优先用 JOIN FETCH，否则查库
        if (entity.getType() == QuestionType.CHOICE) {
            if (entity.getChoiceConfig() != null) {
                dto.setOptions(parseOptions(entity.getChoiceConfig().getOptionsJson()));
            } else {
                choiceConfigRepo.findByQuestion_Id(entity.getId())
                        .ifPresent(config -> dto.setOptions(parseOptions(config.getOptionsJson())));
            }
        }

        // 竞价题：优先用 JOIN FETCH，否则查库
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

}