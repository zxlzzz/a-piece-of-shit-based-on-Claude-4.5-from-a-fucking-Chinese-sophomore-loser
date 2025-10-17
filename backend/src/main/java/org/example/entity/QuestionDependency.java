package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Table(name = "question_dependencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDependency {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuestionEntity question;  // 当前题

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prerequisite_question_id", nullable = false)
    private QuestionEntity prerequisiteQuestion;  // 前置题
}