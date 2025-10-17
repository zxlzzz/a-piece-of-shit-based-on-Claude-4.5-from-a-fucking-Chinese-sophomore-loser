package org.example.repository;

import org.example.entity.GameEntity;
import org.example.entity.PlayerEntity;
import org.example.entity.QuestionEntity;
import org.example.entity.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    // 查找某道题在某个游戏中的所有提交
    List<SubmissionEntity> findByGameAndQuestion(GameEntity game, QuestionEntity question);

    // 查找某个玩家在某个游戏的所有提交
    List<SubmissionEntity> findByGameAndPlayer(GameEntity game, PlayerEntity player);
}