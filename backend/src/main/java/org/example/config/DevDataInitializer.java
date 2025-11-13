package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PlayerEntity;
import org.example.repository.PlayerRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 开发环境数据初始化器
 * 自动创建测试账号
 * ⚠️ 只在开发环境运行（@Profile("dev")）
 */
@Component
@Profile("dev")  // 🔥 只在开发环境激活
@RequiredArgsConstructor
@Slf4j
public class DevDataInitializer implements ApplicationRunner {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        createTestAccounts();
    }

    /**
     * 创建固定测试账号
     */
    private void createTestAccounts() {
        String[] testUsers = {"test1", "test2", "test3", "test4"};

        log.info("🔧 开始初始化测试账号...");

        for (String username : testUsers) {
            // 检查账号是否已存在
            if (playerRepository.findByUsername(username).isEmpty()) {
                PlayerEntity player = PlayerEntity.builder()
                        .playerId(UUID.randomUUID().toString())
                        .username(username)
                        .password(passwordEncoder.encode("123456"))
                        .name("测试玩家-" + username)
                        .spectator(false)
                        .ready(false)
                        .deleted(false)
                        .build();

                playerRepository.save(player);
                log.info("✅ 创建测试账号: {} (密码: 123456)", username);
            } else {
                log.info("⏭️  测试账号已存在: {}", username);
            }
        }

        log.info("🎉 测试账号初始化完成！");
    }
}
