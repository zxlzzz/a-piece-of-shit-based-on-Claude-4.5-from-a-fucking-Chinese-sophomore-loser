package org.example.service.timer;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * 题目超时调度服务实现
 */
@Service
@Slf4j
public class QuestionTimerService {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);
    private final Map<String, ScheduledFuture<?>> activeTimers = new ConcurrentHashMap<>();

    public void scheduleTimeout(String roomCode, long seconds, Runnable onTimeout) {
        // 取消已存在的定时器
        cancelTimeout(roomCode);

        // 启动新定时器
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                onTimeout.run();
            } catch (Exception e) {
                log.error("❌ 房间 {} 超时回调执行失败", roomCode, e);
            } finally {
                activeTimers.remove(roomCode);
            }
        }, seconds, TimeUnit.SECONDS);

        activeTimers.put(roomCode, future);
    }

    public void cancelTimeout(String roomCode) {
        ScheduledFuture<?> future = activeTimers.remove(roomCode);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
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