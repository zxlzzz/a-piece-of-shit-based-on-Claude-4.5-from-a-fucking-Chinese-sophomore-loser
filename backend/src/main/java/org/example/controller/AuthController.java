package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponseDTO;
import org.example.dto.GuestLoginRequestDTO;
import org.example.dto.LoginRequestDTO;
import org.example.dto.RegisterRequestDTO;
import org.example.exception.BusinessException;
import org.example.service.auth.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * 用户注册
     * POST /api/auth/register
     * 🔥 P1-2修复：移除try-catch，让全局异常处理器统一处理错误响应
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody RegisterRequestDTO request) {
        AuthResponseDTO response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 用户登录
     * POST /api/auth/login
     * 🔥 P1-2修复：移除try-catch，让全局异常处理器统一处理错误响应
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        AuthResponseDTO response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 游客快速试玩（无需注册）
     * POST /api/auth/guest
     * 🔥 P1-2修复：移除try-catch，让全局异常处理器统一处理错误响应
     */
    @PostMapping("/guest")
    public ResponseEntity<AuthResponseDTO> guestLogin(@RequestBody GuestLoginRequestDTO request) {
        AuthResponseDTO response = authService.guestLogin(request);
        return ResponseEntity.ok(response);
    }
}