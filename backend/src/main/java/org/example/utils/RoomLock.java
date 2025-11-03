package org.example.utils;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 房间级别的并发控制工具
 * 为每个房间提供唯一的锁对象，确保对同一房间的操作是线程安全的
 */
public class RoomLock {

    /**
     * 房间锁映射表
     * key: 房间码
     * value: 该房间专用的锁对象
     */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    /**
     * 获取指定房间的锁对象
     * 如果锁对象不存在，则创建一个新的
     *
     * @param roomCode 房间码
     * @return 该房间的锁对象
     */
    public static Object getLock(String roomCode) {
        return LOCKS.computeIfAbsent(roomCode, k -> new Object());
    }

    /**
     * 移除指定房间的锁对象
     * 通常在房间删除时调用，避免内存泄漏
     *
     * @param roomCode 房间码
     */
    public static void removeLock(String roomCode) {
        LOCKS.remove(roomCode);
    }

    /**
     * 获取当前锁对象总数（用于监控）
     *
     * @return 锁对象数量
     */
    public static int getLockCount() {
        return LOCKS.size();
    }
}
