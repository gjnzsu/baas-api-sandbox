package com.example.baas.sandbox.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paymentSandboxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("BaaS Payment API Sandbox")
                        .version("0.1.0")
                        .description("A2A payment sandbox with deterministic failure simulation."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("sandbox-token")));
    }
}
