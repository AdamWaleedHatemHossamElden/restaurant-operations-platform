package com.adam.restaurantoperations.auth.security;

import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

public final class RequiredAccessTokenClaimSetConverter
        implements Converter<Map<String, Object>, Map<String, Object>> {

    private static final Set<String> REQUIRED_CLAIMS = Set.of(
            "sub", "jti", "token_type", "roles", "iat", "exp");

    private final Converter<Map<String, Object>, Map<String, Object>> delegate =
            MappedJwtClaimSetConverter.withDefaults(Map.of());

    @Override
    public Map<String, Object> convert(Map<String, Object> claims) {
        if (!claims.keySet().containsAll(REQUIRED_CLAIMS)) {
            throw new BadJwtException("Required access-token claims are missing");
        }
        return delegate.convert(claims);
    }
}
