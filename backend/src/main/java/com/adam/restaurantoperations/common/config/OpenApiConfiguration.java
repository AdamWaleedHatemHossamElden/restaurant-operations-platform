package com.adam.restaurantoperations.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("dev")
public class OpenApiConfiguration {

    public static final String BEARER_AUTHENTICATION = "bearerAuth";

    @Bean
    OpenAPI restaurantOperationsOpenApi() {
        return new OpenAPI().components(new Components().addSecuritySchemes(
                BEARER_AUTHENTICATION,
                new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
