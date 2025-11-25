package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "submissions",
        indexes = {
                @Index(name = "idx_game_question", columnList = "game_id,question_id"),
                @Index(name = "idx_player", columnList = "player_id")
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SubmissionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ 多对一：提交 -> 题目
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;  // ❌ 不加cascade，题目是公共资源

    // ✅ 多对一：提交 -> 玩家
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerEntity player;  // ❌ 不加cascade

    // ✅ 多对一：提交 -> 游戏
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;  // ❌ 不加cascade

    @Column(nullable = false)
    private String choice;

    @Column  // 这道题提交时得到的分数
    private Integer scoreGained;

    @CreationTimestamp
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 更新时间
}
