package com.adam.restaurantoperations.common.config;

import java.util.List;

import com.adam.restaurantoperations.auth.config.AuthProperties;
import com.adam.restaurantoperations.auth.security.AuthenticationPathHttpFirewall;
import com.adam.restaurantoperations.auth.security.CookieCsrfFilter;
import com.adam.restaurantoperations.auth.security.JsonAccessDeniedHandler;
import com.adam.restaurantoperations.auth.security.JsonAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.httpFirewall(new AuthenticationPathHttpFirewall());
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CookieCsrfFilter cookieCsrfFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                // Bearer requests are stateless. Cookie-authenticated refresh/logout requests are protected
                // by CookieCsrfFilter's non-simple custom header plus credentialed, explicit-origin CORS.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(cookieCsrfFilter, BearerTokenAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/refresh/",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/logout/")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/tables", "/api/v1/tables/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/reservations", "/api/v1/reservations/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/menu", "/api/v1/menu/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("ADMIN")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").denyAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        var authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend-origin}") String frontendOrigin) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                AuthProperties.CSRF_HEADER_NAME));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Configuration
    @Profile("dev")
    static class DevelopmentOpenApiSecurityConfig {

        @Bean
        @Order(1)
        SecurityFilterChain developmentSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }
}
