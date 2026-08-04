package com.adam.restaurantoperations.auth.web;

import com.adam.restaurantoperations.auth.config.AuthProperties;
import com.adam.restaurantoperations.auth.dto.AuthResponse;
import com.adam.restaurantoperations.auth.dto.CurrentUserResponse;
import com.adam.restaurantoperations.auth.dto.LoginRequest;
import com.adam.restaurantoperations.auth.service.AuthSession;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.common.config.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final RefreshCookieService cookieService;

    public AuthenticationController(
            AuthenticationService authenticationService,
            RefreshCookieService cookieService) {
        this.authenticationService = authenticationService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest) {
        AuthSession session = authenticationService.login(request, RequestMetadata.from(servletRequest));
        return sessionResponse(session);
    }

    @PostMapping({"/refresh", "/refresh/"})
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = AuthProperties.REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        AuthSession session = authenticationService.refresh(
                refreshToken,
                RequestMetadata.from(servletRequest));
        return sessionResponse(session);
    }

    @PostMapping({"/logout", "/logout/"})
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthProperties.REFRESH_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest servletRequest) {
        authenticationService.logout(refreshToken, RequestMetadata.from(servletRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.clear().toString())
                .build();
    }

    @GetMapping("/me")
    @Operation(security = @SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTHENTICATION))
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.currentUser(jwt);
    }

    private ResponseEntity<AuthResponse> sessionResponse(AuthSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.create(session.refreshToken()).toString())
                .body(session.response());
    }
}
