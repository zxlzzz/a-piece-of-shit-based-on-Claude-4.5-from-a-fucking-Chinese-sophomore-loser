package org.example.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.TagDTO;
import org.example.service.tag.QuestionTagService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin标签管理API
 */
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
@Slf4j
public class AdminTagController {

    private final QuestionTagService questionTagService;

    /**
     * 获取所有标签（分类）
     */
    @GetMapping
    public ResponseEntity<Map<String, List<TagDTO>>> getAllTags() {
        try {
            Map<String, List<TagDTO>> tags = questionTagService.getTagsByCategory();
            return ResponseEntity.ok(tags);
        } catch (Exception e) {
            log.error("获取标签失败", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 创建新标签
     */
    @PostMapping
    public ResponseEntity<TagDTO> createTag(@RequestBody CreateTagRequest request) {
        try {
            TagDTO tag = questionTagService.createTag(
                    request.getName(),
                    request.getCategory(),
                    request.getColor()
            );
            log.info("✅ 创建标签成功: {}", tag.getName());
            return ResponseEntity.ok(tag);
        } catch (Exception e) {
            log.error("创建标签失败", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 删除标签（级联删除所有题目关联）
     */
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(@PathVariable Long tagId) {
        try {
            questionTagService.deleteTag(tagId);
            log.info("✅ 删除标签成功: id={}", tagId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("删除标签失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 为题目添加标签
     */
    @PostMapping("/questions/{questionId}/tags/{tagId}")
    public ResponseEntity<Void> addTagToQuestion(
            @PathVariable Long questionId,
            @PathVariable Long tagId) {
        try {
            questionTagService.addTagToQuestion(questionId, tagId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("添加标签失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 从题目移除标签
     */
    @DeleteMapping("/questions/{questionId}/tags/{tagId}")
    public ResponseEntity<Void> removeTagFromQuestion(
            @PathVariable Long questionId,
            @PathVariable Long tagId) {
        try {
            questionTagService.removeTagFromQuestion(questionId, tagId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("移除标签失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 导出标签配置JSON（用于手动替换question-tags.json）
     */
    @GetMapping("/export")
    public ResponseEntity<String> exportTags() {
        try {
            String json = questionTagService.exportTagsToJson();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=question-tags.json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (Exception e) {
            log.error("导出标签失败", e);
            return ResponseEntity.badRequest().body(null);
        }
    }

    // ==================== Request DTOs ====================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTagRequest {
        private String name;
        private String category;  // mechanism 或 strategy
        private String color;
    }
}
