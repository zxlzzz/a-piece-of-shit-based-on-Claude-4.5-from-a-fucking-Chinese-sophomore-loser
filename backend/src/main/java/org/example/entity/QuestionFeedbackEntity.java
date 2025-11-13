package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 题目反馈实体
 * 用于收集玩家对题目的评价和建议
 *
 * TODO: 需要添加防恶意提交机制
 *       - 可考虑IP限流
 *       - 可考虑内容校验（长度、敏感词）
 *       - 可考虑验证码
 */
@Entity
@Table(name = "question_feedbacks")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 题目ID
     */
    @Column(name = "question_id", nullable = false)
    private Long questionId;

    /**
     * 星级评分（1-5），可为空
     */
    @Column(name = "rating")
    private Integer rating;

    /**
     * 文字评价，可为空
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
