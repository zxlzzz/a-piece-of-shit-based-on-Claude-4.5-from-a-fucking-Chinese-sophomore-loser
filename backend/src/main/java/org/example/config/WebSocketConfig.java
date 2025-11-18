package org.example.config;

import org.example.entity.PlayerEntity;
import org.example.repository.PlayerRepository;
import org.example.service.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;
import java.util.Optional;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    private final SessionManager sessionManager;
    private final PlayerRepository playerRepository;

    // 🔥 手动构造器，使用 @Lazy 打破循环依赖
    // SessionManager 需要 SimpMessagingTemplate，而 SimpMessagingTemplate 由 WebSocket 配置创建
    public WebSocketConfig(@Lazy SessionManager sessionManager, PlayerRepository playerRepository) {
        this.sessionManager = sessionManager;
        this.playerRepository = playerRepository;
    }

    // 🔥 先定义 TaskScheduler bean
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 应用程序消息前缀
        registry.setApplicationDestinationPrefixes("/app");

        // 🔥 启用简单消息代理，支持主题和队列
        // ⚠️ 禁用心跳检测：玩家答题时可能长时间无操作，心跳会导致误判断连
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                .setTaskScheduler(taskScheduler());

        // 用户目标消息前缀
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketChannelInterceptor(sessionManager, playerRepository));

        // 🔥 大幅增加队列容量和线程池，防止消息队列满导致断连
        // 这是防止 "Failed to send message to ExecutorSubscribableChannel" 错误的关键
        registration.taskExecutor()
                .corePoolSize(32)       // 🔥 从 8 增加到 32
                .maxPoolSize(64)        // 🔥 从 16 增加到 64
                .queueCapacity(50000);  // 🔥 从 1000 增加到 50000 - 最关键！
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 🔥 出站通道也需要大容量，防止广播消息时队列满
        registration.taskExecutor()
                .corePoolSize(32)       // 🔥 从 8 增加到 32
                .maxPoolSize(64)        // 🔥 从 16 增加到 64
                .queueCapacity(50000);  // 🔥 从 1000 增加到 50000
    }

    // WebSocket通道拦截器，用于处理连接和断开事件
    public static class WebSocketChannelInterceptor implements ChannelInterceptor {

        public static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

        private final SessionManager sessionManager;
        private final PlayerRepository playerRepository;

        public WebSocketChannelInterceptor(SessionManager sessionManager, PlayerRepository playerRepository) {
            this.sessionManager = sessionManager;
            this.playerRepository = playerRepository;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

            // 🔥 核心修复：对所有消息类型，如果user为null，从session恢复
            // 这确保了Spring的SimpUserRegistry能正确维护用户和session的映射
            if (accessor.getUser() == null && accessor.getSessionAttributes() != null) {
                String sessionPlayerId = (String) accessor.getSessionAttributes().get("playerId");
                if (sessionPlayerId != null) {
                    StompPrincipal restoredPrincipal = new StompPrincipal(sessionPlayerId);
                    accessor.setUser(restoredPrincipal);
                }
            }

            switch (accessor.getCommand()) {
                case CONNECT:
                    // 连接时的处理
                    String playerId = accessor.getFirstNativeHeader("playerId");
                    if (playerId != null) {
                        // 🔥 创建Principal并设置到accessor
                        StompPrincipal principal = new StompPrincipal(playerId);
                        accessor.setUser(principal);

                        // 🔥 关键修复：将playerId保存到session attributes，确保后续消息能获取到用户身份
                        if (accessor.getSessionAttributes() != null) {
                            accessor.getSessionAttributes().put("playerId", playerId);

                            // 🔥 异步查询玩家信息并注册会话（避免阻塞连接建立）
                            String sessionId = accessor.getSessionId();
                            new Thread(() -> {
                                try {
                                    Optional<PlayerEntity> playerOpt = playerRepository.findByPlayerId(playerId);
                                    if (playerOpt.isPresent()) {
                                        PlayerEntity player = playerOpt.get();
                                        String playerName = player.getName();
                                        boolean isRegisteredUser = player.getUsername() != null;
                                        String roomCode = player.getRoom() != null ? player.getRoom().getRoomCode() : null;

                                        // 🔥 注册会话，检测重复登录（仅注册用户）
                                        if (isRegisteredUser) {
                                            sessionManager.registerSession(
                                                sessionId,
                                                playerId,
                                                playerName,
                                                true,
                                                roomCode
                                            );
                                        }

                                        log.info("🔌 WebSocket连接建立: playerId={}, name={}, sessionId={}, isRegistered={}, roomCode={}",
                                            playerId, playerName, sessionId, isRegisteredUser, roomCode);
                                    } else {
                                        log.warn("⚠️ WebSocket连接但未找到玩家（可能已删除）: playerId={}", playerId);
                                    }
                                } catch (Exception e) {
                                    log.error("⚠️ 处理WebSocket连接失败: playerId={}, sessionId={}", playerId, sessionId, e);
                                }
                            }).start();
                        } else {
                            log.warn("⚠️ Session attributes为null，无法保存playerId");
                        }
                    } else {
                        log.warn("⚠️ WebSocket连接但未提供playerId header！sessionId: {}",
                            accessor.getSessionId());
                    }
                    break;

                case DISCONNECT:
                    // 断开连接时的处理
                    Principal user = accessor.getUser();
                    String sessionId = accessor.getSessionId();
                    if (user != null) {
                        log.info("🔌 WebSocket连接断开: playerId={}, sessionId={}", user.getName(), sessionId);
                    }
                    // 🔥 清理会话
                    if (sessionId != null) {
                        sessionManager.removeSession(sessionId);
                    }
                    break;

                case SUBSCRIBE:
                    // 订阅时的处理
                    break;

                default:
                    break;
            }

            return message;
        }
    }

    // 简单的Principal实现，用于标识WebSocket用户
    public static class StompPrincipal implements Principal {
        private final String name;

        public StompPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
