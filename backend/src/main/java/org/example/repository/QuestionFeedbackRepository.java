package org.example.repository;

import org.example.entity.QuestionFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionFeedbackRepository extends JpaRepository<QuestionFeedbackEntity, Long> {

    /**
     * 根据题目ID查询所有反馈
     */
    List<QuestionFeedbackEntity> findByQuestionId(Long questionId);

    /**
     * 统计某题目的反馈数量
     */
    long countByQuestionId(Long questionId);
}
