package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
import org.example.entity.QuestionEntity;
import org.example.service.QuesService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/questions")
@RequiredArgsConstructor
@Slf4j
public class QuestionAdminController {

    private final QuesService questionService;

    /**
     * 批量导入题目（从JSON）
     * POST /api/admin/questions/import
     */
    @PostMapping("/import")
    public ResponseEntity<?> importQuestions(@RequestBody List<QuestionDTO> questions) {  // ← 改参数类型
        try {
            questionService.batchImport(questions);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "成功导入 " + questions.size() + " 道题目"
            ));
        } catch (Exception e) {
            log.error("导入题目失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "导入失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 导出所有题目为JSON
     * GET /api/admin/questions/export
     */
    @GetMapping("/export")
    public ResponseEntity<List<QuestionDTO>> exportQuestions() {
        List<QuestionDTO> questions = questionService.exportAll();
        return ResponseEntity.ok(questions);
    }

    /**
     * 获取所有题目（分页，返回完整DTO）
     * GET /api/admin/questions?page=0&size=20
     */
    @GetMapping
    public ResponseEntity<?> getAllQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 🔥 改为返回 DTO，包含完整配置
        Page<QuestionEntity> entityPage = questionService.findAll(PageRequest.of(page, size));

        // 转换为 DTO
        List<QuestionDTO> dtoList = questionService.convertEntitiesToDTOs(entityPage.getContent());

        // 构造分页响应
        Map<String, Object> response = Map.of(
                "content", dtoList,
                "totalElements", entityPage.getTotalElements(),
                "totalPages", entityPage.getTotalPages(),
                "currentPage", entityPage.getNumber(),
                "size", entityPage.getSize()
        );

        return ResponseEntity.ok(response);
    }
    /**
     * 创建题目（完整版，支持配置）
     * POST /api/admin/questions
     */
    @PostMapping
    public ResponseEntity<?> createQuestion(@RequestBody QuestionDTO questionDTO) {  // ← 改参数类型
        try {
            // 复用 batchImport 逻辑
            questionService.batchImport(List.of(questionDTO));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "题目创建成功"
            ));
        } catch (Exception e) {
            log.error("创建题目失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 删除题目
     * DELETE /api/admin/questions/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long id) {
        try {
            questionService.deleteById(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            log.error("删除题目失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 清空所有题目
     * DELETE /api/admin/questions/all
     */
    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAllQuestions() {
        try {
            questionService.deleteAll();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "已清空所有题目"
            ));
        } catch (Exception e) {
            log.error("清空题目失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 更新题目（智能更新，只修改提供的字段）
     * PUT /api/admin/questions/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateQuestion(
            @PathVariable Long id,
            @RequestBody QuestionDTO questionDTO) {
        try {
            questionService.updateQuestion(id, questionDTO);  // ← 调用新方法
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "题目更新成功"
            ));
        } catch (Exception e) {
            log.error("更新题目失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
