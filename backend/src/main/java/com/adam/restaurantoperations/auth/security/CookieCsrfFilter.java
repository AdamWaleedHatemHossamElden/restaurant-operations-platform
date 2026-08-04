package com.adam.restaurantoperations.auth.security;

import java.io.IOException;
import com.adam.restaurantoperations.auth.config.AuthProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CookieCsrfFilter extends OncePerRequestFilter {

    private static final RequestMatcher PROTECTED_ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/refresh/"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/logout/"));

    private final SecurityErrorWriter errorWriter;

    public CookieCsrfFilter(SecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (PROTECTED_ENDPOINTS.matches(request) && !AuthProperties.CSRF_HEADER_VALUE.equals(
                request.getHeader(AuthProperties.CSRF_HEADER_NAME))) {
            errorWriter.write(
                    response,
                    HttpStatus.FORBIDDEN,
                    "Required CSRF protection header is missing",
                    request.getRequestURI());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
