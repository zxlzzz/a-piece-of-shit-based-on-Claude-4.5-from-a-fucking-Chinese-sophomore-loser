package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 题目统计实体 - 聚合统计数据
 * 存储各题目的选项分布（按人数区分）
 */
@Entity
@Table(name = "question_statistics", indexes = {
    @Index(name = "idx_question_id", columnList = "question_id", unique = true)
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionStatisticsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联题目（一对一）
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private QuestionEntity question;

    /**
     * 选项分布JSON（按人数区分）
     * 格式: {
     *   "2": {"A": 120, "B": 230, "C": 150},
     *   "3": {"A": 45, "B": 89, "C": 67},
     *   "4": {"A": 12, "B": 34, "C": 28}
     * }
     */
    @Column(name = "choice_distribution_json", columnDefinition = "TEXT")
    private String choiceDistributionJson;

    /**
     * 总被玩次数
     */
    @Column(name = "total_plays", nullable = false)
    @Builder.Default
    private Integer totalPlays = 0;

    /**
     * 最后被玩时间
     */
    @Column(name = "last_played_at")
    private LocalDateTime lastPlayedAt;

    /**
     * 更新时间
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
