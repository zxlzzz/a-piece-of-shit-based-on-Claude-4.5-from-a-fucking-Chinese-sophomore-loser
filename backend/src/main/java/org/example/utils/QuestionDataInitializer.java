package org.example.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
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
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;
    private final QuestionMetadataRepository metadataRepository;
    private final ObjectMapper objectMapper;  // ✅ 注入全局 ObjectMapper

    @PostConstruct
    @Transactional
    public void init() {
        try {log.info("数据库中已有题目，跳过初始化");
            if (questionRepository.count() > 0) {
                
                return;
            }


            InputStream is = getClass().getResourceAsStream("/src/main/questions.json");
            if (is == null) {
                throw new FileNotFoundException("questions.json not found in classpath");
            }

            List<QuestionDTO> dtos = objectMapper.readValue(is, new TypeReference<>() {});

            System.out.println(dtos);

        } catch (IOException e) {

            throw new RuntimeException("题目初始化失败", e);
        }
    }

    private void saveQuestion(QuestionDTO dto) throws IOException {


        // 1. 创建并保存 QuestionEntity
        QuestionEntity entity = QuestionEntity.builder()
                .type(dto.getType())
                .text(dto.getText())
                .calculateRule(dto.getCalculateRule())
                .strategyId(dto.getStrategyId())
                .minPlayers(dto.getMinPlayers())
                .maxPlayers(dto.getMaxPlayers())
                .defaultChoice(dto.getDefaultChoice())
                .hasMetadata(false)
                .build();

        QuestionEntity savedEntity = questionRepository.save(entity);


        // 2. 根据题目类型保存对应配置
        if (dto.getType() == QuestionType.CHOICE) {
            saveChoiceConfig(savedEntity, dto);
        } else if (dto.getType() == QuestionType.BID) {
            saveBidConfig(savedEntity, dto);
        }

        // 3. 保存元数据(如果有)
        if (needsMetadata(dto)) {
            saveMetadata(savedEntity, dto);
        }
    }

    private void saveChoiceConfig(QuestionEntity entity, QuestionDTO dto) throws IOException {
        if (dto.getOptions() == null || dto.getOptions().isEmpty()) {

            return;
        }

        String optionsJson = objectMapper.writeValueAsString(dto.getOptions());
        ChoiceQuestionConfig config = ChoiceQuestionConfig.builder()
                .question(entity)
                .optionsJson(optionsJson)
                .build();

        choiceConfigRepository.save(config);

    }

    private void saveBidConfig(QuestionEntity entity, QuestionDTO dto) {
        if (dto.getMin() == null || dto.getMax() == null) {

            return;
        }

        BidQuestionConfig config = BidQuestionConfig.builder()
                .question(entity)
                .minValue(dto.getMin())
                .maxValue(dto.getMax())
                .step(dto.getStep() != null ? dto.getStep() : 1)  // 默认步长为1
                .build();

        bidConfigRepository.save(config);
    }

    private boolean needsMetadata(QuestionDTO dto) {
        return dto.getSequenceGroupId() != null
                || dto.getIsRepeatable() != null
                || dto.getRepeatGroupId() != null;
    }

    private void saveMetadata(QuestionEntity entity, QuestionDTO dto) {
        QuestionMetadata metadata = QuestionMetadata.builder()
                .questionId(entity.getId())
                .sequenceGroupId(dto.getSequenceGroupId())
                .sequenceOrder(dto.getSequenceOrder())
                .totalSequenceCount(dto.getTotalSequenceCount())
                .isRepeatable(dto.getIsRepeatable())
                .repeatTimes(dto.getRepeatTimes())
                .repeatInterval(dto.getRepeatInterval())
                .repeatGroupId(dto.getRepeatGroupId())
                .build();

        metadataRepository.save(metadata);

        // 更新 hasMetadata 标记
        entity.setHasMetadata(true);
        questionRepository.save(entity);

    }


}