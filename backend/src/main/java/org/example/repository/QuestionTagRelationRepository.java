package org.example.repository;

import org.example.entity.QuestionTagRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionTagRelationRepository extends JpaRepository<QuestionTagRelationEntity, QuestionTagRelationEntity.QuestionTagRelationId> {

    /**
     * 根据题目ID查询标签ID列表
     */
    @Query("SELECT r.tagId FROM QuestionTagRelationEntity r WHERE r.questionId = :questionId")
    List<Long> findTagIdsByQuestionId(Long questionId);

    /**
     * 根据题目ID列表批量查询
     */
    List<QuestionTagRelationEntity> findByQuestionIdIn(List<Long> questionIds);
}
