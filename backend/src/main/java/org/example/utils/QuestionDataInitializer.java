package org.example.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.*;
import org.example.repository.*;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class QuestionDataInitializer {

    private final QuestionRepository questionRepository;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;  // ← 新增
    private final BidQuestionConfigRepository bidConfigRepository;        // ← 新增
    private final QuestionMetadataRepository metadataRepository;          // ← 新增

    @PostConstruct
    @Transactional
    public void init() throws IOException {
        if (questionRepository.count() > 0) {
            log.info("数据库中已有题目，跳过初始化");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/questions.json");
        if (is == null) {
            throw new FileNotFoundException("questions.json not found");
        }

        List<QuestionDTO> dtos = mapper.readValue(is, new TypeReference<List<QuestionDTO>>() {});

        for (QuestionDTO dto : dtos) {
            // 1. 保存 QuestionEntity（基础信息）
            QuestionEntity entity = QuestionEntity.builder()
                    .type(dto.getType())
                    .text(dto.getText())
                    .strategyId(dto.getStrategyId())
                    .minPlayers(dto.getMinPlayers())
                    .maxPlayers(dto.getMaxPlayers())
                    .defaultChoice(dto.getDefaultChoice())
                    .hasChoiceConfig(false)  // 先设为 false
                    .hasBidConfig(false)
                    .hasMetadata(false)
                    .build();

            QuestionEntity savedEntity = questionRepository.save(entity);

            // 2. 保存配置（根据题型）
            if ("choice".equals(dto.getType()) && dto.getOptions() != null && !dto.getOptions().isEmpty()) {
                ChoiceQuestionConfig config = ChoiceQuestionConfig.builder()
                        .questionId(savedEntity.getId())
                        .optionsJson(mapper.writeValueAsString(dto.getOptions()))
                        .build();
                choiceConfigRepository.save(config);

                // 更新标记
                savedEntity.setHasChoiceConfig(true);
                questionRepository.save(savedEntity);
            }

            if ("bid".equals(dto.getType()) && dto.getMin() != null && dto.getMax() != null) {
                BidQuestionConfig config = BidQuestionConfig.builder()
                        .questionId(savedEntity.getId())
                        .minValue(dto.getMin())
                        .maxValue(dto.getMax())
                        .step(dto.getStep())
                        .build();
                bidConfigRepository.save(config);

                // 更新标记
                savedEntity.setHasBidConfig(true);
                questionRepository.save(savedEntity);
            }

            // 3. 保存元数据（序列/重复配置，如果有的话）
            if (dto.getSequenceGroupId() != null || dto.getIsRepeatable() != null) {
                QuestionMetadata metadata = QuestionMetadata.builder()
                        .questionId(savedEntity.getId())
                        .sequenceGroupId(dto.getSequenceGroupId())
                        .sequenceOrder(dto.getSequenceOrder())
                        .totalSequenceCount(dto.getTotalSequenceCount())
                        .isRepeatable(dto.getIsRepeatable())
                        .repeatTimes(dto.getRepeatTimes())
                        .repeatInterval(dto.getRepeatInterval())
                        .repeatGroupId(dto.getRepeatGroupId())
                        .prerequisiteQuestionIds(dto.getPrerequisiteQuestionIds())
                        .build();
                metadataRepository.save(metadata);

                // 更新标记
                savedEntity.setHasMetadata(true);
                questionRepository.save(savedEntity);
            }
        }

        log.info("从 questions.json 导入了 {} 道题目到数据库", dtos.size());
    }

    // ========== 内部 DTO 类 ==========
    @Data
    private static class QuestionDTO {
        // 基础信息
        private Long id;
        private String type;
        private String text;
        private String strategyId;
        private Integer minPlayers;
        private Integer maxPlayers;
        private String defaultChoice;

        // choice 题配置
        private List<QuestionOption> options;  // ← 改类型

        // bid 题配置
        private Integer min;
        private Integer max;
        private Integer step;

        // 序列配置
        private String sequenceGroupId;
        private Integer sequenceOrder;
        private Integer totalSequenceCount;

        // 重复配置
        private Boolean isRepeatable;
        private Integer repeatTimes;
        private Integer repeatInterval;
        private String repeatGroupId;
        private String prerequisiteQuestionIds;
    }
}