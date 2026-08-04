package com.adam.restaurantoperations.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.auth.config.AuthProperties;
import com.adam.restaurantoperations.auth.dto.AuthResponse;
import com.adam.restaurantoperations.auth.dto.CurrentUserResponse;
import com.adam.restaurantoperations.auth.dto.LoginRequest;
import com.adam.restaurantoperations.auth.persistence.RefreshTokenEntity;
import com.adam.restaurantoperations.auth.persistence.RefreshTokenRepository;
import com.adam.restaurantoperations.auth.security.AccessToken;
import com.adam.restaurantoperations.auth.security.JwtService;
import com.adam.restaurantoperations.auth.security.RefreshTokenGenerator;
import com.adam.restaurantoperations.auth.security.RefreshTokenHasher;
import com.adam.restaurantoperations.users.EmailNormalizer;
import com.adam.restaurantoperations.users.UserEntity;
import com.adam.restaurantoperations.users.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$DhYByo/pNcoj7krOgZKqv.pFYSpF3VFzBKnlFFT3OdfBYdENwu.QW";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final JwtService jwtService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AuthenticationAuditService auditService;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthenticationService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailNormalizer emailNormalizer,
            JwtService jwtService,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            AuthenticationAuditService auditService,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailNormalizer = emailNormalizer;
        this.jwtService = jwtService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenHasher = refreshTokenHasher;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthSession login(LoginRequest request, RequestMetadata metadata) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        UserEntity user = userRepository.findWithRolesByEmail(normalizedEmail).orElse(null);
        String passwordHash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches) {
            auditService.record("LOGIN_FAILURE", null, metadata.ipAddress(), Map.of("reason", "INVALID_CREDENTIALS"));
            throw AuthException.invalidCredentials();
        }
        if (!user.isEnabled()) {
            auditService.record("LOGIN_FAILURE", user.getId(), metadata.ipAddress(), Map.of("reason", "DISABLED"));
            throw AuthException.disabledAccount();
        }
        Instant now = clock.instant();
        user.recordLogin(now);
        String rawRefreshToken = issueRefreshToken(user, UUID.randomUUID().toString(), metadata, now);
        auditService.record("LOGIN_SUCCESS", user.getId(), metadata.ipAddress(), Map.of());
        return session(user, rawRefreshToken);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthSession refresh(String rawToken, RequestMetadata metadata) {
        if (rawToken == null || rawToken.isBlank()) {
            throw AuthException.invalidRefreshToken();
        }
        RefreshTokenEntity current = findTokenForUpdate(rawToken)
                .orElseThrow(AuthException::invalidRefreshToken);
        Instant now = clock.instant();
        if (current.isRevoked()) {
            revokeFamily(current.getFamilyId(), now);
            auditService.record(
                    "REFRESH_TOKEN_REUSE_DETECTED",
                    current.getUser().getId(),
                    metadata.ipAddress(),
                    Map.of("familyId", current.getFamilyId()));
            throw AuthException.invalidRefreshToken();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw AuthException.invalidRefreshToken();
        }
        UserEntity user = current.getUser();
        if (!user.isEnabled()) {
            revokeFamily(current.getFamilyId(), now);
            throw AuthException.disabledAccount();
        }
        String nextRawToken = refreshTokenGenerator.generate();
        RefreshTokenEntity replacement = new RefreshTokenEntity(
                user,
                refreshTokenHasher.hash(nextRawToken),
                current.getFamilyId(),
                now.plus(properties.refreshTokenTtl()),
                truncate(metadata.ipAddress(), 45),
                truncate(metadata.userAgent(), 512));
        refreshTokenRepository.save(replacement);
        current.rotate(now, replacement);
        auditService.record("TOKEN_REFRESH", user.getId(), metadata.ipAddress(), Map.of());
        return session(user, nextRawToken);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void logout(String rawToken, RequestMetadata metadata) {
        Long userId = null;
        if (rawToken != null && !rawToken.isBlank()) {
            var token = findTokenForUpdate(rawToken);
            if (token.isPresent()) {
                userId = token.get().getUser().getId();
                if (!token.get().isRevoked()) {
                    token.get().revoke(clock.instant());
                }
            }
        }
        auditService.record("LOGOUT", userId, metadata.ipAddress(), Map.of());
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw AuthException.invalidAccessToken();
        }
        UserEntity user = userRepository.findWithRolesById(userId)
                .orElseThrow(AuthException::invalidAccessToken);
        if (!user.isEnabled()) {
            throw AuthException.disabledAccount();
        }
        return CurrentUserResponse.from(user);
    }

    private AuthSession session(UserEntity user, String rawRefreshToken) {
        AccessToken accessToken = jwtService.issue(user);
        long expiresIn = properties.accessTokenTtl().toSeconds();
        AuthResponse response = new AuthResponse(
                accessToken.value(),
                "Bearer",
                expiresIn,
                CurrentUserResponse.from(user));
        return new AuthSession(response, rawRefreshToken);
    }

    private String issueRefreshToken(
            UserEntity user,
            String familyId,
            RequestMetadata metadata,
            Instant now) {
        String rawToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(new RefreshTokenEntity(
                user,
                refreshTokenHasher.hash(rawToken),
                familyId,
                now.plus(properties.refreshTokenTtl()),
                truncate(metadata.ipAddress(), 45),
                truncate(metadata.userAgent(), 512)));
        return rawToken;
    }

    private void revokeFamily(String familyId, Instant now) {
        refreshTokenRepository.revokeActiveFamily(familyId, now);
    }

    private Optional<RefreshTokenEntity> findTokenForUpdate(String rawToken) {
        String hash = refreshTokenHasher.hash(rawToken);
        Optional<Long> userId = refreshTokenRepository.findUserIdByTokenHash(hash);
        if (userId.isEmpty() || userRepository.findByIdForUpdate(userId.get()).isEmpty()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHashForUpdate(hash);
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
