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
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;
    private final QuestionMetadataRepository metadataRepository;
    private final ObjectMapper objectMapper;  // ✅ 注入全局 ObjectMapper

    @PostConstruct
    @Transactional
    public void init() {
        try {
            if (questionRepository.count() > 0) {
                log.info("数据库中已有题目，跳过初始化");
                return;
            }

            log.info("开始初始化题目数据...");

            InputStream is = getClass().getResourceAsStream("/questions.json");
            if (is == null) {
                throw new FileNotFoundException("questions.json not found in classpath");
            }

            List<QuestionDTO> dtos = objectMapper.readValue(is, new TypeReference<>() {});
            log.info("从 questions.json 读取到 {} 道题目", dtos.size());
            System.out.println(dtos);

            int successCount = 0;
            for (QuestionDTO dto : dtos) {
                try {
                    saveQuestion(dto);
                    successCount++;
                } catch (Exception e) {
                    log.error("保存题目失败: id={}, text={}", dto.getId(), dto.getText(), e);
                }
            }

            log.info("题目初始化完成! 成功导入 {}/{} 道题目", successCount, dtos.size());

        } catch (IOException e) {
            log.error("读取 questions.json 失败", e);
            throw new RuntimeException("题目初始化失败", e);
        }
    }

    private void saveQuestion(QuestionDTO dto) throws IOException {
        log.debug("正在保存题目: id={}, type={}, strategyId={}", dto.getId(), dto.getType(), dto.getStrategyId());

        // 1. 创建并保存 QuestionEntity
        QuestionEntity entity = QuestionEntity.builder()
                .type(dto.getType())
                .text(dto.getText())
                .strategyId(dto.getStrategyId())
                .minPlayers(dto.getMinPlayers())
                .maxPlayers(dto.getMaxPlayers())
                .defaultChoice(dto.getDefaultChoice())
                .hasMetadata(false)
                .build();

        QuestionEntity savedEntity = questionRepository.save(entity);
        log.debug("QuestionEntity 保存成功, 数据库ID={}", savedEntity.getId());

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
            log.warn("选择题 {} 没有选项数据", entity.getId());
            return;
        }

        String optionsJson = objectMapper.writeValueAsString(dto.getOptions());
        ChoiceQuestionConfig config = ChoiceQuestionConfig.builder()
                .question(entity)
                .optionsJson(optionsJson)
                .build();

        choiceConfigRepository.save(config);
        log.debug("ChoiceQuestionConfig 保存成功: questionId={}, options={}", entity.getId(), optionsJson);
    }

    private void saveBidConfig(QuestionEntity entity, QuestionDTO dto) {
        if (dto.getMin() == null || dto.getMax() == null) {
            log.warn("竞价题 {} 缺少 min/max 配置", entity.getId());
            return;
        }

        BidQuestionConfig config = BidQuestionConfig.builder()
                .question(entity)
                .minValue(dto.getMin())
                .maxValue(dto.getMax())
                .step(dto.getStep() != null ? dto.getStep() : 1)  // 默认步长为1
                .build();

        bidConfigRepository.save(config);
        log.debug("BidQuestionConfig 保存成功: questionId={}, min={}, max={}, step={}",
                entity.getId(), dto.getMin(), dto.getMax(), config.getStep());
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

        log.debug("QuestionMetadata 保存成功: questionId={}", entity.getId());
    }

    // ========== 内部 DTO 类 ==========
    @Data
    private static class QuestionDTO {
        private Long id;
        private QuestionType type;           // ✅ 直接用枚举类型
        private String text;
        private String strategyId;
        private Integer minPlayers;
        private Integer maxPlayers;
        private String defaultChoice;

        // Choice 题专用
        private List<QuestionOption> options;

        // Bid 题专用
        private Integer min;
        private Integer max;
        private Integer step;

        // 元数据
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