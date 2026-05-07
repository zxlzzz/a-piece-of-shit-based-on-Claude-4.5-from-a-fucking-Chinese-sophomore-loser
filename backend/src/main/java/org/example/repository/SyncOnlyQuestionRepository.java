package org.example.repository;

import org.example.entity.SyncOnlyQuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

@Repository
public interface SyncOnlyQuestionRepository extends JpaRepository<SyncOnlyQuestionEntity, Long> {

    /**
     * 返回所有需要同步模式的题目ID集合
     */
    default Set<Long> findAllQuestionIds() {
        return findAll().stream()
                .map(SyncOnlyQuestionEntity::getQuestionId)
                .collect(Collectors.toSet());
    }
}
