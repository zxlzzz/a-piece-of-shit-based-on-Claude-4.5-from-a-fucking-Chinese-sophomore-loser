package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.TagDTO;
import org.example.service.tag.QuestionTagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 标签相关API
 */
@Slf4j
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final QuestionTagService questionTagService;

    /**
     * 获取所有标签（分类）
     * GET /api/tags
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
}
