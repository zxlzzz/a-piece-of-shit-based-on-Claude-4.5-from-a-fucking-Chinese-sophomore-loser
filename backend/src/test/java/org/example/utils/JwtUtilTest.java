package org.example.utils;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.example.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试
 *
 * 测试覆盖：
 * 1. Token生成
 * 2. Token验证（有效、过期、篡改）
 * 3. 信息提取（username、playerId）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JWT工具类测试")
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private JwtProperties jwtProperties;

    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PLAYER_ID = "test-player-id";
    private static final String TEST_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";
    private static final Long TEST_EXPIRATION = 3600000L; // 1小时

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(TEST_SECRET);
        jwtProperties.setExpiration(TEST_EXPIRATION);

        jwtUtil = new JwtUtil(jwtProperties);
    }

    // ==================== Token生成测试 ====================

    @Test
    @DisplayName("生成Token - 成功")
    void generateToken_Success() {
        // 执行
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        // 验证
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.split("\\.").length == 3); // JWT格式：header.payload.signature
    }

    @Test
    @DisplayName("生成Token - 包含正确的username")
    void generateToken_ContainsUsername() {
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        String username = jwtUtil.getUsernameFromToken(token);
        assertEquals(TEST_USERNAME, username);
    }

    @Test
    @DisplayName("生成Token - 包含正确的playerId")
    void generateToken_ContainsPlayerId() {
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        String playerId = jwtUtil.getPlayerIdFromToken(token);
        assertEquals(TEST_PLAYER_ID, playerId);
    }

    @Test
    @DisplayName("生成Token - 不同的用户生成不同的token")
    void generateToken_DifferentTokensForDifferentUsers() {
        String token1 = jwtUtil.generateToken("user1", "player1");
        String token2 = jwtUtil.generateToken("user2", "player2");

        assertNotEquals(token1, token2);
    }

    // ==================== Token验证测试 ====================

    @Test
    @DisplayName("验证Token - 有效的token返回true")
    void validateToken_ValidToken_ReturnsTrue() {
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        boolean isValid = jwtUtil.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("验证Token - 空token返回false")
    void validateToken_EmptyToken_ReturnsFalse() {
        boolean isValid = jwtUtil.validateToken("");

        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证Token - null token返回false")
    void validateToken_NullToken_ReturnsFalse() {
        boolean isValid = jwtUtil.validateToken(null);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证Token - 格式错误的token返回false")
    void validateToken_MalformedToken_ReturnsFalse() {
        String malformedToken = "not.a.valid.jwt.token";

        boolean isValid = jwtUtil.validateToken(malformedToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证Token - 被篡改的token返回false")
    void validateToken_TamperedToken_ReturnsFalse() {
        String validToken = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);
        String tamperedToken = validToken + "tampered";

        boolean isValid = jwtUtil.validateToken(tamperedToken);

        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证Token - 过期的token返回false")
    void validateToken_ExpiredToken_ReturnsFalse() throws InterruptedException {
        // 创建一个只有1毫秒有效期的JwtUtil
        JwtProperties shortExpirationProperties = new JwtProperties();
        shortExpirationProperties.setSecret(TEST_SECRET);
        shortExpirationProperties.setExpiration(1L); // 1毫秒

        JwtUtil shortExpirationJwtUtil = new JwtUtil(shortExpirationProperties);

        // 生成token
        String token = shortExpirationJwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        // 等待token过期
        Thread.sleep(10);

        // 验证
        boolean isValid = shortExpirationJwtUtil.validateToken(token);
        assertFalse(isValid);
    }

    @Test
    @DisplayName("验证Token - 使用错误的secret签名的token返回false")
    void validateToken_WrongSecret_ReturnsFalse() {
        // 使用一个secret生成token
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        // 使用另一个secret验证
        JwtProperties differentSecretProperties = new JwtProperties();
        differentSecretProperties.setSecret("different-secret-key-that-is-also-long-enough-for-hmac");
        differentSecretProperties.setExpiration(TEST_EXPIRATION);

        JwtUtil differentSecretJwtUtil = new JwtUtil(differentSecretProperties);

        boolean isValid = differentSecretJwtUtil.validateToken(token);
        assertFalse(isValid);
    }

    // ==================== 信息提取测试 ====================

    @Test
    @DisplayName("提取Username - 成功")
    void getUsernameFromToken_Success() {
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        String username = jwtUtil.getUsernameFromToken(token);

        assertEquals(TEST_USERNAME, username);
    }

    @Test
    @DisplayName("提取PlayerId - 成功")
    void getPlayerIdFromToken_Success() {
        String token = jwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        String playerId = jwtUtil.getPlayerIdFromToken(token);

        assertEquals(TEST_PLAYER_ID, playerId);
    }

    @Test
    @DisplayName("提取信息 - 从过期的token提取（能提取但验证失败）")
    void getInfoFromExpiredToken() throws InterruptedException {
        // 创建短期token
        JwtProperties shortExpirationProperties = new JwtProperties();
        shortExpirationProperties.setSecret(TEST_SECRET);
        shortExpirationProperties.setExpiration(1L);

        JwtUtil shortExpirationJwtUtil = new JwtUtil(shortExpirationProperties);
        String token = shortExpirationJwtUtil.generateToken(TEST_USERNAME, TEST_PLAYER_ID);

        Thread.sleep(10);

        // 验证token无效
        assertFalse(shortExpirationJwtUtil.validateToken(token));

        // 但仍然可以解析信息（这是预期行为，因为getClaimsFromToken会抛异常）
        assertThrows(ExpiredJwtException.class, () -> {
            shortExpirationJwtUtil.getUsernameFromToken(token);
        });
    }

    @Test
    @DisplayName("提取信息 - 从格式错误的token提取会抛异常")
    void getInfoFromMalformedToken() {
        String malformedToken = "not.a.valid.token";

        assertThrows(MalformedJwtException.class, () -> {
            jwtUtil.getUsernameFromToken(malformedToken);
        });
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("边界情况 - username为空字符串")
    void generateToken_EmptyUsername() {
        String token = jwtUtil.generateToken("", TEST_PLAYER_ID);

        assertNotNull(token);
        assertEquals("", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("边界情况 - playerId为空字符串")
    void generateToken_EmptyPlayerId() {
        String token = jwtUtil.generateToken(TEST_USERNAME, "");

        assertNotNull(token);
        assertEquals("", jwtUtil.getPlayerIdFromToken(token));
    }

    @Test
    @DisplayName("边界情况 - username和playerId都为null")
    void generateToken_NullValues() {
        String token = jwtUtil.generateToken(null, null);

        assertNotNull(token);
        assertNull(jwtUtil.getUsernameFromToken(token));
        assertNull(jwtUtil.getPlayerIdFromToken(token));
    }

    @Test
    @DisplayName("边界情况 - 特殊字符的username")
    void generateToken_SpecialCharactersInUsername() {
        String specialUsername = "user@#$%^&*()";
        String token = jwtUtil.generateToken(specialUsername, TEST_PLAYER_ID);

        assertEquals(specialUsername, jwtUtil.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("边界情况 - 中文username")
    void generateToken_ChineseUsername() {
        String chineseUsername = "中文用户名";
        String token = jwtUtil.generateToken(chineseUsername, TEST_PLAYER_ID);

        assertEquals(chineseUsername, jwtUtil.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("Token生命周期 - 生成多个token都有效")
    void generateToken_MultipleTokensAllValid() {
        String token1 = jwtUtil.generateToken("user1", "player1");
        String token2 = jwtUtil.generateToken("user2", "player2");
        String token3 = jwtUtil.generateToken("user3", "player3");

        assertTrue(jwtUtil.validateToken(token1));
        assertTrue(jwtUtil.validateToken(token2));
        assertTrue(jwtUtil.validateToken(token3));
    }
}
