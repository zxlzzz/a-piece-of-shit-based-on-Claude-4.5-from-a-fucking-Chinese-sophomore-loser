package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 需要同步答题的题目记录
 * 被标记在此表中的题目无法在异步模式（ASYNC）的房间中出现。
 * 目前为空——所有现有题目均支持异步模式；未来若新增需要实时互动的题目，可在此表插入记录。
 */
@Entity
@Table(name = "sync_only_questions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SyncOnlyQuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的题目ID（唯一约束，一道题只记录一次）
     */
    @Column(nullable = false, unique = true)
    private Long questionId;

    /**
     * 说明该题为何需要同步模式（可选，方便维护）
     */
    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
