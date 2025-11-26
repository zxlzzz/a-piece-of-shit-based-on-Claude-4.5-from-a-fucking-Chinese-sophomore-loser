package org.example.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponseDTO;
import org.example.dto.GuestLoginRequestDTO;
import org.example.dto.LoginRequestDTO;
import org.example.dto.RegisterRequestDTO;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.example.utils.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        // 验证输入
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException("密码至少需要6位");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }

        // 检查用户名是否已存在（不区分大小写）
        String username = request.getUsername().trim().toLowerCase();
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
                .name(request.getName().trim())
                .ready(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build();

        playerRepository.save(player);

        // 生成 token
        String token = jwtUtil.generateToken(username, playerId);


        return AuthResponseDTO.builder()
                .token(token)
                .id(player.getId())
                .playerId(playerId)
                .username(username)
                .name(request.getName().trim())
                .roomCode(null)
                .build();
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        // 验证输入
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        // 查找用户（不区分大小写）
        String username = request.getUsername().trim().toLowerCase();
        PlayerEntity player = playerRepository.findByUsernameWithRoom(username)
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

        // 检查玩家是否在房间中（room已通过JOIN FETCH加载，不会懒加载）
        String roomCode = null;
        if (player.getRoom() != null) {
            roomCode = player.getRoom().getRoomCode();
        }

        return AuthResponseDTO.builder()
                .token(token)
                .id(player.getId())
                .playerId(player.getPlayerId())
                .username(username)
                .name(player.getName())
                .roomCode(roomCode)
                .build();
    }
}
