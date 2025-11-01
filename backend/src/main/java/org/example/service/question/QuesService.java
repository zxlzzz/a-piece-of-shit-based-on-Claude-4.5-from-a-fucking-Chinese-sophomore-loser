package org.example.service.question;

import jakarta.transaction.Transactional;
import org.example.dto.QuestionDTO;
import org.example.entity.QuestionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface QuesService {



    /**
     * 获取所有题目（DTO格式，包含完整配置）
     */
    List<QuestionDTO> getAllQuestionDTO();

    /**
     * 获取随机题目（DTO格式）
     */
    List<QuestionDTO> getRandomQuestionDTO(int count);

    /**
     * 根据玩家数获取合适的题目（DTO格式）
     */
    List<QuestionDTO> getQuestionsByPlayerCountDTO(int playerCount, int questionCount);



    @Transactional
    void batchImport(List<QuestionDTO> questionDTOs);

    List<QuestionDTO> exportAll();

    Page<QuestionEntity> findAll(Pageable pageable);

    void updateQuestion(Long id, QuestionDTO dto);

    void deleteById(Long id);

    @Transactional
    void deleteAll();

    List<QuestionDTO> convertEntitiesToDTOs(List<QuestionEntity> entities);
}
