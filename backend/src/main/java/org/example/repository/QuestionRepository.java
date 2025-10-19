package org.example.repository;

import org.springframework.data.repository.query.Param;
import org.example.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {
    // 根据参与人数范围查找合适的题目
    List<QuestionEntity> findByMinPlayersLessThanEqualAndMaxPlayersGreaterThanEqual(int playerCount1, int playerCount2);

    // 根据题目类型查找
    List<QuestionEntity> findByType(String type);
    /**
     * 根据ID查询题目，并关联加载配置
     */
    @Query("SELECT q FROM QuestionEntity q " +
            "LEFT JOIN FETCH q.choiceConfig " +
            "LEFT JOIN FETCH q.bidConfig " +
            "WHERE q.id = :id")
    Optional<QuestionEntity> findByIdWithConfigs(@Param("id") Long id);

    /**
     * 查询所有题目，并关联加载配置
     */
    @Query("SELECT DISTINCT q FROM QuestionEntity q " +
            "LEFT JOIN FETCH q.choiceConfig " +
            "LEFT JOIN FETCH q.bidConfig")
    List<QuestionEntity> findAllWithConfigs();

    /**
     * 根据 strategyId 查询
     */
    @Query("SELECT q FROM QuestionEntity q " +
            "LEFT JOIN FETCH q.choiceConfig " +
            "LEFT JOIN FETCH q.bidConfig " +
            "WHERE q.strategyId = :strategyId")
    Optional<QuestionEntity> findByStrategyIdWithConfigs(@Param("strategyId") String strategyId);
}
