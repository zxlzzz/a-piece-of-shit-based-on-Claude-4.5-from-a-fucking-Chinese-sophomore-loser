package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "questions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== 基础信息 ==========
    // 然后在Entity中
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text; // 题目描述

    @Column(nullable = false)
    private String strategyId; // 计分策略ID，如 "Q001"

    // ========== 玩家数量限制 ==========
    @Column
    private Integer minPlayers;

    @Column
    private Integer maxPlayers;

    @Column
    private String defaultChoice; // 默认选择

    // ========== 快速判断标记 ==========
    @Column
    private Boolean hasChoiceConfig = false;

    @Column
    private Boolean hasBidConfig = false;

    @Column
    private Boolean hasMetadata = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;  // 创建时间

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;  // 更新时间
}