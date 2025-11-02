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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String calculateRule;

    @Column(nullable = false)
    private String strategyId;

    @Column
    private Integer minPlayers;

    @Column
    private Integer maxPlayers;

    @Column
    private String defaultChoice;

    // ========== 快速判断标记 ==========
    @Column
    private Boolean hasMetadata = false;

    // ========== ✅ 新增：关联配置 ==========
    @OneToOne(mappedBy = "question", fetch = FetchType.LAZY)
    private ChoiceQuestionConfig choiceConfig;

    @OneToOne(mappedBy = "question", fetch = FetchType.LAZY)
    private BidQuestionConfig bidConfig;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}