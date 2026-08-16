package com.adam.restaurantoperations.common.config;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionProfileConfigurationTest {

    @Test
    void productionProfileRequiresSecretsAndExcludesDevelopmentSurfaces() throws IOException {
        var source = new YamlPropertySourceLoader()
                .load("production", new ClassPathResource("application-prod.yml"))
                .getFirst();

        assertThat(source.getProperty("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(source.getProperty("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(source.getProperty("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(source.getProperty("app.frontend-origin")).isEqualTo("${FRONTEND_ORIGIN}");
        assertThat(source.getProperty("app.auth.jwt-secret")).isEqualTo("${JWT_SECRET}");
        assertThat(source.getProperty("app.auth.cookie-secure")).isEqualTo(true);
        assertThat(source.getProperty("app.auth.bootstrap-email")).isEqualTo("");
        assertThat(source.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(source.getProperty("spring.flyway.locations")).isNotEqualTo("classpath:db/dev");
        assertThat(source.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(source.getProperty("management.endpoint.health.probes.enabled")).isEqualTo(true);
        assertThat(source.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(source.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
        assertThat(source.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
        assertThat(source.getProperty("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(source.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }
}
