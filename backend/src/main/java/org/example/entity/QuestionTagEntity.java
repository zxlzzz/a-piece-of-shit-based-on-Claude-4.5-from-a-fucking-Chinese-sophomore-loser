package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目标签实体
 */
@Entity
@Table(name = "question_tag")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionTagEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 标签名称
     */
    @Column(nullable = false, length = 50)
    private String name;

    /**
     * 标签分类（mechanism/strategy）
     */
    @Column(nullable = false, length = 20)
    private String category;

    /**
     * 显示颜色
     */
    @Column(length = 20)
    private String color;
}
