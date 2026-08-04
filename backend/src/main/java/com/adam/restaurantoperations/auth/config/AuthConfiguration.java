package com.adam.restaurantoperations.auth.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;

import javax.crypto.spec.SecretKeySpec;

import com.adam.restaurantoperations.auth.security.AccessTokenClaimsValidator;
import com.adam.restaurantoperations.auth.security.RequiredAccessTokenClaimSetConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    JwtEncoder jwtEncoder(AuthProperties properties) {
        var key = new SecretKeySpec(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        return NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(AuthProperties properties) {
        var key = new SecretKeySpec(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setClaimSetConverter(new RequiredAccessTokenClaimSetConverter());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new AccessTokenClaimsValidator()));
        return decoder;
    }
}
