package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户反馈实体
 * 用于收集用户的题目建议、问题反馈等
 *
 * TODO: 后续可考虑添加防恶意提交机制
 */
@Entity
@Table(name = "user_feedbacks")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 反馈类型（QUESTION_SUGGESTION=题目建议, OTHER=其他）
     */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    /**
     * 反馈内容（必填）
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 昵称（可选）
     */
    @Column(name = "nickname", length = 100)
    private String nickname;

    /**
     * 联系方式（可选，不限制类型）
     */
    @Column(name = "contact", length = 200)
    private String contact;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
