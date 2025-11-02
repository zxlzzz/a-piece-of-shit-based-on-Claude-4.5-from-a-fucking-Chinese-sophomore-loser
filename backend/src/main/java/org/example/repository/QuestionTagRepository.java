package org.example.repository;

import org.example.entity.QuestionTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionTagRepository extends JpaRepository<QuestionTagEntity, Long> {

    /**
     * 根据分类查询标签
     */
    List<QuestionTagEntity> findByCategory(String category);
}
