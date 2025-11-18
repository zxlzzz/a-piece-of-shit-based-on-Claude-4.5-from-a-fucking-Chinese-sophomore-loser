package org.example.config;

import org.example.entity.PlayerEntity;
import org.example.repository.PlayerRepository;
import org.example.service.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    private final SessionManager sessionManager;
    private final PlayerRepository playerRepository;
    private final Executor wsConnectionExecutor;

    // 🔥 手动构造器，使用 @Lazy 打破循环依赖
    // SessionManager 需要 SimpMessagingTemplate，而 SimpMessagingTemplate 由 WebSocket 配置创建
    // 🔥 修复：添加@Qualifier指定使用wsConnectionExecutor这个bean，避免依赖注入歧义
    public WebSocketConfig(@Lazy SessionManager sessionManager,
                          PlayerRepository playerRepository,
                          @Qualifier("wsConnectionExecutor") Executor wsConnectionExecutor) {
        this.sessionManager = sessionManager;
        this.playerRepository = playerRepository;
        this.wsConnectionExecutor = wsConnectionExecutor;
    }

    // 🔥 先定义 TaskScheduler bean（用于心跳检测，虽然已禁用但仍需配置）
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    // 🔥 WebSocket 连接处理专用线程池
    // 避免每次连接都创建新线程，提高并发能力，防止线程泄漏
    @Bean(name = "wsConnectionExecutor")
    public Executor wsConnectionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);           // 核心线程数
        executor.setMaxPoolSize(50);            // 最大线程数
        executor.setQueueCapacity(500);         // 队列容量
        executor.setThreadNamePrefix("ws-conn-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
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
        registration.interceptors(new WebSocketChannelInterceptor(sessionManager, playerRepository, wsConnectionExecutor));

        // 🔥 配置消息处理线程池和队列
        // 🔥 修复P2-2：队列容量从50000降到5000，避免OOM风险
        // 如果队列接近满载，说明服务器处理能力不足，应该拒绝而不是无限堆积
        registration.taskExecutor()
                .corePoolSize(32)       // 核心线程数：32
                .maxPoolSize(64)        // 最大线程数：64
                .queueCapacity(5000)    // 队列容量：5000（从50000降低，避免内存溢出）
                .keepAliveSeconds(300); // 非核心线程空闲300秒后回收
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 🔥 出站通道配置
        // 🔥 修复P2-2：队列容量从50000降到5000
        registration.taskExecutor()
                .corePoolSize(32)       // 核心线程数：32
                .maxPoolSize(64)        // 最大线程数：64
                .queueCapacity(5000)    // 队列容量：5000
                .keepAliveSeconds(300); // 非核心线程空闲300秒后回收
    }

    // WebSocket通道拦截器，用于处理连接和断开事件
    public static class WebSocketChannelInterceptor implements ChannelInterceptor {

        public static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

        private final SessionManager sessionManager;
        private final PlayerRepository playerRepository;
        private final Executor executor;

        public WebSocketChannelInterceptor(SessionManager sessionManager,
                                          PlayerRepository playerRepository,
                                          Executor executor) {
            this.sessionManager = sessionManager;
            this.playerRepository = playerRepository;
            this.executor = executor;
        }

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
            StompCommand command = accessor.getCommand();

            // 🔥 修复P1-6：优化Principal恢复逻辑
            // 只在特定命令类型需要时才恢复，而不是对所有消息都检查
            // CONNECT时已设置Principal，后续的SEND、SUBSCRIBE等命令应该已有Principal
            // 只有在确实需要Principal的命令上才检查和恢复
            if (command != null && command != StompCommand.CONNECT) {
                // 对于非CONNECT命令，如果Principal为null，可能是框架内部丢失
                if (accessor.getUser() == null && accessor.getSessionAttributes() != null) {
                    String sessionPlayerId = (String) accessor.getSessionAttributes().get("playerId");
                    if (sessionPlayerId != null) {
                        StompPrincipal restoredPrincipal = new StompPrincipal(sessionPlayerId);
                        accessor.setUser(restoredPrincipal);
                        // 添加监控：记录需要恢复的情况（调试模式）
                        log.debug("恢复Principal: command={}, playerId={}, sessionId={}",
                                command, sessionPlayerId, accessor.getSessionId());
                    }
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

                            // 🔥 优化：使用线程池异步查询玩家信息并注册会话（避免阻塞连接建立，防止线程泄漏）
                            // 🔥 修复P0-2：确保异步注册失败时能清理状态，避免不一致
                            String sessionId = accessor.getSessionId();
                            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
                            executor.execute(() -> {
                                try {
                                    Optional<PlayerEntity> playerOpt = playerRepository.findByPlayerId(playerId);
                                    if (playerOpt.isPresent()) {
                                        PlayerEntity player = playerOpt.get();
                                        String playerName = player.getName();
                                        boolean isRegisteredUser = player.getUsername() != null;
                                        String roomCode = player.getRoom() != null ? player.getRoom().getRoomCode() : null;

                                        // 🔥 注册会话（注册用户和游客都注册，确保sessionToPlayer映射完整）
                                        sessionManager.registerSession(
                                            sessionId,
                                            playerId,
                                            playerName,
                                            isRegisteredUser,
                                            roomCode
                                        );

                                        log.info("🔌 WebSocket连接建立: playerId={}, name={}, sessionId={}, isRegistered={}, roomCode={}",
                                            playerId, playerName, sessionId, isRegisteredUser, roomCode);
                                    } else {
                                        log.warn("⚠️ WebSocket连接但未找到玩家（可能已删除）: playerId={}", playerId);
                                        // 🔥 修复：玩家不存在时清理sessionAttributes，避免状态不一致
                                        if (sessionAttrs != null) {
                                            sessionAttrs.remove("playerId");
                                            log.info("已清理不存在玩家的sessionAttributes: playerId={}", playerId);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("⚠️ 处理WebSocket连接失败: playerId={}, sessionId={}", playerId, sessionId, e);
                                    // 🔥 修复：异步处理失败时清理sessionAttributes，避免状态不一致
                                    if (sessionAttrs != null) {
                                        sessionAttrs.remove("playerId");
                                        log.info("已清理失败会话的sessionAttributes: playerId={}", playerId);
                                    }
                                }
                            });
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
