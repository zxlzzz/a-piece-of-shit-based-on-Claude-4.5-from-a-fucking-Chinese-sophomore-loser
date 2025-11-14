package org.example.service.auth.impl;

import org.example.dto.AuthResponseDTO;
import org.example.dto.GuestLoginRequestDTO;
import org.example.dto.LoginRequestDTO;
import org.example.dto.RegisterRequestDTO;
import org.example.entity.PlayerEntity;
import org.example.exception.BusinessException;
import org.example.repository.PlayerRepository;
import org.example.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 *
 * 测试覆盖：
 * 1. 用户注册（成功、失败）
 * 2. 用户登录（成功、失败）
 * 3. 游客登录（成功、失败）
 * 4. 输入验证
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证服务测试")
class AuthServiceImplTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NAME = "测试用户";
    private static final String TEST_PLAYER_ID = "test-player-id";
    private static final String TEST_TOKEN = "test-jwt-token";

    @BeforeEach
    void setUp() {
        // 设置默认的mock行为
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn(TEST_TOKEN);
    }

    // ==================== 注册测试 ====================

    @Test
    @DisplayName("注册成功 - 正常流程")
    void register_Success() {
        // 准备测试数据
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);

        // Mock：用户名不存在
        when(playerRepository.existsByUsername(TEST_USERNAME.toLowerCase())).thenReturn(false);

        // Mock：保存玩家
        when(playerRepository.save(any(PlayerEntity.class))).thenAnswer(invocation -> {
            PlayerEntity player = invocation.getArgument(0);
            player.setId(1L);
            return player;
        });

        // 执行
        AuthResponseDTO response = authService.register(request);

        // 验证
        assertNotNull(response);
        assertEquals(TEST_TOKEN, response.getToken());
        assertEquals(TEST_USERNAME.toLowerCase(), response.getUsername());
        assertEquals(TEST_NAME, response.getName());
        assertNotNull(response.getPlayerId());

        // 验证方法调用
        verify(playerRepository).existsByUsername(TEST_USERNAME.toLowerCase());
        verify(passwordEncoder).encode(TEST_PASSWORD);
        verify(playerRepository).save(any(PlayerEntity.class));
        verify(jwtUtil).generateToken(eq(TEST_USERNAME.toLowerCase()), anyString());
    }

    @Test
    @DisplayName("注册失败 - 用户名已存在")
    void register_UsernameExists() {
        // 准备测试数据
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);

        // Mock：用户名已存在
        when(playerRepository.existsByUsername(TEST_USERNAME.toLowerCase())).thenReturn(true);

        // 执行并验证异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.register(request);
        });

        assertEquals("用户名已存在", exception.getMessage());
        verify(playerRepository, never()).save(any());
    }

    @Test
    @DisplayName("注册失败 - 用户名为空")
    void register_EmptyUsername() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("");
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.register(request);
        });

        assertEquals("用户名不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("注册失败 - 密码太短")
    void register_ShortPassword() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword("123");  // 少于6位
        request.setName(TEST_NAME);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.register(request);
        });

        assertEquals("密码至少需要6位", exception.getMessage());
    }

    @Test
    @DisplayName("注册失败 - 用户名长度不符合要求")
    void register_InvalidUsernameLength() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("a");  // 只有1个字符
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.register(request);
        });

        assertEquals("用户名长度应为2-20个字符", exception.getMessage());
    }

    @Test
    @DisplayName("注册 - 用户名转小写")
    void register_UsernameToLowerCase() {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setUsername("TestUser");  // 大小写混合
        request.setPassword(TEST_PASSWORD);
        request.setName(TEST_NAME);

        when(playerRepository.existsByUsername("testuser")).thenReturn(false);
        when(playerRepository.save(any(PlayerEntity.class))).thenAnswer(invocation -> {
            PlayerEntity player = invocation.getArgument(0);
            player.setId(1L);
            return player;
        });

        AuthResponseDTO response = authService.register(request);

        // 验证用户名被转为小写
        assertEquals("testuser", response.getUsername());
        verify(playerRepository).existsByUsername("testuser");
    }

    // ==================== 登录测试 ====================

    @Test
    @DisplayName("登录成功 - 正常流程")
    void login_Success() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        PlayerEntity player = PlayerEntity.builder()
                .id(1L)
                .playerId(TEST_PLAYER_ID)
                .username(TEST_USERNAME.toLowerCase())
                .password("encoded-password")
                .name(TEST_NAME)
                .deleted(false)
                .build();

        when(playerRepository.findByUsername(TEST_USERNAME.toLowerCase()))
                .thenReturn(Optional.of(player));
        when(passwordEncoder.matches(TEST_PASSWORD, "encoded-password")).thenReturn(true);

        // 执行
        AuthResponseDTO response = authService.login(request);

        // 验证
        assertNotNull(response);
        assertEquals(TEST_TOKEN, response.getToken());
        assertEquals(TEST_USERNAME.toLowerCase(), response.getUsername());
        assertEquals(TEST_NAME, response.getName());
        assertEquals(TEST_PLAYER_ID, response.getPlayerId());

        verify(passwordEncoder).matches(TEST_PASSWORD, "encoded-password");
        verify(jwtUtil).generateToken(TEST_USERNAME.toLowerCase(), TEST_PLAYER_ID);
    }

    @Test
    @DisplayName("登录失败 - 用户不存在")
    void login_UserNotFound() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        when(playerRepository.findByUsername(TEST_USERNAME.toLowerCase()))
                .thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(request);
        });

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("登录失败 - 密码错误")
    void login_WrongPassword() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword("wrongpassword");

        PlayerEntity player = PlayerEntity.builder()
                .username(TEST_USERNAME.toLowerCase())
                .password("encoded-password")
                .deleted(false)
                .build();

        when(playerRepository.findByUsername(TEST_USERNAME.toLowerCase()))
                .thenReturn(Optional.of(player));
        when(passwordEncoder.matches("wrongpassword", "encoded-password")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(request);
        });

        assertEquals("用户名或密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("登录失败 - 账号已被删除")
    void login_AccountDeleted() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername(TEST_USERNAME);
        request.setPassword(TEST_PASSWORD);

        PlayerEntity player = PlayerEntity.builder()
                .username(TEST_USERNAME.toLowerCase())
                .password("encoded-password")
                .deleted(true)  // 账号已删除
                .build();

        when(playerRepository.findByUsername(TEST_USERNAME.toLowerCase()))
                .thenReturn(Optional.of(player));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(request);
        });

        assertEquals("该账号已被删除", exception.getMessage());
    }

    @Test
    @DisplayName("登录失败 - 用户名为空")
    void login_EmptyUsername() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setUsername("");
        request.setPassword(TEST_PASSWORD);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.login(request);
        });

        assertEquals("用户名不能为空", exception.getMessage());
    }

    // ==================== 游客登录测试 ====================

    @Test
    @DisplayName("游客登录成功 - 正常流程")
    void guestLogin_Success() {
        GuestLoginRequestDTO request = new GuestLoginRequestDTO();
        request.setName(TEST_NAME);

        when(playerRepository.save(any(PlayerEntity.class))).thenAnswer(invocation -> {
            PlayerEntity player = invocation.getArgument(0);
            player.setId(1L);
            return player;
        });

        // 执行
        AuthResponseDTO response = authService.guestLogin(request);

        // 验证
        assertNotNull(response);
        assertEquals(TEST_TOKEN, response.getToken());
        assertNull(response.getUsername());  // 游客没有username
        assertEquals(TEST_NAME, response.getName());
        assertNotNull(response.getPlayerId());

        // 验证保存的实体
        verify(playerRepository).save(argThat(player ->
            player.getUsername() == null &&
            player.getPassword() == null &&
            player.getName().equals(TEST_NAME)
        ));
    }

    @Test
    @DisplayName("游客登录失败 - 昵称为空")
    void guestLogin_EmptyName() {
        GuestLoginRequestDTO request = new GuestLoginRequestDTO();
        request.setName("");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.guestLogin(request);
        });

        assertEquals("游客昵称不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("游客登录失败 - 昵称太短")
    void guestLogin_NameTooShort() {
        GuestLoginRequestDTO request = new GuestLoginRequestDTO();
        request.setName("a");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.guestLogin(request);
        });

        assertEquals("昵称长度应为2-20个字符", exception.getMessage());
    }

    @Test
    @DisplayName("游客登录失败 - 昵称太长")
    void guestLogin_NameTooLong() {
        GuestLoginRequestDTO request = new GuestLoginRequestDTO();
        request.setName("这是一个非常非常非常非常长的昵称");  // 超过20个字符

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            authService.guestLogin(request);
        });

        assertEquals("昵称长度应为2-20个字符", exception.getMessage());
    }

    @Test
    @DisplayName("游客登录 - 自动去除首尾空格")
    void guestLogin_TrimName() {
        GuestLoginRequestDTO request = new GuestLoginRequestDTO();
        request.setName("  测试用户  ");

        when(playerRepository.save(any(PlayerEntity.class))).thenAnswer(invocation -> {
            PlayerEntity player = invocation.getArgument(0);
            player.setId(1L);
            return player;
        });

        AuthResponseDTO response = authService.guestLogin(request);

        assertEquals("测试用户", response.getName());
    }
}
