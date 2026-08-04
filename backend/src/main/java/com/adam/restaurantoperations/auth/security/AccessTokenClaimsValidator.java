package com.adam.restaurantoperations.auth.security;

import java.util.Collection;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCESS_TOKEN = new OAuth2Error(
            "invalid_token",
            "Required access-token claims are missing or invalid",
            null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object roles = jwt.getClaims().get("roles");
        boolean valid = jwt.getClaims().containsKey("sub")
                && hasText(jwt.getSubject())
                && jwt.getClaims().containsKey("jti")
                && hasText(jwt.getId())
                && jwt.getClaims().containsKey("token_type")
                && "access".equals(jwt.getClaimAsString("token_type"))
                && jwt.getClaims().containsKey("roles")
                && roles instanceof Collection<?>
                && jwt.getClaims().containsKey("iat")
                && jwt.getIssuedAt() != null
                && jwt.getClaims().containsKey("exp")
                && jwt.getExpiresAt() != null
                && jwt.getIssuedAt().isBefore(jwt.getExpiresAt());
        return valid
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_ACCESS_TOKEN);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
