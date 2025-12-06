package org.example.repository;

import org.example.entity.ChoiceRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChoiceRecordRepository extends JpaRepository<ChoiceRecordEntity, Long> {

    /**
     * 统计指定题目的记录数（按人数分组）
     */
    @Query("SELECT cr.playerCount, cr.choice, COUNT(cr) " +
           "FROM ChoiceRecordEntity cr " +
           "WHERE cr.question.id = :questionId " +
           "GROUP BY cr.playerCount, cr.choice")
    List<Object[]> countByQuestionIdGroupByPlayerCountAndChoice(@Param("questionId") Long questionId);

}
