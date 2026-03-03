package org.example.repository;

import org.example.entity.ChoiceRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChoiceRecordRepository extends JpaRepository<ChoiceRecordEntity, Long> {

    /**
     * 查询指定题目的所有记录
     */
    List<ChoiceRecordEntity> findByQuestionId(Long questionId);

    /**
     * 查询指定时间后的记录
     */
    List<ChoiceRecordEntity> findByCreatedAtAfter(LocalDateTime after);

    /**
     * 统计指定题目的记录数（按人数分组）
     */
    @Query("SELECT cr.playerCount, cr.choice, COUNT(cr) " +
           "FROM ChoiceRecordEntity cr " +
           "WHERE cr.question.id = :questionId " +
           "GROUP BY cr.playerCount, cr.choice")
    List<Object[]> countByQuestionIdGroupByPlayerCountAndChoice(@Param("questionId") Long questionId);

    /**
     * 删除指定时间之前的记录（定期清理）
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
}
