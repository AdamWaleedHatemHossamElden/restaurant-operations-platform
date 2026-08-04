package com.adam.restaurantoperations.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.auth.config.AuthProperties;
import com.adam.restaurantoperations.auth.dto.LoginRequest;
import com.adam.restaurantoperations.auth.persistence.RefreshTokenRepository;
import com.adam.restaurantoperations.auth.security.JwtService;
import com.adam.restaurantoperations.auth.security.RefreshTokenGenerator;
import com.adam.restaurantoperations.auth.security.RefreshTokenHasher;
import com.adam.restaurantoperations.users.EmailNormalizer;
import com.adam.restaurantoperations.users.UserEntity;
import com.adam.restaurantoperations.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final RequestMetadata METADATA = new RequestMetadata("127.0.0.1", "test-agent");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailNormalizer emailNormalizer;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private RefreshTokenHasher refreshTokenHasher;

    @Mock
    private AuthenticationAuditService auditService;

    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        var properties = new AuthProperties(
                "unit-test-jwt-secret-with-at-least-32-bytes",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                false,
                "Lax",
                null,
                null,
                null);
        service = new AuthenticationService(
                userRepository,
                refreshTokenRepository,
                passwordEncoder,
                emailNormalizer,
                jwtService,
                refreshTokenGenerator,
                refreshTokenHasher,
                auditService,
                properties,
                Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void unknownUserPerformsOneBcryptComparisonAgainstFixedDummyHash() {
        LoginRequest request = new LoginRequest("unknown@example.com", "candidate-password");
        given(emailNormalizer.normalize(request.email())).willReturn(request.email());
        given(userRepository.findWithRolesByEmail(request.email())).willReturn(Optional.empty());
        given(passwordEncoder.matches(
                eq(request.password()),
                argThat(hash -> hash.startsWith("$2a$12$") && hash.length() == 60)))
                .willReturn(false);

        assertThatThrownBy(() -> service.login(request, METADATA))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches(
                eq(request.password()),
                argThat(hash -> hash.startsWith("$2a$12$") && hash.length() == 60));
        verify(auditService).record(
                "LOGIN_FAILURE",
                null,
                METADATA.ipAddress(),
                Map.of("reason", "INVALID_CREDENTIALS"));
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrongPasswordPerformsOneComparisonAgainstStoredHashWithSameFailureBehavior() {
        LoginRequest request = new LoginRequest("known@example.com", "candidate-password");
        UserEntity user = new UserEntity(request.email(), "stored-password-hash", "Known User");
        given(emailNormalizer.normalize(request.email())).willReturn(request.email());
        given(userRepository.findWithRolesByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPasswordHash())).willReturn(false);

        assertThatThrownBy(() -> service.login(request, METADATA))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches(request.password(), user.getPasswordHash());
        verify(auditService).record(
                "LOGIN_FAILURE",
                null,
                METADATA.ipAddress(),
                Map.of("reason", "INVALID_CREDENTIALS"));
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(refreshTokenRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
