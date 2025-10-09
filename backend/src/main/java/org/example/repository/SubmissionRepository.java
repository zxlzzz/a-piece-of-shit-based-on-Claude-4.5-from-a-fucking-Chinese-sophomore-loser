package org.example.repository;

import org.example.entity.GameEntity;
import org.example.entity.PlayerEntity;
import org.example.entity.QuestionEntity;
import org.example.entity.SubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<SubmissionEntity, Long> {

    /**
     * 查询玩家在某局游戏某道题的提交记录
     */
    Optional<SubmissionEntity> findByPlayerAndQuestionAndGame(
            PlayerEntity player,
            QuestionEntity question,
            GameEntity game
    );

    /**
     * 查询某局游戏某道题的所有提交
     */
    List<SubmissionEntity> findByGameAndQuestion(GameEntity game, QuestionEntity question);

    /**
     * 查询玩家在某局游戏的所有提交
     */
    List<SubmissionEntity> findByPlayerAndGame(PlayerEntity player, GameEntity game);
}