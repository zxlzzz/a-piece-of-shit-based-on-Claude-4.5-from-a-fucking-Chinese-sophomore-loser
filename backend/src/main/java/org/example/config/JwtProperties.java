package org.example.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
@Slf4j
public class JwtProperties {
    private String secret;
    private Long expiration;

    /**
     * 🔥 P0-1修复：启动时验证JWT密钥是否已配置且足够强
     */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException(
                "❌ JWT密钥未配置！请设置环境变量 JWT_SECRET。" +
                "\n建议使用以下命令生成强密钥：" +
                "\n  openssl rand -base64 32" +
                "\n或在Linux/Mac上：" +
                "\n  cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1"
            );
        }

        // 检查密钥强度（至少32字节 = 256位）
        if (secret.getBytes().length < 32) {
            log.warn("⚠️ JWT密钥长度不足32字节，建议使用更强的密钥（256位以上）");
        }

        // 检查是否使用了示例密钥
        if (secret.contains("dev-secret") || secret.contains("example") ||
            secret.contains("test") || secret.equals("secret")) {
            throw new IllegalStateException(
                "❌ 检测到不安全的JWT密钥！禁止使用示例密钥或弱密钥。" +
                "\n请设置强度足够的 JWT_SECRET 环境变量。"
            );
        }

        log.info("✅ JWT配置验证通过（密钥长度: {} 字节）", secret.getBytes().length);
    }
}