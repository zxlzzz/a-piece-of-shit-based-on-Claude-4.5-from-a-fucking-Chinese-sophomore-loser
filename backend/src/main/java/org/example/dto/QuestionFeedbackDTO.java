package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目反馈请求DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class QuestionFeedbackDTO {

    /**
     * 星级评分（1-5），可为空
     */
    private Integer rating;

    /**
     * 文字评价，可为空
     */
    private String comment;
}
