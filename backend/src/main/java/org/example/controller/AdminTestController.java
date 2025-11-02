package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.RoomDTO;
import org.example.service.game.GameService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员测试工具 Controller
 */
@RestController
@RequestMapping("/api/admin/test")
@RequiredArgsConstructor
@Slf4j
public class AdminTestController {

    private final GameService gameService;

    /**
     * 创建测试房间（自动填充虚拟玩家）
     */
    @PostMapping("/room")
    public ResponseEntity<Map<String, Object>> createTestRoom(
            @RequestParam(defaultValue = "3") Integer maxPlayers,
            @RequestParam(defaultValue = "5") Integer questionCount
    ) {
        log.info("创建测试房间: maxPlayers={}, questionCount={}", maxPlayers, questionCount);

        // 创建测试房间
        RoomDTO roomDTO = gameService.createTestRoom(maxPlayers, questionCount);

        // 构建响应
        Map<String, Object> response = new HashMap<>();
        response.put("roomCode", roomDTO.getRoomCode());
        response.put("testRoom", true);
        response.put("maxPlayers", maxPlayers);
        response.put("botCount", maxPlayers - 1);

        log.info("测试房间创建成功: {}", roomDTO.getRoomCode());

        return ResponseEntity.ok(response);
    }
}
