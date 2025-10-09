package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bid_question_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BidQuestionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, unique = true)
    private Long questionId; // 外键：对应 QuestionEntity.id

    @Column(nullable = false)
    private Integer minValue; // 最小竞价值

    @Column(nullable = false)
    private Integer maxValue; // 最大竞价值

    @Column
    private Integer step; // 步长（可选）
}
