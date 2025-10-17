package org.example.repository;

import org.example.entity.BidQuestionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BidQuestionConfigRepository extends JpaRepository<BidQuestionConfig, Long> {
    // 根据题目ID查找配置
    Optional<BidQuestionConfig> findByQuestionId(Long questionId);

    // 批量查询
    List<BidQuestionConfig> findByQuestionIdIn(List<Long> questionIds);
}

