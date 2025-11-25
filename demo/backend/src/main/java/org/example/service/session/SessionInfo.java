package org.example.service.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket会话信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionInfo {

    /**
     * WebSocket会话ID
     */
    private String sessionId;

    /**
     * 玩家ID
     */
    private String playerId;

    /**
     * 玩家名称
     */
    private String playerName;

    /**
     * 是否为注册用户（游客为false）
     */
    private boolean isRegisteredUser;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 最后心跳时间
     */
    private LocalDateTime lastHeartbeat;

    /**
     * 所在房间代码（可能为null）
     */
    private String roomCode;
}
