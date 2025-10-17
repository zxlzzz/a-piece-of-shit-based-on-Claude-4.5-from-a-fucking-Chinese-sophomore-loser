package org.example.service.timer.impl;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.service.timer.QuestionTimerService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 题目超时调度服务实现
 */
@Service
@Slf4j
public class QuestionTimerServiceImpl implements QuestionTimerService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    @Override
    public void scheduleTimeout(String roomCode, long seconds, Runnable onTimeout) {
        // 取消已存在的定时器
        cancelTimeout(roomCode);

        // 启动新定时器
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                log.info("⏰ 房间 {} 题目超时，执行回调", roomCode);
                onTimeout.run();
            } catch (Exception e) {
                log.error("❌ 房间 {} 超时回调执行失败", roomCode, e);
            } finally {
                activeTimers.remove(roomCode);
            }
        }, seconds, TimeUnit.SECONDS);

        activeTimers.put(roomCode, future);
        log.debug("⏱️ 房间 {} 启动 {} 秒超时定时器", roomCode, seconds);
    }

    @Override
    public void cancelTimeout(String roomCode) {
        ScheduledFuture<?> future = activeTimers.remove(roomCode);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
            log.debug("⏹️ 取消房间 {} 的超时定时器", roomCode);
        }
    }

    @PreDestroy
    @Override
    public void shutdown() {
        log.info("🛑 关闭题目超时调度器");
        activeTimers.values().forEach(future -> future.cancel(false));
        activeTimers.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}