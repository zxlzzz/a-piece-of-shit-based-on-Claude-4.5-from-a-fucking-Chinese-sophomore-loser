package org.example.repository;

import org.example.entity.UserFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFeedbackRepository extends JpaRepository<UserFeedbackEntity, Long> {

    /**
     * 根据类型查询反馈
     */
    List<UserFeedbackEntity> findByType(String type);

    /**
     * 统计某类型的反馈数量
     */
    long countByType(String type);
}
