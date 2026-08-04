package com.adam.restaurantoperations.auth.config;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import com.adam.restaurantoperations.auth.security.JwtService;
import com.adam.restaurantoperations.users.UserEntity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidationTest {

    private static final Instant NOW = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

    private JwtEncoder encoder;

    private JwtDecoder decoder;

    private AuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties(
                "test-only-phase-2a-signing-key-at-least-32-bytes-long",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                false,
                "Lax",
                null,
                null,
                null);
        var configuration = new AuthConfiguration();
        encoder = configuration.jwtEncoder(properties);
        decoder = configuration.jwtDecoder(properties);
    }

    @Test
    void issuedAccessTokenContainsRequiredClaimsAndDocumentedLifetime() {
        UserEntity user = new UserEntity("admin@example.com", "unused", "Admin");
        ReflectionTestUtils.setField(user, "id", 7L);
        JwtService service = new JwtService(
                encoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        var decoded = decoder.decode(service.issue(user).value());

        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getId()).isNotBlank();
        assertThat(decoded.getClaimAsString("token_type")).isEqualTo("access");
        assertThat(decoded.getClaimAsStringList("roles")).isEmpty();
        assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).toSeconds())
                .isEqualTo(900);
    }

    @Test
    void actuallyExpiredTokenIsRejectedByConfiguredDecoder() {
        String token = token(encoder, "access", null, NOW.minusSeconds(1200), NOW.minusSeconds(300));

        assertRejected(token);
    }

    @Test
    void missingAndInvalidTokenTypeAreRejected() {
        assertRejected(token(encoder, null, null, NOW.minusSeconds(30), NOW.plusSeconds(870)));
        assertRejected(token(encoder, "refresh", null, NOW.minusSeconds(30), NOW.plusSeconds(870)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "jti", "roles", "iat", "exp"})
    void missingRequiredClaimIsRejected(String missingClaim) {
        String encoded = "iat".equals(missingClaim)
                ? tokenWithoutIssuedAt()
                : token(encoder, "access", missingClaim, NOW.minusSeconds(30), NOW.plusSeconds(870));
        assertRejected(encoded);
    }

    @Test
    void invalidSignatureIsRejected() {
        byte[] otherSecret = "different-test-signing-key-with-at-least-32-bytes".getBytes(StandardCharsets.UTF_8);
        var otherEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherSecret, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256)
                .build();

        assertRejected(token(otherEncoder, "access", null, NOW.minusSeconds(30), NOW.plusSeconds(870)));
    }

    @Test
    void opaqueRefreshTokenIsNeverAcceptedAsBearerAccessToken() {
        assertRejected("opaque-refresh-token-value");
    }

    private String token(
            JwtEncoder tokenEncoder,
            String tokenType,
            String missingClaim,
            Instant issuedAt,
            Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        if (!"sub".equals(missingClaim)) {
            claims.subject("7");
        }
        if (!"jti".equals(missingClaim)) {
            claims.id("test-token-id");
        }
        if (!"roles".equals(missingClaim)) {
            claims.claim("roles", List.of("ADMIN"));
        }
        if (!"iat".equals(missingClaim)) {
            claims.issuedAt(issuedAt);
        }
        if (!"exp".equals(missingClaim)) {
            claims.expiresAt(expiresAt);
        }
        if (tokenType != null) {
            claims.claim("token_type", tokenType);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return tokenEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String tokenWithoutIssuedAt() {
        var claims = new JWTClaimsSet.Builder()
                .subject("7")
                .jwtID("test-token-id")
                .claim("roles", List.of("ADMIN"))
                .claim("token_type", "access")
                .expirationTime(Date.from(NOW.plusSeconds(870)))
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(properties.jwtSecret().getBytes(StandardCharsets.UTF_8)));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not create test token", exception);
        }
        return jwt.serialize();
    }
}
