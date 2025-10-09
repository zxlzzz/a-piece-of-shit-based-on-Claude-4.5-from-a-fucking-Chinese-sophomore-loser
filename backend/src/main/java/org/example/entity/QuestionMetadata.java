package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    // ========== 依赖关系 ==========
    @Column(columnDefinition = "TEXT")
    private String prerequisiteQuestionIds; // 前置题目ID（逗号分隔）
}
