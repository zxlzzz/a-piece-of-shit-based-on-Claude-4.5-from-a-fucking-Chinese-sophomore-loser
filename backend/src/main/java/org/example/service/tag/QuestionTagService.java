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

    // ==================== Admin管理方法 ====================

    /**
     * 创建新标签
     */
    TagDTO createTag(String name, String category, String color);

    /**
     * 删除标签（级联删除所有题目关联）
     */
    void deleteTag(Long tagId);

    /**
     * 为题目添加标签
     */
    void addTagToQuestion(Long questionId, Long tagId);

    /**
     * 从题目移除标签
     */
    void removeTagFromQuestion(Long questionId, Long tagId);

    /**
     * 导出标签配置为JSON（用于手动替换question-tags.json）
     */
    String exportTagsToJson();
}
