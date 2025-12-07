package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 选项记录实体 - 记录每次玩家的选择
 * 用于题目统计分析和Bot策略生成
 */
@Entity
@Table(name = "choice_records", indexes = {
    @Index(name = "idx_question_id", columnList = "question_id"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_player_count", columnList = "player_count")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChoiceRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联题目
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;

    /**
     * 关联玩家（可为null，表示Bot或匿名）
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private PlayerEntity player;

    /**
     * 玩家选择的选项
     */
    @Column(nullable = false, length = 10)
    private String choice;

    /**
     * 当局玩家人数（用于区分统计）
     */
    @Column(name = "player_count", nullable = false)
    private Integer playerCount;

    /**
     * 游戏类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 20)
    private GameType gameType;

    /**
     * 房间代码（可选，用于追踪）
     */
    @Column(name = "room_code", length = 10)
    private String roomCode;

    /**
     * 记录时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 游戏类型枚举
     */
    public enum GameType {
        MATCH,      // 正式对局
        PRACTICE    // 练习模式
    }
}
