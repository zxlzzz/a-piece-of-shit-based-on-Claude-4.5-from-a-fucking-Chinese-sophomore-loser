package org.example.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponseDTO;
import org.example.dto.LoginRequestDTO;
import org.example.dto.RegisterRequestDTO;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final PlayerRepository playerRepository;

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new BusinessException("密码至少需要6位");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessException("昵称不能为空");
        }

        String username = request.getUsername().trim().toLowerCase();
        if (playerRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }

        String playerId = UUID.randomUUID().toString();

        PlayerEntity player = PlayerEntity.builder()
                .playerId(playerId)
                .username(username)
                .password(request.getPassword())  // 明文存储（demo 用）
                .name(request.getName().trim())
                .ready(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deleted(false)
                .build();

        playerRepository.save(player);

        return AuthResponseDTO.builder()
                .token(playerId)  // 直接用 playerId 作为 token
                .id(player.getId())
                .playerId(playerId)
                .username(username)
                .name(request.getName().trim())
                .roomCode(null)
                .build();
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            throw new BusinessException("密码不能为空");
        }

        String username = request.getUsername().trim().toLowerCase();
        PlayerEntity player = playerRepository.findByUsernameWithRoom(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (player.getDeleted()) {
            throw new BusinessException("该账号已被删除");
        }

        // 明文密码比较（demo 用）
        if (!request.getPassword().equals(player.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        String roomCode = null;
        if (player.getRoom() != null) {
            roomCode = player.getRoom().getRoomCode();
        }

        return AuthResponseDTO.builder()
                .token(player.getPlayerId())  // 直接用 playerId 作为 token
                .id(player.getId())
                .playerId(player.getPlayerId())
                .username(username)
                .name(player.getName())
                .roomCode(roomCode)
                .build();
    }
}
