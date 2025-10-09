package org.example.repository;

import org.example.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {
    // 根据参与人数范围查找合适的题目
    List<QuestionEntity> findByMinPlayersLessThanEqualAndMaxPlayersGreaterThanEqual(int playerCount1, int playerCount2);

    // 根据题目类型查找
    List<QuestionEntity> findByType(String type);
}
