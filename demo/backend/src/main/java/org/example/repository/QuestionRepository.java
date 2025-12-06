package org.example.repository;

import org.springframework.data.repository.query.Param;
import org.example.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {
    /**
     * 查询所有题目，并关联加载配置
     */
    @Query("SELECT DISTINCT q FROM QuestionEntity q " +
            "LEFT JOIN FETCH q.choiceConfig " +
            "LEFT JOIN FETCH q.bidConfig")
    List<QuestionEntity> findAllWithConfigs();

}
