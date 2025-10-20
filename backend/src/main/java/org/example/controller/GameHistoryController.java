package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.entity.GameEntity;
import org.example.entity.GameResultEntity;
import org.example.exception.BusinessException;
import org.example.repository.GameRepository;
import org.example.repository.GameResultRepository;
import org.example.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/games")
@Slf4j
public class GameHistoryController {

    @Autowired
    private GameService gameService;

    /**
     * 获取所有历史记录列表
     */
    @GetMapping("/history")
    public ResponseEntity<List<GameHistorySummaryDTO>> getHistoryList(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String playerId) {

        try {
            List<GameHistorySummaryDTO> summaries = gameService.getHistoryList(days, playerId);
            return ResponseEntity.ok(summaries);
        } catch (Exception e) {
            log.error("获取历史记录列表失败", e);
            return ResponseEntity.internalServerError().body(null);
        }
    }

    /**
     * 获取单场游戏的详细历史（用于弹窗）
     */
    @GetMapping("/history/{gameId}")
    public ResponseEntity<GameHistoryDTO> getHistoryDetail(@PathVariable Long gameId) {
        try {
            GameHistoryDTO dto = gameService.getHistoryDetail(gameId);
            return ResponseEntity.ok(dto);
        } catch (BusinessException e) {
            log.error("获取游戏详情失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            log.error("获取游戏详情失败: gameId={}", gameId, e);
            return ResponseEntity.internalServerError().body(null);
        }
    }
}