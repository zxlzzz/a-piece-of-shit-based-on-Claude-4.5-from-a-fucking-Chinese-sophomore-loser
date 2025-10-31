package org.example.service.tag;

import org.example.dto.TagDTO;

import java.util.List;
import java.util.Map;

/**
 * 题目标签服务
 */
public interface QuestionTagService {

    /**
     * 初始化标签数据（从question-tags.json）
     */
    void initializeTagsFromJson();

    /**
     * 获取所有标签
     */
    List<TagDTO> getAllTags();

    /**
     * 根据分类获取标签
     */
    Map<String, List<TagDTO>> getTagsByCategory();

    /**
     * 根据题目ID获取标签列表
     */
    List<TagDTO> getTagsByQuestionId(Long questionId);

    /**
     * 批量获取题目的标签（返回Map: questionId -> tags）
     */
    Map<Long, List<TagDTO>> getTagsForQuestions(List<Long> questionIds);
}
