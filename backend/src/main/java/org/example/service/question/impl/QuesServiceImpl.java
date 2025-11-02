package org.example.service.question.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
import org.example.dto.TagDTO;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.repository.BidQuestionConfigRepository;
import org.example.repository.ChoiceQuestionConfigRepository;
import org.example.repository.QuestionMetadataRepository;
import org.example.repository.QuestionRepository;
import org.example.service.question.QuesService;
import org.example.service.tag.QuestionTagService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuesServiceImpl implements QuesService {

    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;
    private final QuestionMetadataRepository metadataRepository;
    private final QuestionTagService questionTagService;

    public QuesServiceImpl(
            QuestionRepository questionRepository,
            ObjectMapper objectMapper,
            ChoiceQuestionConfigRepository choiceConfigRepository,
            BidQuestionConfigRepository bidConfigRepository,
            QuestionMetadataRepository metadataRepository,
            QuestionTagService questionTagService) {
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.choiceConfigRepository = choiceConfigRepository;
        this.bidConfigRepository = bidConfigRepository;
        this.metadataRepository = metadataRepository;
        this.questionTagService = questionTagService;
    }

    @Override
    public List<QuestionDTO> convertEntitiesToDTOs(List<QuestionEntity> entities) {
        return convertToDTO(entities);  // 复用已有的私有方法
    }

    @Transactional
    @Override
    public void batchImport(List<QuestionDTO> questionDTOs) {
        for (QuestionDTO dto : questionDTOs) {
            // 1. 保存基础 Entity
            QuestionEntity entity = QuestionEntity.builder()
                    .type(dto.getType())
                    .text(dto.getText())
                    .calculateRule(dto.getCalculateRule())  // 🔥 添加计分规则
                    .strategyId(dto.getStrategyId())
                    .minPlayers(dto.getMinPlayers())
                    .maxPlayers(dto.getMaxPlayers())
                    .defaultChoice(dto.getDefaultChoice())
                    .hasMetadata(false)
                    .build();

            QuestionEntity savedEntity = questionRepository.save(entity);
            Long questionId = savedEntity.getId();

            // 2. 保存选择题配置
            if (dto.getType() == QuestionType.CHOICE && dto.getOptions() != null && !dto.getOptions().isEmpty()) {
                try {
                    ChoiceQuestionConfig config = ChoiceQuestionConfig.builder()
                            .question(savedEntity)
                            .optionsJson(objectMapper.writeValueAsString(dto.getOptions()))
                            .build();
                    choiceConfigRepository.save(config);
                } catch (JsonProcessingException e) {
                    log.error("序列化选项失败: {}", e.getMessage());
                    throw new RuntimeException("保存选择题配置失败", e);
                }
            }

            if (dto.getType() == QuestionType.BID && dto.getMin() != null && dto.getMax() != null) {
                BidQuestionConfig config = BidQuestionConfig.builder()
                        .question(savedEntity)
                        .minValue(dto.getMin())
                        .maxValue(dto.getMax())
                        .step(dto.getStep())
                        .build();
                bidConfigRepository.save(config);
            }

            // 4. 保存元数据
            if (hasMetadata(dto)) {
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

        log.info("成功导入 {} 道题目（包含配置）", questionDTOs.size());
    }

    /**
     * 判断是否有元数据配置
     */
    private boolean hasMetadata(QuestionDTO dto) {
        return dto.getSequenceGroupId() != null
                || dto.getIsRepeatable() != null
                || dto.getRepeatTimes() != null;
    }

    /**
     * 导出所有题目
     */
    @Override
    public List<QuestionDTO> exportAll() {
        List<QuestionEntity> all = questionRepository.findAll();
        return convertToDTO(all);
    }

    @Override
    public List<QuestionDTO> getAllQuestionDTO() {
        List<QuestionEntity> entities = questionRepository.findAll();
        return convertToDTO(entities);
    }

    @Override
    public List<QuestionDTO> getRandomQuestionDTO(int count) {
        List<QuestionEntity> allQuestions = questionRepository.findAll();

        if (allQuestions.isEmpty()) {
            throw new BusinessException("没有可用题目");
        }

        Collections.shuffle(allQuestions);
        List<QuestionEntity> selected = allQuestions.stream()
                .limit(count)
                .collect(Collectors.toList());

        return convertToDTO(selected);
    }

    @Override
    public List<QuestionDTO> getQuestionsByPlayerCountDTO(int playerCount, int questionCount) {
        List<QuestionEntity> suitable = questionRepository.findAll().stream()
                .filter(q -> q.getMinPlayers() <= playerCount && q.getMaxPlayers() >= playerCount)
                .collect(Collectors.toList());

        if (suitable.isEmpty()) {
            suitable = questionRepository.findAll();
        }

        Collections.shuffle(suitable);
        List<QuestionEntity> selected = suitable.stream()
                .limit(questionCount)
                .collect(Collectors.toList());

        return convertToDTO(selected);
    }

// ========== 私有辅助方法 ==========

    /**
     * 批量转换 Entity → DTO（包含配置信息）
     */
    private List<QuestionDTO> convertToDTO(List<QuestionEntity> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> questionIds = entities.stream()
                .map(QuestionEntity::getId)
                .collect(Collectors.toList());

        Map<Long, ChoiceQuestionConfig> choiceConfigMap = choiceConfigRepository
                .findByQuestionIds(questionIds)  // 改这里
                .stream()
                .collect(Collectors.toMap(c -> c.getQuestion().getId(), c -> c));

        Map<Long, BidQuestionConfig> bidConfigMap = bidConfigRepository
                .findByQuestionIds(questionIds)  // 改这里
                .stream()
                .collect(Collectors.toMap(b -> b.getQuestion().getId(), b -> b));

        Map<Long, QuestionMetadata> metadataMap = metadataRepository
                .findByQuestionIdIn(questionIds)
                .stream()
                .collect(Collectors.toMap(QuestionMetadata::getQuestionId, m -> m));

        // 🔥 批量查询标签
        Map<Long, List<TagDTO>> tagsMap = questionTagService.getTagsForQuestions(questionIds);

        return entities.stream()
                .map(entity -> convertSingleToDTO(entity, choiceConfigMap, bidConfigMap, metadataMap, tagsMap))
                .collect(Collectors.toList());
    }

    /**
     * 单个 Entity → DTO
     */
    private QuestionDTO convertSingleToDTO(
            QuestionEntity entity,
            Map<Long, ChoiceQuestionConfig> choiceConfigMap,
            Map<Long, BidQuestionConfig> bidConfigMap,
            Map<Long, QuestionMetadata> metadataMap,
            Map<Long, List<TagDTO>> tagsMap) {

        QuestionDTO dto = new QuestionDTO();
        dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setText(entity.getText());
        dto.setCalculateRule(entity.getCalculateRule());  // 🔥 添加计分规则
        dto.setStrategyId(entity.getStrategyId());
        dto.setDefaultChoice(entity.getDefaultChoice());
        dto.setMinPlayers(entity.getMinPlayers());
        dto.setMaxPlayers(entity.getMaxPlayers());

        if (entity.getType() == QuestionType.CHOICE) {
            ChoiceQuestionConfig config = choiceConfigMap.get(entity.getId());
            if (config != null) {
                dto.setOptions(deserializeOptions(config.getOptionsJson()));
            }
        }

        if (entity.getType() == QuestionType.BID) {
            BidQuestionConfig config = bidConfigMap.get(entity.getId());
            if (config != null) {
                dto.setMin(config.getMinValue());
                dto.setMax(config.getMaxValue());
                dto.setStep(config.getStep());
            }
        }

        QuestionMetadata metadata = metadataMap.get(entity.getId());
        if (metadata != null) {
            dto.setSequenceGroupId(metadata.getSequenceGroupId());
            dto.setSequenceOrder(metadata.getSequenceOrder());
            dto.setTotalSequenceCount(metadata.getTotalSequenceCount());
            dto.setIsRepeatable(metadata.getIsRepeatable());
            dto.setRepeatTimes(metadata.getRepeatTimes());
            dto.setRepeatInterval(metadata.getRepeatInterval());
            dto.setRepeatGroupId(metadata.getRepeatGroupId());
        }

        // 🔥 设置标签
        dto.setTags(tagsMap.getOrDefault(entity.getId(), Collections.emptyList()));

        return dto;
    }

    private List<QuestionOption> deserializeOptions(String optionsJson) {
        if (optionsJson != null && !optionsJson.isEmpty()) {
            try {
                return objectMapper.readValue(
                        optionsJson,
                        new TypeReference<List<QuestionOption>>() {}
                );
            } catch (IOException e) {
                log.error("反序列化选项失败: {}", e.getMessage());
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    /**
     * 分页查询
     */
    @Override
    public Page<QuestionEntity> findAll(Pageable pageable) {
        return questionRepository.findAll(pageable);
    }

    /**
     * 删除题目
     */
    @Override
    public void deleteById(Long id) {
        questionRepository.deleteById(id);
    }

    /**
     * 清空所有题目
     */
    @Transactional
    @Override
    public void deleteAll() {
        questionRepository.deleteAll();
    }

    @Override
    @Transactional
    public void updateQuestion(Long id, QuestionDTO dto) {
        // 🔥 调试日志
        log.info("📥 收到更新请求: id={}, calculateRule={}", id, dto.getCalculateRule());

        // 1. 查询现有题目
        QuestionEntity existingEntity = questionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("题目不存在: " + id));

        log.info("📥 更新前的 calculateRule: {}", existingEntity.getCalculateRule());

        // 2. 更新基础字段（只更新非空字段）
        if (dto.getType() != null) {
            existingEntity.setType(dto.getType());
        }
        if (dto.getText() != null) {
            existingEntity.setText(dto.getText());
        }
        // 🔥 支持更新或清空 calculateRule（传空字符串或null都可以清空）
        if (dto.getCalculateRule() != null) {
            existingEntity.setCalculateRule(dto.getCalculateRule().isEmpty() ? null : dto.getCalculateRule());
            log.info("📥 更新后的 calculateRule: {}", existingEntity.getCalculateRule());
        } else {
            log.info("📥 dto.getCalculateRule() 为 null，跳过更新");
        }
        if (dto.getStrategyId() != null) {
            existingEntity.setStrategyId(dto.getStrategyId());
        }
        if (dto.getMinPlayers() != null) {
            existingEntity.setMinPlayers(dto.getMinPlayers());
        }
        if (dto.getMaxPlayers() != null) {
            existingEntity.setMaxPlayers(dto.getMaxPlayers());
        }
        if (dto.getDefaultChoice() != null) {
            existingEntity.setDefaultChoice(dto.getDefaultChoice());
        }

        questionRepository.save(existingEntity);

        if (existingEntity.getType() == QuestionType.CHOICE && dto.getOptions() != null) {
            updateChoiceConfig(id, dto);
        }

        if (existingEntity.getType() == QuestionType.BID &&
                (dto.getMin() != null || dto.getMax() != null || dto.getStep() != null)) {
            updateBidConfig(id, dto);
        }

        // 5. 更新元数据
        if (hasAnyMetadata(dto)) {
            updateMetadata(id, dto);
        }

        log.info("题目更新成功: id={}", id);
    }

    /**
     * 更新选择题配置
     */
    private void updateChoiceConfig(Long questionId, QuestionDTO dto) {
        try {
            QuestionEntity question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new BusinessException("题目不存在"));

            ChoiceQuestionConfig config = choiceConfigRepository
                    .findByQuestion_Id(questionId)  // 改这里
                    .orElse(ChoiceQuestionConfig.builder()
                            .question(question)
                            .build());

            if (dto.getOptions() != null) {
                config.setOptionsJson(objectMapper.writeValueAsString(dto.getOptions()));
            }

            choiceConfigRepository.save(config);

            QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
            questionRepository.save(entity);

        } catch (JsonProcessingException e) {
            log.error("更新选择题配置失败", e);
            throw new RuntimeException("更新选择题配置失败", e);
        }
    }

    /**
     * 更新竞价题配置
     */
    private void updateBidConfig(Long questionId, QuestionDTO dto) {
        QuestionEntity question = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("题目不存在"));

        BidQuestionConfig config = bidConfigRepository
                .findByQuestion_Id(questionId)  // 改这里
                .orElse(BidQuestionConfig.builder()
                        .question(question)
                        .build());

        if (dto.getMin() != null) {
            config.setMinValue(dto.getMin());
        }
        if (dto.getMax() != null) {
            config.setMaxValue(dto.getMax());
        }
        if (dto.getStep() != null) {
            config.setStep(dto.getStep());
        }

        bidConfigRepository.save(config);

        QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
        questionRepository.save(entity);
    }

    /**
     * 更新元数据
     */
    private void updateMetadata(Long questionId, QuestionDTO dto) {
        QuestionMetadata metadata = metadataRepository
                .findByQuestionId(questionId)
                .orElse(QuestionMetadata.builder()
                        .questionId(questionId)
                        .build());

        // 只更新非空字段
        if (dto.getSequenceGroupId() != null) {
            metadata.setSequenceGroupId(dto.getSequenceGroupId());
        }
        if (dto.getSequenceOrder() != null) {
            metadata.setSequenceOrder(dto.getSequenceOrder());
        }
        if (dto.getTotalSequenceCount() != null) {
            metadata.setTotalSequenceCount(dto.getTotalSequenceCount());
        }
        if (dto.getIsRepeatable() != null) {
            metadata.setIsRepeatable(dto.getIsRepeatable());
        }
        if (dto.getRepeatTimes() != null) {
            metadata.setRepeatTimes(dto.getRepeatTimes());
        }
        if (dto.getRepeatInterval() != null) {
            metadata.setRepeatInterval(dto.getRepeatInterval());
        }
        if (dto.getRepeatGroupId() != null) {
            metadata.setRepeatGroupId(dto.getRepeatGroupId());
        }

        metadataRepository.save(metadata);

        // 更新标记
        QuestionEntity entity = questionRepository.findById(questionId).orElseThrow();
        entity.setHasMetadata(true);
        questionRepository.save(entity);
    }

    /**
     * 判断 DTO 是否包含任何元数据字段
     */
    private boolean hasAnyMetadata(QuestionDTO dto) {
        return dto.getSequenceGroupId() != null
                || dto.getSequenceOrder() != null
                || dto.getTotalSequenceCount() != null
                || dto.getIsRepeatable() != null
                || dto.getRepeatTimes() != null
                || dto.getRepeatInterval() != null
                || dto.getRepeatGroupId() != null;
    }
}

