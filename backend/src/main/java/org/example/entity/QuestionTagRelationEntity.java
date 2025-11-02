package org.example.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 题目-标签关联实体
 */
@Entity
@Table(name = "question_tag_relation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(QuestionTagRelationEntity.QuestionTagRelationId.class)
public class QuestionTagRelationEntity {

    @Id
    @Column(name = "question_id")
    private Long questionId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    /**
     * 复合主键类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionTagRelationId implements Serializable {
        private Long questionId;
        private Long tagId;
    }
}
