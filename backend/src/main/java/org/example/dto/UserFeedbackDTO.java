package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户反馈DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserFeedbackDTO {

    /**
     * 反馈类型（QUESTION_SUGGESTION=题目建议, OTHER=其他）
     */
    private String type;

    /**
     * 反馈内容（必填）
     */
    private String content;

    /**
     * 昵称（可选）
     */
    private String nickname;

    /**
     * 联系方式（可选）
     */
    private String contact;
}
