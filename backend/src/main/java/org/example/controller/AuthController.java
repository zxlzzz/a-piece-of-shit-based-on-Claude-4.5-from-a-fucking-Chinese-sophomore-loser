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
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody RegisterRequestDTO request) {
        try {
            AuthResponseDTO response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("注册失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        try {
            AuthResponseDTO response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("登录失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * 游客快速试玩（无需注册）
     * POST /api/auth/guest
     */
    @PostMapping("/guest")
    public ResponseEntity<AuthResponseDTO> guestLogin(@RequestBody GuestLoginRequestDTO request) {
        try {
            AuthResponseDTO response = authService.guestLogin(request);
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            log.error("游客登录失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}