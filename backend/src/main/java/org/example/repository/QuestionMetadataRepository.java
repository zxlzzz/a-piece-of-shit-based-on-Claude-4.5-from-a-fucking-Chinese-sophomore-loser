package org.example.repository;

import org.example.entity.QuestionMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuestionMetadataRepository extends JpaRepository<QuestionMetadata, Long> {
    // 根据题目ID查找元数据
    Optional<QuestionMetadata> findByQuestionId(Long questionId);

    // 批量查询（重要！用于QuestionSelector避免N+1查询）
    List<QuestionMetadata> findByQuestionIdIn(List<Long> questionIds);
}
