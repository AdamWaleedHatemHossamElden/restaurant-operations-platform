package com.adam.restaurantoperations.infrastructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowIT {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("refresh_token=([^;]+)");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("restaurant_operations")
            .withUsername("restaurant_user")
            .withPassword("integration_test_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.auth.jwt-secret", () -> "integration-test-only-jwt-key-with-at-least-32-bytes");
        registry.add("app.auth.access-token-ttl", () -> "15m");
        registry.add("app.auth.refresh-token-ttl", () -> "7d");
        registry.add("app.frontend-origin", () -> "http://localhost:5173");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void seedUserAndRole() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update("INSERT INTO roles (name, description, enabled) VALUES ('ADMIN', 'Admin', TRUE)");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "admin@example.com", passwordEncoder.encode("correct-password"), "Test Admin");
        jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r");
    }

    @Test
    void loginPersistsOnlyHashesUpdatesLastLoginAndIncludesRoleClaim() throws Exception {
        MvcResult login = login("correct-password").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String body = login.getResponse().getContentAsString();
        String accessToken = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
        String refreshToken = refreshCookie(login);
        String storedHash = jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_tokens", String.class);
        String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM users", String.class);

        assertThat(accessToken.split("\\.")).hasSize(3);
        assertThat(refreshToken).isNotBlank();
        assertThat(storedHash).hasSize(64).isNotEqualTo(refreshToken);
        assertThat(passwordHash).startsWith("$2").doesNotContain("correct-password");
        assertThat(jdbcTemplate.queryForObject("SELECT last_login_at IS NOT NULL FROM users", Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_SUCCESS'", Integer.class)).isEqualTo(1);
        assertAuditEvents("LOGIN_SUCCESS", 1, currentUserId());

        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    void refreshRotatesAndReuseRevokesTheTokenFamily() throws Exception {
        String original = refreshCookie(login("correct-password").andReturn());

        MvcResult refreshed = refresh(original).andExpect(status().isOk()).andReturn();
        String replacement = refreshCookie(refreshed);
        assertThat(replacement).isNotEqualTo(original);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
        assertAuditEvents("TOKEN_REFRESH", 1, currentUserId());

        refresh(original).andExpect(status().isUnauthorized());
        refresh(replacement).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'", Integer.class))
                .isEqualTo(2);
        assertAuditEvents("REFRESH_TOKEN_REUSE_DETECTED", 2, currentUserId());
    }

    @Test
    void simultaneousRefreshesCreateAtMostOneReplacementAndRevokeTheFamily() throws Exception {
        String original = refreshCookie(login("correct-password").andReturn());
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'admin@example.com'",
                Long.class);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<MvcResult> first;
        Future<MvcResult> second;

        try {
            try (Connection lockConnection = dataSource.getConnection()) {
                lockConnection.setAutoCommit(false);
                try (PreparedStatement statement = lockConnection.prepareStatement(
                        "SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                    statement.setLong(1, userId);
                    statement.executeQuery().close();
                }
                first = executor.submit(() -> concurrentRefresh(original, ready, start));
                second = executor.submit(() -> concurrentRefresh(original, ready, start));
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                Thread.sleep(300);
                assertThat(first.isDone()).isFalse();
                assertThat(second.isDone()).isFalse();
                lockConnection.commit();
            }
            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE replacement_token_id IS NOT NULL",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'TOKEN_REFRESH'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void logoutRevokesTokenAndDisabledOrExpiredSessionsAreRejected() throws Exception {
        String token = refreshCookie(login("correct-password").andReturn());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(new Cookie("refresh_token", token))
                        .header("X-CSRF-Protection", "1"))
                .andExpect(status().isOk());
        assertAuditEvents("LOGOUT", 1, currentUserId());
        refresh(token).andExpect(status().isUnauthorized());

        seedUserAndRole();
        String expired = refreshCookie(login("correct-password").andReturn());
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = DATE_SUB(NOW(), INTERVAL 1 SECOND)");
        refresh(expired).andExpect(status().isUnauthorized());

        jdbcTemplate.update("UPDATE users SET enabled = FALSE");
        login("correct-password").andExpect(status().isForbidden());
    }

    @Test
    void disabledUserRefreshIsRejectedAndRevokesTheExistingFamily() throws Exception {
        String token = refreshCookie(login("correct-password").andReturn());
        Long userId = currentUserId();
        jdbcTemplate.update("UPDATE users SET enabled = FALSE WHERE id = ?", userId);

        refresh(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is disabled"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE replacement_token_id IS NOT NULL",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE revoked_at IS NULL",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'TOKEN_REFRESH'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED'",
                Integer.class)).isZero();
        assertAuditEvents("LOGIN_SUCCESS", 1, userId);
    }

    @Test
    void wrongAndUnknownCredentialsMatchAndEmailIsUnique() throws Exception {
        login("wrong").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unknown@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        assertAuditEvents("LOGIN_FAILURE", 2, null);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name) VALUES ('admin@example.com', 'x', 'x')"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private org.springframework.test.web.servlet.ResultActions login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ADMIN@example.com\",\"password\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie("refresh_token", token))
                .header("X-CSRF-Protection", "1"));
    }

    private MvcResult concurrentRefresh(
            String token,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent refresh start was not released");
        }
        return refresh(token).andReturn();
    }

    private String refreshCookie(MvcResult result) {
        var matcher = REFRESH_COOKIE.matcher(result.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private Long currentUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'admin@example.com'",
                Long.class);
    }

    private void assertAuditEvents(String action, int expectedCount, Long expectedActorUserId) {
        List<java.util.Map<String, Object>> events = jdbcTemplate.queryForList(
                """
                SELECT actor_user_id, action, entity_type, entity_id, details, ip_address
                FROM audit_logs
                WHERE action = ?
                ORDER BY id
                """,
                action);
        assertThat(events).hasSize(expectedCount);
        for (java.util.Map<String, Object> event : events) {
            assertThat(event.get("action")).isEqualTo(action);
            assertThat(event.get("actor_user_id")).isEqualTo(expectedActorUserId);
            assertThat(event.get("entity_type")).isEqualTo("AUTHENTICATION");
            assertThat(event.get("entity_id"))
                    .isEqualTo(expectedActorUserId == null ? null : expectedActorUserId.toString());
            assertThat(event.get("ip_address")).isNotNull();
            String details = String.valueOf(event.get("details"));
            assertThat(details.toLowerCase())
                    .doesNotContain("password", "access_token", "refresh_token", "cookie", "jwt_secret");
            if ("LOGIN_FAILURE".equals(action)) {
                assertThat(details).contains("reason", "INVALID_CREDENTIALS");
            } else if ("REFRESH_TOKEN_REUSE_DETECTED".equals(action)) {
                assertThat(details).contains("familyId");
            } else {
                assertThat(details).isEqualTo("{}");
            }
        }
    }
}
