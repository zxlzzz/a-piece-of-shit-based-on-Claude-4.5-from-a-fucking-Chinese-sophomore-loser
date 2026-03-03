package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 练习会话DTO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PracticeSessionDTO {
    /**
     * 会话ID（临时，关闭后失效）
     */
    private String sessionId;

    /**
     * 题目信息
     */
    private QuestionDTO question;

    /**
     * Bot选择（提交前不返回）
     */
    private String botChoice;

    /**
     * 当前玩家人数（用于统计）
     */
    private Integer playerCount;
}
