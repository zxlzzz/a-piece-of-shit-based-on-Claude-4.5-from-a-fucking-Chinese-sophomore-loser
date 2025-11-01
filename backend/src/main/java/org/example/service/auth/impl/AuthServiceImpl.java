package org.example.service.auth.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponseDTO;
import org.example.dto.GuestLoginRequestDTO;
import org.example.dto.LoginRequestDTO;
import org.example.dto.RegisterRequestDTO;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.example.service.auth.AuthService;
import org.example.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        // 验证输入
        validateRegisterRequest(request);

        // 检查用户名是否已存在（不区分大小写）
        String username = request.getUsername().toLowerCase();
        if (playerRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }

        // 生成 playerId（UUID）
        String playerId = UUID.randomUUID().toString();

        // 创建玩家
        PlayerEntity player = PlayerEntity.builder()
                .playerId(playerId)
                .username(username)
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .ready(false)
                .build();

        playerRepository.save(player);

        // 生成 token
        String token = jwtUtil.generateToken(username, playerId);

        log.info("用户注册成功: username={}, playerId={}", username, playerId);

        return AuthResponseDTO.builder()
                .token(token)
                .id(player.getId())
                .playerId(playerId)
                .username(username)
                .name(request.getName())
                .build();
    }

    @Override
    public AuthResponseDTO login(LoginRequestDTO request) {
        // 验证输入
        validateLoginRequest(request);

        // 查找用户（不区分大小写）
        String username = request.getUsername().toLowerCase();
        PlayerEntity player = playerRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        // 检查账号是否被删除
        if (player.getDeleted()) {
            throw new BusinessException("该账号已被删除");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), player.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 生成 token
        String token = jwtUtil.generateToken(username, player.getPlayerId());

        log.info("用户登录成功: username={}, playerId={}", username, player.getPlayerId());

        return AuthResponseDTO.builder()
                .token(token)
                .playerId(player.getPlayerId())
                .username(username)
                .name(player.getName())
                .build();
    }

    @Override
    @Transactional
    public AuthResponseDTO guestLogin(GuestLoginRequestDTO request) {
        // 验证游客昵称
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException("游客昵称不能为空");
        }

        String name = request.getName().trim();
        if (name.length() < 2 || name.length() > 20) {
            throw new BusinessException("昵称长度应为2-20个字符");
        }

        // 生成 playerId（UUID）
        String playerId = UUID.randomUUID().toString();

        // 创建游客账号（username 和 password 为 null）
        PlayerEntity guestPlayer = PlayerEntity.builder()
                .playerId(playerId)
                .username(null)  // 游客没有用户名
                .password(null)  // 游客没有密码
                .name(name)
                .ready(false)
                .spectator(false)
                .deleted(false)
                .build();

        playerRepository.save(guestPlayer);

        // 生成 token（使用 playerId 作为标识）
        String token = jwtUtil.generateToken(playerId, playerId);

        log.info("游客试玩: playerId={}, name={}", playerId, name);

        return AuthResponseDTO.builder()
                .token(token)
                .id(guestPlayer.getId())
                .playerId(playerId)
                .username(null)  // 游客没有 username
                .name(name)
                .build();
    }

    /**
     * 验证注册请求
     */
    private void validateRegisterRequest(RegisterRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException("密码至少需要6位");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }

        // 用户名规则：2-20字符
        String username = request.getUsername().trim();
        if (username.length() < 2 || username.length() > 20) {
            throw new BusinessException("用户名长度应为2-20个字符");
        }

        // 昵称规则：2-20字符
        String name = request.getName().trim();
        if (name.length() < 2 || name.length() > 20) {
            throw new BusinessException("昵称长度应为2-20个字符");
        }
    }

    /**
     * 验证登录请求
     */
    private void validateLoginRequest(LoginRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }
    }
}
