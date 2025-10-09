package org.example.repository;

import org.example.entity.ChoiceQuestionConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChoiceQuestionConfigRepository extends JpaRepository<ChoiceQuestionConfig, Long> {
    // 根据题目ID查找配置
    Optional<ChoiceQuestionConfig> findByQuestionId(Long questionId);

    // 批量查询（避免N+1问题）
    List<ChoiceQuestionConfig> findByQuestionIdIn(List<Long> questionIds);

    // 删除配置
    void deleteByQuestionId(Long questionId);
}
