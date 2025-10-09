package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "choice_question_config")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChoiceQuestionConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false, unique = true)
    private Long questionId; // 外键：对应 QuestionEntity.id

    @Column(name = "options_json", columnDefinition = "TEXT", nullable = false)
    private String optionsJson; // JSON 格式存储选项

    /*
     * optionsJson 格式：
     * [
     *   {"key": "A", "text": "选项A"},
     *   {"key": "B", "text": "选项B"},
     *   {"key": "C", "text": "选项C"},
     *   {"key": "D", "text": "选项D"}
     * ]
     */
}
