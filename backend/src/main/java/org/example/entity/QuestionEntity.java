package org.example.entity;

import jakarta.persistence.*;
import lombok.*;

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
    @Column(nullable = false)
    private String type; // "choice" 或 "bid"

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
}