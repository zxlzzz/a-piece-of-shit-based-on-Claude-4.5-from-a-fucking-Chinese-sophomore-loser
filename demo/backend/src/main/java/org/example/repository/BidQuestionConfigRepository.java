package org.example.repository;

import org.example.entity.BidQuestionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BidQuestionConfigRepository extends JpaRepository<BidQuestionConfig, Long> {

    /**
     * 根据题目ID查找配置
     * 使用 question.id 导航查询
     */
    Optional<BidQuestionConfig> findByQuestion_Id(Long questionId);

    /**
     * 批量查询(避免 N+1 问题)
     */
    @Query("SELECT b FROM BidQuestionConfig b WHERE b.question.id IN :questionIds")
    List<BidQuestionConfig> findByQuestionIds(@Param("questionIds") List<Long> questionIds);

    /**
     * 检查题目是否已有配置
     */
    boolean existsByQuestion_Id(Long questionId);
}