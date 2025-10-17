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
            // 1. 保存 QuestionEntity
            QuestionEntity entity = QuestionEntity.builder()
                    .type(dto.getType())
                    .text(dto.getText())
                    .strategyId(dto.getStrategyId())
                    .minPlayers(dto.getMinPlayers())
                    .maxPlayers(dto.getMaxPlayers())
                    .defaultChoice(dto.getDefaultChoice())
                    .hasChoiceConfig(false)
                    .hasBidConfig(false)
                    .hasMetadata(false)
                    .build();

            QuestionEntity savedEntity = questionRepository.save(entity);

            // 2. 保存选择题配置
            if ("choice".equals(dto.getType()) && dto.getOptions() != null && !dto.getOptions().isEmpty()) {
                ChoiceQuestionConfig config = ChoiceQuestionConfig.builder()
                        .question(savedEntity)  // ✅ 改成 question 对象
                        .optionsJson(mapper.writeValueAsString(dto.getOptions()))
                        .build();
                choiceConfigRepository.save(config);
                savedEntity.setHasChoiceConfig(true);
            }

            // 3. 保存竞价题配置
            if ("bid".equals(dto.getType()) && dto.getMin() != null && dto.getMax() != null) {
                BidQuestionConfig config = BidQuestionConfig.builder()
                        .question(savedEntity)  // ✅ 改成 question 对象
                        .minValue(dto.getMin())
                        .maxValue(dto.getMax())
                        .step(dto.getStep())
                        .build();
                bidConfigRepository.save(config);
                savedEntity.setHasBidConfig(true);
            }

            // 4. 保存元数据
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
                        .build();
                metadataRepository.save(metadata);
                savedEntity.setHasMetadata(true);
            }

            // ✅ 只在最后保存一次
            questionRepository.save(savedEntity);
        }

        log.info("从 questions.json 导入了 {} 道题目到数据库", dtos.size());
    }

    // ========== 内部 DTO 类 ==========
    @Data
    private static class QuestionDTO {
        private Long id;
        private QuestionType type;
        private String text;
        private String strategyId;
        private Integer minPlayers;
        private Integer maxPlayers;
        private String defaultChoice;
        private List<QuestionOption> options;
        private Integer min;
        private Integer max;
        private Integer step;
        private String sequenceGroupId;
        private Integer sequenceOrder;
        private Integer totalSequenceCount;
        private Boolean isRepeatable;
        private Integer repeatTimes;
        private Integer repeatInterval;
        private String repeatGroupId;
        private String prerequisiteQuestionIds;
    }
}