package org.example.service.question;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
import org.example.entity.QuestionEntity;
import org.example.repository.QuestionRepository;
import org.example.utils.DTOConverter;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class QuestionSelectorService {
    private final QuestionRepository questionRepository;
    private final DTOConverter dtoConverter;

    public QuestionSelectorService(
            QuestionRepository questionRepository,
            DTOConverter dtoConverter) {
        this.questionRepository = questionRepository;
        this.dtoConverter = dtoConverter;
    }

    /**
     * 选择题目（简化版：随机选择）
     */
    public List<QuestionDTO> selectQuestions(int totalCount, int playerCount) {
        return selectQuestions(totalCount, playerCount, null);
    }

    /**
     * 选择题目（简化版：随机选择，忽略标签筛选）
     */
    public List<QuestionDTO> selectQuestions(int totalCount, int playerCount, List<Long> tagIds) {
        // 查询所有题目
        List<QuestionEntity> allQuestions = questionRepository.findAllWithConfigs();

        // 筛选适合人数的题目
        List<QuestionEntity> suitable = allQuestions.stream()
                .filter(q -> q.getMinPlayers() <= playerCount && q.getMaxPlayers() >= playerCount)
                .toList();

        if (suitable.isEmpty()) {
            throw new RuntimeException("No suitable questions found");
        }

        // 随机打乱并选择
        Collections.shuffle(suitable);
        List<QuestionEntity> selected = suitable.stream()
                .limit(totalCount)
                .collect(Collectors.toList());

        // 转换成 DTO
        List<QuestionDTO> selectedDTOs = selected.stream()
                .map(dtoConverter::toQuestionDTOWithConfig)
                .collect(Collectors.toList());

        log.info("选题完成: 共选择 {} 道题目", selectedDTOs.size());

        return selectedDTOs;
    }
}
