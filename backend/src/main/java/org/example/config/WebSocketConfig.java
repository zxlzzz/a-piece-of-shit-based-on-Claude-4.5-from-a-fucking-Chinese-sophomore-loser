package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.*;

import java.security.Principal;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

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

        // 启用简单消息代理，支持主题和队列
        // 🔥 禁用心跳检测 - 本地开发环境不需要，避免答题时因无操作超时断连
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                .setTaskScheduler(taskScheduler())
                .setHeartbeatValue(new long[]{0, 0});

        // 用户目标消息前缀
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new WebSocketChannelInterceptor());

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
    @Component
    public static class WebSocketChannelInterceptor implements ChannelInterceptor {

        public static final Logger log = LoggerFactory.getLogger(WebSocketChannelInterceptor.class);

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
                            log.info("🔌 WebSocket连接建立，playerId: {}, sessionId: {}, 已保存到session",
                                playerId, accessor.getSessionId());
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
                    if (user != null) {
                        log.info("🔌 WebSocket连接断开，playerId: {}", user.getName());
                    }
                    break;

                case SUBSCRIBE:
                    // 订阅时的处理 - 记录日志
                    Principal subUser = accessor.getUser();
                    String destination = accessor.getDestination();
                    String sessionId = accessor.getSessionId();

                    log.info("📡 WebSocket订阅: sessionId={}, user={}, destination={}",
                        sessionId,
                        subUser != null ? subUser.getName() : "null",
                        destination);

                    // 🔥 如果订阅的是私聊频道，额外记录
                    if (destination != null && destination.contains("/user/queue/private")) {
                        log.info("   ⭐ 私聊频道订阅成功！playerId={}, 该用户将能收到发送给此ID的私聊消息",
                            subUser != null ? subUser.getName() : "unknown");
                    }
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
