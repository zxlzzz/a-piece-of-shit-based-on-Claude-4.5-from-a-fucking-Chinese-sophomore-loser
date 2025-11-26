package org.example.service.question;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
import org.example.entity.*;
import org.example.exception.BusinessException;
import org.example.repository.BidQuestionConfigRepository;
import org.example.repository.ChoiceQuestionConfigRepository;
import org.example.repository.QuestionRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QuesService {

    private final QuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final ChoiceQuestionConfigRepository choiceConfigRepository;
    private final BidQuestionConfigRepository bidConfigRepository;

    public QuesService(
            QuestionRepository questionRepository,
            ObjectMapper objectMapper,
            ChoiceQuestionConfigRepository choiceConfigRepository,
            BidQuestionConfigRepository bidConfigRepository) {
        this.questionRepository = questionRepository;
        this.objectMapper = objectMapper;
        this.choiceConfigRepository = choiceConfigRepository;
        this.bidConfigRepository = bidConfigRepository;
    }

    public List<QuestionDTO> convertEntitiesToDTOs(List<QuestionEntity> entities) {
        return convertToDTO(entities);
    }

    @Transactional
    public void batchImport(List<QuestionDTO> questionDTOs) {
        for (QuestionDTO dto : questionDTOs) {
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

            // 保存选择题配置
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

            // 保存竞价题配置
            if (dto.getType() == QuestionType.BID && dto.getMin() != null && dto.getMax() != null) {
                BidQuestionConfig config = BidQuestionConfig.builder()
                        .question(savedEntity)
                        .minValue(dto.getMin())
                        .maxValue(dto.getMax())
                        .step(dto.getStep())
                        .build();
                bidConfigRepository.save(config);
            }
        }
    }

    public List<QuestionDTO> getAllQuestionDTO() {
        List<QuestionEntity> entities = questionRepository.findAll();
        return convertToDTO(entities);
    }

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

    private List<QuestionDTO> convertToDTO(List<QuestionEntity> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> questionIds = entities.stream()
                .map(QuestionEntity::getId)
                .collect(Collectors.toList());

        Map<Long, ChoiceQuestionConfig> choiceConfigMap = choiceConfigRepository
                .findByQuestionIds(questionIds)
                .stream()
                .collect(Collectors.toMap(c -> c.getQuestion().getId(), c -> c));

        Map<Long, BidQuestionConfig> bidConfigMap = bidConfigRepository
                .findByQuestionIds(questionIds)
                .stream()
                .collect(Collectors.toMap(b -> b.getQuestion().getId(), b -> b));

        return entities.stream()
                .map(entity -> convertSingleToDTO(entity, choiceConfigMap, bidConfigMap))
                .collect(Collectors.toList());
    }

    private QuestionDTO convertSingleToDTO(
            QuestionEntity entity,
            Map<Long, ChoiceQuestionConfig> choiceConfigMap,
            Map<Long, BidQuestionConfig> bidConfigMap) {

        QuestionDTO dto = new QuestionDTO();
        dto.setId(entity.getId());
        dto.setType(entity.getType());
        dto.setText(entity.getText());
        dto.setCalculateRule(entity.getCalculateRule());
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

    public void deleteById(Long id) {
        questionRepository.deleteById(id);
    }
}
