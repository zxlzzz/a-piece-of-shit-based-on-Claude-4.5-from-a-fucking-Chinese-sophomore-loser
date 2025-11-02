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
        // 🔥 优化心跳间隔，减少不必要的心跳消息（从10秒改为25秒）
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                .setTaskScheduler(taskScheduler())
                .setHeartbeatValue(new long[]{25000, 25000});

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

            switch (accessor.getCommand()) {
                case CONNECT:
                    // 连接时的处理
                    String playerId = accessor.getFirstNativeHeader("playerId");
                    if (playerId != null) {
                        accessor.setUser(new StompPrincipal(playerId));
                        log.info("WebSocket连接建立，playerId: {}", playerId);
                    }
                    break;

                case DISCONNECT:
                    // 断开连接时的处理
                    Principal user = accessor.getUser();
                    if (user != null) {
                        log.info("WebSocket连接断开，playerId: {}", user.getName());
                        // 这里可以添加清理逻辑，比如从房间中移除玩家
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
