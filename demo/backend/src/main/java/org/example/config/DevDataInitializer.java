package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.PlayerEntity;
import org.example.repository.PlayerRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 开发环境数据初始化器 - 简化版
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataInitializer implements ApplicationRunner {

    private final PlayerRepository playerRepository;

    @Override
    public void run(ApplicationArguments args) {
        createTestAccounts();
    }

    private void createTestAccounts() {
        String[] testUsers = {"test1", "test2", "test3", "test4"};

        log.info("🔧 开始初始化测试账号...");

        for (String username : testUsers) {
            if (playerRepository.findByUsername(username).isEmpty()) {
                PlayerEntity player = PlayerEntity.builder()
                        .playerId(UUID.randomUUID().toString())
                        .username(username)
                        .password("123456")  // 明文密码（demo 用）
                        .name("测试玩家-" + username)
                        .ready(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
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
