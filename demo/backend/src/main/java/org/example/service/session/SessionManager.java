package org.example.service.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket会话管理服务
 * 管理在线用户会话，防止重复登录，实现踢出功能
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionManager {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 在线会话映射：playerId -> SessionInfo
     * 只管理注册用户的会话，游客不管理（因为每次登录playerId都不同）
     */
    private final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();

    /**
     * sessionId 到 playerId 的反向映射，用于快速查找
     */
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();

    /**
     * 立即注册会话ID映射
     *
     * @param sessionId WebSocket会话ID
     * @param playerId 玩家ID
     */
    public void registerSessionIdMapping(String sessionId, String playerId) {
        sessionToPlayer.put(sessionId, playerId);
        log.debug(" 立即注册会话映射: sessionId={}, playerId={}", sessionId, playerId);
    }

    /**
     * 注册新会话
     * 如果是注册用户且已有会话，则踢出旧会话
     *
     * @param sessionId WebSocket会话ID
     * @param playerId 玩家ID
     * @param playerName 玩家名称
     * @param isRegisteredUser 是否为注册用户
     * @param roomCode 当前所在房间（可能为null）
     * @return 旧会话信息（如果有被踢出的）
     */
    public Optional<SessionInfo> registerSession(String sessionId, String playerId, String playerName,
                                                  boolean isRegisteredUser, String roomCode) {
        SessionInfo newSession = SessionInfo.builder()
                .sessionId(sessionId)
                .playerId(playerId)
                .playerName(playerName)
                .isRegisteredUser(isRegisteredUser)
                .loginTime(LocalDateTime.now())
                .lastHeartbeat(LocalDateTime.now())
                .roomCode(roomCode)
                .build();

        // 游客不需要管理会话（每次playerId都不同）
        if (!isRegisteredUser) {
            log.info("游客会话不进行重复登录检测: playerId={}, sessionId={}", playerId, sessionId);
            sessionToPlayer.put(sessionId, playerId);
            return Optional.empty();
        }

        // 注册用户：检查是否已有会话
        SessionInfo oldSession = activeSessions.get(playerId);
        if (oldSession != null) {
            log.warn("检测到重复登录，踢出旧会话: playerId={}, oldSessionId={}, newSessionId={}",
                     playerId, oldSession.getSessionId(), sessionId);

            // 向旧会话推送踢出通知
            kickSession(oldSession.getSessionId(), "您的账号已在其他设备登录");

            // 清理旧会话映射
            sessionToPlayer.remove(oldSession.getSessionId());
        }

        // 注册新会话
        activeSessions.put(playerId, newSession);
        sessionToPlayer.put(sessionId, playerId);

        log.info("注册新会话: playerId={}, sessionId={}, hasOldSession={}",
                 playerId, sessionId, oldSession != null);

        return Optional.ofNullable(oldSession);
    }

    /**
     * 移除会话（断开连接时调用）
     *
     * @param sessionId WebSocket会话ID
     */
    public void removeSession(String sessionId) {
        String playerId = sessionToPlayer.remove(sessionId);
        if (playerId != null) {
            SessionInfo session = activeSessions.remove(playerId);
            if (session != null) {
                log.info("移除会话: playerId={}, sessionId={}", playerId, sessionId);
            }
        }
    }

    /**
     * 更新心跳时间
     *
     * @param sessionId WebSocket会话ID
     */
    public void updateHeartbeat(String sessionId) {
        String playerId = sessionToPlayer.get(sessionId);
        if (playerId != null) {
            SessionInfo session = activeSessions.get(playerId);
            if (session != null) {
                session.setLastHeartbeat(LocalDateTime.now());
            }
        }
    }

    /**
     * 检查玩家是否在线
     *
     * @param playerId 玩家ID
     * @return 是否在线
     */
    public boolean isPlayerOnline(String playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * 获取玩家会话信息
     *
     * @param playerId 玩家ID
     * @return 会话信息
     */
    public Optional<SessionInfo> getSession(String playerId) {
        return Optional.ofNullable(activeSessions.get(playerId));
    }

    /**
     * 踢出指定会话
     *
     * @param sessionId 要踢出的会话ID
     * @param reason 踢出原因
     */
    public void kickSession(String sessionId, String reason) {
        String playerId = sessionToPlayer.get(sessionId);
        if (playerId == null) {
            log.warn("尝试踢出不存在的会话: sessionId={}", sessionId);
            return;
        }

        // 推送踢出通知到指定playerId的topic（与前端订阅路径一致）
        try {
            String destination = "/topic/player/" + playerId + "/kicked";
            messagingTemplate.convertAndSend(
                destination,
                Map.of(
                    "reason", reason,
                    "timestamp", LocalDateTime.now().toString()
                )
            );
            log.info("已推送踢出通知: destination={}, playerId={}, sessionId={}, reason={}",
                     destination, playerId, sessionId, reason);
        } catch (Exception e) {
            log.error("推送踢出通知失败: playerId={}, sessionId={}", playerId, sessionId, e);
        }
    }

    /**
     * 获取在线会话数量
     */
    public int getOnlineCount() {
        return activeSessions.size();
    }

    /**
     * 清理所有会话（用于测试或重置）
     */
    public void clearAll() {
        activeSessions.clear();
        sessionToPlayer.clear();
        log.info("已清理所有会话");
    }

    /**
     * 定时清理过期会话
     */
    @Scheduled(fixedDelay = 300000) // 5分钟
    public void cleanupStaleSessions() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(60);
            List<String> toRemove = new ArrayList<>();

            activeSessions.forEach((playerId, session) -> {
                if (session.getLastHeartbeat().isBefore(threshold)) {
                    toRemove.add(playerId);
                }
            });

            // 清理过期会话
            if (!toRemove.isEmpty()) {
                toRemove.forEach(playerId -> {
                    SessionInfo session = activeSessions.remove(playerId);
                    if (session != null) {
                        sessionToPlayer.remove(session.getSessionId());
                        log.info("🧹 清理过期会话: playerId={}, sessionId={}, loginTime={}",
                                playerId, session.getSessionId(), session.getLoginTime());
                    }
                });

                log.info("🧹 定时清理完成: 移除{}个过期会话，剩余{}个在线会话",
                        toRemove.size(), activeSessions.size());
            }
        } catch (Exception e) {
            log.error(" 定时清理过期会话失败", e);
        }
    }

    /**
     * 获取会话统计信息（用于监控）
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "activeSessionsCount", activeSessions.size(),
                "sessionMappingsCount", sessionToPlayer.size()
        );
    }
}
