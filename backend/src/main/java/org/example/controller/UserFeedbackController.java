package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.UserFeedbackDTO;
import org.example.service.feedback.UserFeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户反馈控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class UserFeedbackController {

    private final UserFeedbackService feedbackService;

    /**
     * 提交用户反馈
     * POST /api/feedback
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> submitFeedback(@RequestBody UserFeedbackDTO feedbackDTO) {
        Map<String, Object> response = new HashMap<>();
        try {
            feedbackService.submitFeedback(feedbackDTO);
            response.put("success", true);
            response.put("message", "提交成功，感谢您的反馈！");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("反馈提交失败 - 参数错误: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("反馈提交失败", e);
            response.put("success", false);
            response.put("message", "提交失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
