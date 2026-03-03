package org.example.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.service.cache.RoomCache;
import org.example.utils.RoomLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * RoomLock定时清理任务
 * 防止长时间运行后LOCKS Map堆积废弃对象导致内存泄漏
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoomLockCleanupScheduler {

    private final RoomCache roomCache;

    /**
     * 每10分钟清理一次废弃的RoomLock
     */
    @Scheduled(fixedDelay = 600000) // 10分钟
    public void cleanupOrphanedLocks() {
        try {
            // 获取所有活跃房间的代码
            var activeRoomCodes = roomCache.getAll().stream()
                    .map(gameRoom -> gameRoom.getRoomCode())
                    .collect(Collectors.toSet());

            // 清理不在活跃列表中的锁
            int removed = RoomLock.cleanupOrphanedLocks(activeRoomCodes);

            if (removed > 0) {
                log.info("🧹 定时清理完成: 活跃房间={}, 移除废弃锁={}, 剩余锁={}",
                    activeRoomCodes.size(), removed, RoomLock.getLockCount());
            }
        } catch (Exception e) {
            log.error("❌ RoomLock定时清理失败", e);
        }
    }
}
