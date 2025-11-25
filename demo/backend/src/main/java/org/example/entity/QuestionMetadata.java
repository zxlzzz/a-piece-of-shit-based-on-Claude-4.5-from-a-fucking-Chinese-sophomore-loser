package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "question_metadata")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, unique = true)
    private Long questionId; // 外键：对应 QuestionEntity.id

    // ========== 序列相关 ==========
    @Column
    private String sequenceGroupId; // 序列组ID

    @Column
    private Integer sequenceOrder; // 序列中的顺序

    @Column
    private Integer totalSequenceCount; // 该序列共几题

    // ========== 重复相关 ==========
    @Column
    private Boolean isRepeatable; // 是否可重复

    @Column
    private Integer repeatTimes; // 重复次数

    @Column
    private Integer repeatInterval; // 重复间隔

    @Column
    private String repeatGroupId; // 重复组ID

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 更新时间
}
