package org.example.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI boiluneOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Boilune API 文档")
                        .description("博弈论多人实时答题游戏平台 - RESTful API 接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Boilune Team")
                                .url("https://github.com/zxlzzz/a-piece-of-shit-based-on-Claude-4.5-from-a-fucking-Chinese-sophomore-loser"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发服务器"),
                        new Server()
                                .url("http://localhost:8080")
                                .description("生产环境服务器 (待配置)")
                ))
                // 添加 JWT 认证配置
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("输入你的 JWT Token (无需添加 'Bearer ' 前缀)")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
