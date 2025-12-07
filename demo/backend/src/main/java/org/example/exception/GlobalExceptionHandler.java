package org.example.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
    log.warn("业务异常: {}", e.getMessage());

    Map<String, Object> response = new HashMap<>();
    response.put("error", true);
    response.put("message", e.getMessage());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
    log.error("系统异常: ", e);

    Map<String, Object> response = new HashMap<>();
    response.put("error", true);
    response.put("message", "系统内部错误");
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.internalServerError().body(response);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
    log.warn("参数验证失败: {}", e.getMessage());

    Map<String, Object> response = new HashMap<>();
    response.put("error", true);
    response.put("message", "参数验证失败");
    response.put("details", e.getBindingResult().getAllErrors());
    response.put("timestamp", System.currentTimeMillis());

    return ResponseEntity.badRequest().body(response);
  }
}
