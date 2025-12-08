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
 * 本文件用于快捷注册，在启动时会自动注册四个账号，test1,test2,test3,test4，密码都是123456
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


        for (String username : testUsers) {
            if (playerRepository.findByUsername(username).isEmpty()) {
                PlayerEntity player = PlayerEntity.builder()
                        .playerId(UUID.randomUUID().toString())
                        .username(username)
                        .password("123456")
                        .name("测试玩家-" + username)
                        .ready(false)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .deleted(false)
                        .build();

                playerRepository.save(player);
            } else {
                log.info("⏭️  测试账号已存在: {}", username);
            }
        }

    }
}
