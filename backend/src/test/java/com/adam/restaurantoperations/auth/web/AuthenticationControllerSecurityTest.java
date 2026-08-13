package com.adam.restaurantoperations.auth.web;

import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.KitchenAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.dto.AuthResponse;
import com.adam.restaurantoperations.auth.dto.CurrentUserResponse;
import com.adam.restaurantoperations.auth.dto.LoginRequest;
import com.adam.restaurantoperations.auth.service.AuthException;
import com.adam.restaurantoperations.auth.service.AuthSession;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.menu.MenuService;
import com.adam.restaurantoperations.kitchen.KitchenService;
import com.adam.restaurantoperations.orders.OrderService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.reservations.ReservationService;
import com.adam.restaurantoperations.tables.RestaurantTableService;
import com.adam.restaurantoperations.testsupport.MockInventoryBeans;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockInventoryBeans
class AuthenticationControllerSecurityTest {

    private static final CurrentUserResponse USER =
            new CurrentUserResponse(7L, "admin@example.com", "Admin", true, List.of("ADMIN"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private AuthenticationAuditService authenticationAuditService;

    @MockitoBean
    private RestaurantTableService restaurantTableService;

    @MockitoBean
    private TableAuditService tableAuditService;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private ReservationAuditService reservationAuditService;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private MenuAuditService menuAuditService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderAuditService orderAuditService;

    @MockitoBean
    private KitchenService kitchenService;

    @MockitoBean
    private KitchenAuditService kitchenAuditService;

    @Test
    void loginReturnsAccessTokenAndHttpOnlyCookieWithoutRefreshTokenInJson() throws Exception {
        given(authenticationService.login(any(LoginRequest.class), any(RequestMetadata.class)))
                .willReturn(session("access-value", "refresh-value"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refresh_token=refresh-value"),
                        org.hamcrest.Matchers.containsString("HttpOnly"),
                        org.hamcrest.Matchers.containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.accessToken").value("access-value"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"));
    }

    @Test
    void invalidCredentialsUseSameGenericResponseAndMissingFieldsAreBadRequest() throws Exception {
        given(authenticationService.login(any(LoginRequest.class), any(RequestMetadata.class)))
                .willThrow(AuthException.invalidCredentials());

        for (String email : List.of("admin@example.com", "unknown@example.com")) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid email or password"));
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void malformedLoginJsonReturnsSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@example.com\",\"password\":not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    void disabledAccountIsForbiddenJson() throws Exception {
        given(authenticationService.login(any(LoginRequest.class), any(RequestMetadata.class)))
                .willThrow(AuthException.disabledAccount());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"disabled@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is disabled"));
    }

    @Test
    void meRequiresValidJwtAndReturnsSafeUser() throws Exception {
        given(authenticationService.currentUser(any())).willReturn(USER);

        mockMvc.perform(get("/api/v1/auth/me").with(jwt().jwt(token -> token
                                .subject("7")
                                .issuedAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(60))
                                .claim("roles", List.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void cookieEndpointsRequireCsrfHeaderAcrossMvcPathVariants() throws Exception {
        given(authenticationService.refresh(eq("valid-refresh"), any(RequestMetadata.class)))
                .willReturn(session("access-value", "replacement-refresh"));
        String[] endpoints = {
            "/api/v1/auth/refresh",
            "/api/v1/auth/refresh/",
            "/api/v1/auth/refresh;probe",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout/",
            "/api/v1/auth/logout;probe"
        };

        for (String endpoint : endpoints) {
            assertCsrfProtection(endpoint, false);
            assertCsrfProtection(endpoint, true);
        }
    }

    @Test
    void corsIsCredentialedAndLimitedAndUnrelatedApiIsDenied() throws Exception {
        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-Protection"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("X-CSRF-Protection")));

        mockMvc.perform(options("/api/v1/payments/orders/1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsString("Idempotency-Key")));

        mockMvc.perform(options("/api/v1/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-CSRF-Protection"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/unrelated"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(post("/api/v1/unrelated;probe"))
                .andExpect(status().isBadRequest());
    }

    private AuthSession session(String accessToken, String refreshToken) {
        return new AuthSession(new AuthResponse(accessToken, "Bearer", 900, USER), refreshToken);
    }

    private void assertCsrfProtection(String endpoint, boolean withContextPath) throws Exception {
        MockHttpServletRequestBuilder withoutHeader = postForEndpoint(endpoint, withContextPath)
                .cookie(new Cookie("refresh_token", "valid-refresh"));
        mockMvc.perform(withoutHeader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        MockHttpServletRequestBuilder incorrectHeader = postForEndpoint(endpoint, withContextPath)
                .cookie(new Cookie("refresh_token", "valid-refresh"))
                .header("X-CSRF-Protection", "incorrect");
        mockMvc.perform(incorrectHeader)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        MockHttpServletRequestBuilder withHeader = postForEndpoint(endpoint, withContextPath)
                .cookie(new Cookie("refresh_token", "valid-refresh"))
                .header("X-CSRF-Protection", "1");
        mockMvc.perform(withHeader)
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder postForEndpoint(String endpoint, boolean withContextPath) {
        String contextPath = withContextPath ? "/application" : "";
        int matrixParameterStart = endpoint.indexOf(';');
        String builderEndpoint = matrixParameterStart >= 0
                ? endpoint.substring(0, matrixParameterStart)
                : endpoint;
        MockHttpServletRequestBuilder request = post(contextPath + builderEndpoint);
        if (withContextPath) {
            request.contextPath(contextPath);
        }
        if (matrixParameterStart >= 0) {
            request.with(mockRequest -> {
                mockRequest.setRequestURI(contextPath + endpoint);
                mockRequest.setServletPath(endpoint);
                return mockRequest;
            });
        }
        return request;
    }
}
