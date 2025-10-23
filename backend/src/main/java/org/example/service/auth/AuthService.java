package org.example.service.auth;

import org.example.dto.AuthResponseDTO;
import org.example.dto.LoginRequestDTO;
import org.example.dto.RegisterRequestDTO;

public interface AuthService {

    /**
     * 用户注册
     */
    AuthResponseDTO register(RegisterRequestDTO request);

    /**
     * 用户登录
     */
    AuthResponseDTO login(LoginRequestDTO request);
}
