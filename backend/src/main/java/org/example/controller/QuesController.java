package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.dto.QuestionDTO;
import org.example.exception.BusinessException;
import org.example.service.question.QuesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/question")
public class QuesController {
    private final QuesService questionService;

    public QuesController(QuesService questionService) {
        this.questionService = questionService;
    }

    /**
     * 获取所有题目
     * GET /api/question
     */
    @GetMapping
    public ResponseEntity<List<QuestionDTO>> getAllQuestions() {  // ← 改返回类型
        try {
            List<QuestionDTO> questions = questionService.getAllQuestionDTO();  // ← 改方法名
            return ResponseEntity.ok(questions);
        } catch (BusinessException e) {
            log.error("获取所有题目失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 获取随机题目
     * GET /api/question/random?count=5
     */
    @GetMapping("/random")
    public ResponseEntity<List<QuestionDTO>> getRandomQuestions(  // ← 改返回类型
                                                                  @RequestParam(defaultValue = "10") int count) {
        try {
            List<QuestionDTO> questions = questionService.getRandomQuestionDTO(count);  // ← 改方法名
            return ResponseEntity.ok(questions);
        } catch (BusinessException e) {
            log.error("获取随机题目失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 根据人数获取合适的题目
     * GET /api/question/suitable?playerCount=4&questionCount=10
     */
    @GetMapping("/suitable")
    public ResponseEntity<List<QuestionDTO>> getSuitableQuestions(  // ← 改返回类型
                                                                    @RequestParam int playerCount,
                                                                    @RequestParam(defaultValue = "10") int questionCount) {
        try {
            List<QuestionDTO> questions = questionService.getQuestionsByPlayerCountDTO(playerCount, questionCount);  // ← 改方法名
            return ResponseEntity.ok(questions);
        } catch (BusinessException e) {
            log.error("获取合适题目失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}