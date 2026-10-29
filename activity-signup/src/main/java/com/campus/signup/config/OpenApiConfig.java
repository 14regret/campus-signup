package com.campus.signup.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 元信息。
 *
 * <p>注册一个 HTTP Bearer 的安全方案，Swagger UI 右上角才会出现 Authorize 按钮——
 * 否则所有需要登录的接口在文档里都调不通，演示时会很尴尬。
 *
 * <p>访问地址：{@code http://localhost:8080/swagger-ui/index.html}
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI campusOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("校园活动报名系统 API")
                        .version("1.0.0")
                        .description("""
                                校园活动发布、报名与审核接口。

                                **使用方式**：先调 `POST /api/auth/login` 拿到 token，
                                点右上角 Authorize 填入（只填 token，不要带 Bearer 前缀），
                                之后所有需要登录的接口都会自动带上 Authorization 头。

                                **错误约定**：HTTP 状态码 + 统一返回体 `{code, message, data}`，
                                业务码分段：0 成功 / 1xxx 通用与认证 / 2xxx 用户 / 3xxx 活动 / 4xxx 报名。
                                """))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 token")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
