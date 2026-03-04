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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            InputStream is = getClass().getResourceAsStream("/questions.json");
            if (is == null) {
                log.warn("questions.json 未找到，跳过初始化");
                return;
            }
            List<QuestionDTO> dtos = objectMapper.readValue(is, new TypeReference<>() {});

            if (questionRepository.count() == 0) {
                log.info("数据库无题目，开始初始化...");
                for (QuestionDTO dto : dtos) {
                    saveQuestion(dto);
                }
                log.info("题目初始化完成，共导入 {} 条", dtos.size());
            } else {
                // 数据库已有题目：修复缺失或错误的配置
                repairMissingBidConfigs(dtos);
                repairRepeatableMetadata(dtos);
            }
        } catch (IOException e) {
            throw new RuntimeException("题目初始化失败", e);
        }
    }

    /**
     * 修复已有 BID 题目中缺失的 bid_question_config 记录
     */
    private void repairMissingBidConfigs(List<QuestionDTO> dtos) {
        Map<String, QuestionDTO> dtoByStrategyId = new HashMap<>();
        for (QuestionDTO dto : dtos) {
            if (dto.getStrategyId() != null && dto.getType() == QuestionType.BID
                    && dto.getMin() != null && dto.getMax() != null) {
                dtoByStrategyId.put(dto.getStrategyId(), dto);
            }
        }

        List<QuestionEntity> bidQuestions = questionRepository.findAll().stream()
                .filter(q -> q.getType() == QuestionType.BID)
                .toList();

        int repaired = 0;
        for (QuestionEntity entity : bidQuestions) {
            if (!bidConfigRepository.existsByQuestion_Id(entity.getId())) {
                QuestionDTO dto = dtoByStrategyId.get(entity.getStrategyId());
                if (dto != null) {
                    saveBidConfig(entity, dto);
                    repaired++;
                    log.info("修复 BID 配置: strategyId={}, min={}, max={}, step={}",
                            entity.getStrategyId(), dto.getMin(), dto.getMax(), dto.getStep());
                }
            }
        }
        if (repaired > 0) {
            log.info("共修复 {} 个缺失的 BID 配置", repaired);
        }
    }

    /**
     * 修复 QR（可重复）题目的元数据：将 isRepeatable 设为 true 并写入正确的 repeatTimes。
     * 针对 questions.json 中 isRepeatable=true 的条目，找到 DB 中对应的 QuestionMetadata
     * 并 upsert（不存在则创建，存在但值错误则更新）。
     */
    private void repairRepeatableMetadata(List<QuestionDTO> dtos) {
        Map<String, QuestionDTO> dtoByStrategyId = new HashMap<>();
        for (QuestionDTO dto : dtos) {
            if (dto.getStrategyId() != null && Boolean.TRUE.equals(dto.getIsRepeatable())
                    && dto.getRepeatTimes() != null) {
                dtoByStrategyId.put(dto.getStrategyId(), dto);
            }
        }
        if (dtoByStrategyId.isEmpty()) return;

        // 按 strategyId 查找对应的 QuestionEntity
        List<QuestionEntity> allEntities = questionRepository.findAll();
        int repaired = 0;
        for (QuestionEntity entity : allEntities) {
            QuestionDTO dto = dtoByStrategyId.get(entity.getStrategyId());
            if (dto == null) continue;

            QuestionMetadata existing = metadataRepository.findByQuestionId(entity.getId()).orElse(null);
            if (existing != null) {
                if (!Boolean.TRUE.equals(existing.getIsRepeatable())
                        || !dto.getRepeatTimes().equals(existing.getRepeatTimes())) {
                    existing.setIsRepeatable(true);
                    existing.setRepeatTimes(dto.getRepeatTimes());
                    metadataRepository.save(existing);
                    repaired++;
                    log.info("修复 repeatable 元数据: strategyId={}, repeatTimes={}",
                            entity.getStrategyId(), dto.getRepeatTimes());
                }
            } else {
                QuestionMetadata meta = QuestionMetadata.builder()
                        .questionId(entity.getId())
                        .isRepeatable(true)
                        .repeatTimes(dto.getRepeatTimes())
                        .build();
                metadataRepository.save(meta);
                entity.setHasMetadata(true);
                questionRepository.save(entity);
                repaired++;
                log.info("新增 repeatable 元数据: strategyId={}, repeatTimes={}",
                        entity.getStrategyId(), dto.getRepeatTimes());
            }
        }
        if (repaired > 0) {
            log.info("共修复/新增 {} 个 repeatable 元数据", repaired);
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