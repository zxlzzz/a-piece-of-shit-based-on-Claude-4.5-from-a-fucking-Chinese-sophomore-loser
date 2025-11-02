package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.entity.QuestionOption;
import org.example.entity.QuestionType;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDTO {
    private Long id;
    private QuestionType type;           // "choice" 或 "bid"
    private String text;           // 题目描述
    private String calculateRule;  // 🔥 计分规则（可选，用于分离情景描述和规则）
    private String strategyId;     // 计分策略ID

    // choice题专用
    private List<QuestionOption> options; // 选项列表

    // bid题专用
    private Integer min;           // 最小值
    private Integer max;           // 最大值
    private Integer step;          // 步长

    // 序列配置
    private String sequenceGroupId;
    private Integer sequenceOrder;
    private Integer totalSequenceCount;
    private String prerequisiteQuestionIds;

    // 重复配置
    private Boolean isRepeatable;
    private Integer repeatTimes;
    private Integer repeatInterval;
    private String repeatGroupId;

    // 通用
    private String defaultChoice;  // 默认选择
    private Integer minPlayers;
    private Integer maxPlayers;

    // 标签
    private List<TagDTO> tags;     // 题目的标签列表

}
