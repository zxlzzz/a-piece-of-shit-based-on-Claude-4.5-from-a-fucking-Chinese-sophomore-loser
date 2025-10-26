package org.example.service.question;

import org.example.dto.QuestionDetailDTO;
import org.example.pojo.GameContext;

import java.util.Map;

public interface QuestionScoringStrategy {
    /**
     * 计算题目结果，返回前端所需的 DTO
     */
    QuestionDetailDTO calculateResult(GameContext context);

    /**
     * 获取题目标识符（用于 Factory 匹配）
     * @return 题目ID，如 "Q001", "Q007"
     */
    String getQuestionIdentifier();

    Map<String, Integer> test(Map<String, String> submissions);
}