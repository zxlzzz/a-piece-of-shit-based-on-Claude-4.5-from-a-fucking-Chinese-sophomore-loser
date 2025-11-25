package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods}")
    private String allowedMethods;

    @Value("${cors.max-age}")
    private Long maxAge;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");

        registry.addMapping("/**")
                .allowedOriginPatterns(origins)  // 使用具体的域名列表
                .allowedMethods(allowedMethods.split(","))
                .allowedHeaders("*")
                .exposedHeaders("X-Total-Count", "X-Page-Number")  // ✅ 如果需要暴露自定义响应头
                .allowCredentials(true)
                .maxAge(maxAge);
    }
}