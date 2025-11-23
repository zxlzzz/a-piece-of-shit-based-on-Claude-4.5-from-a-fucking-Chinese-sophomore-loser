package org.example.repository;

import org.example.entity.QuestionStatisticsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionStatisticsRepository extends JpaRepository<QuestionStatisticsEntity, Long> {

    /**
     * 通过题目ID查询统计
     */
    @Query("SELECT qs FROM QuestionStatisticsEntity qs WHERE qs.question.id = :questionId")
    Optional<QuestionStatisticsEntity> findByQuestionId(@Param("questionId") Long questionId);

    /**
     * 通过题目ID查询统计（带question信息）
     */
    @Query("SELECT qs FROM QuestionStatisticsEntity qs " +
           "JOIN FETCH qs.question " +
           "WHERE qs.question.id = :questionId")
    Optional<QuestionStatisticsEntity> findByQuestionIdWithQuestion(@Param("questionId") Long questionId);
}
